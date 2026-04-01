import weaviate
import weaviate.classes.config as wcc
from weaviate.classes.init import AdditionalConfig, Timeout
from weaviate.classes.query import Filter, Sort, HybridFusion
import uuid
from datetime import datetime, timedelta
from sentence_transformers import CrossEncoder
import logging

# Set transformers logging to error only to hide the "Unexpected" warnings
logging.getLogger("transformers.modeling_utils").setLevel(logging.ERROR)

class LilyRepository:
    def __init__(self):
        # Connecting to a local instance (adjust if using Weaviate Cloud)
        self.client = weaviate.connect_to_local(
            port=8080,
            grpc_port=50051,
            additional_config=AdditionalConfig(timeout=Timeout(init=30, query=60, insert=120))
        )
        self._setup_schema()

    def _setup_schema(self):
        if not self.client.collections.exists("LilyMemory"):
            self.client.collections.create(
                name="LilyMemory",
                vector_config=wcc.Configure.Vectors.text2vec_ollama(
                    name="default",
                    api_endpoint="http://host.docker.internal:11434",
                    model="nomic-embed-text",
                ),
                # Indexing timestamps allows you to search "What happened last Tuesday?"
                inverted_index_config=wcc.Configure.inverted_index(
                    index_timestamps=True
                ),
                properties=[
                    wcc.Property(name="content", data_type=wcc.DataType.TEXT),
                    wcc.Property(name="importance", data_type=wcc.DataType.NUMBER),
                    wcc.Property(name="timestamp", data_type=wcc.DataType.DATE),
                    wcc.Property(name="entry_type", data_type=wcc.DataType.TEXT),
                ]
            )

    def save_memory(self, text, importance, metadata_type="sentence", manual_id=None):
        memories = self.client.collections.get("LilyMemory")
        
        # Weaviate expects RFC3339 format for dates
        # e.g., "2023-10-25T14:30:00Z"
        iso_timestamp = datetime.now().isoformat() + "Z"
        
        memories.data.insert(
            uuid=manual_id or uuid.uuid4(),
            properties={
                "content": text,
                "importance": float(importance),
                "timestamp": iso_timestamp,
                "entry_type": metadata_type
            }
        )
        
    def query_memories(self, query_text, limit=5, alpha=0.3):
        memories = self.client.collections.get("LilyMemory")
        # Weaviate handles vector search internally if configured with a module, 
        # or you can pass vectors manually.
        response = memories.query.hybrid(
            query=query_text,
            limit=20,
            alpha=alpha,
            fusion_type=HybridFusion.RELATIVE_SCORE
        )

        initial_results = response.objects
        pairs = [[query_text, obj.properties["content"]] for obj in initial_results]
        model = CrossEncoder('cross-encoder/ms-marco-MiniLM-L-6-v2', device='cpu')
        scores = model.predict(pairs)

        for i, obj in enumerate(initial_results):
            obj.metadata.score = scores[i] # Store the rerank score

        reranked = sorted(initial_results, key=lambda x: x.metadata.score, reverse=True)
        return reranked[:limit]
    
    def get_memories_by_range(self, min_threshold=0.4, max_threshold=1.0, metadata_type=None, limit=30):
        memories = self.client.collections.get("LilyMemory")
        # Sử dụng Filter.and_ để quét trong một khoảng nhất định
        filters = Filter.by_property("importance").greater_or_equal(min_threshold) & \
                  Filter.by_property("importance").less_than(max_threshold)
        
        if metadata_type:
            filters = filters & Filter.by_property("metadata_type").equal(metadata_type)
        
        response = memories.query.fetch_objects(
            limit=limit,
            filters=filters,
            sort=Sort.by_property("timestamp", ascending=False)
        )
        return response.objects

    def update_memories(self, objects, properties):
        if not objects:
            return
            
        memories = self.client.collections.get("LilyMemory")
        try:
            for obj in objects:
                memories.data.update(
                    uuid=obj.uuid,
                    properties=properties
                )
            print(f" [Updated {len(objects)} memories with {properties}]")
        except Exception as e:
            print(f"Update Error: {e}")

    def cleanup_memories(self):
        memories = self.client.collections.get("LilyMemory")
        now = datetime.now()
        
        # 1. Delete absolute trash (0.0 - 0.3) immediately
        memories.data.delete_many(
            where=Filter.by_property("importance").less_than(0.35)
        )
        
        # 2. Delete "Grey Zone" (0.4 - 0.6) only if they are older than 3 days
        # This keeps the recent 'vibe' of the conversation but clears the long-term clutter
        three_days_ago = (now - timedelta(days=3)).isoformat() + "Z"
        
        memories.data.delete_many(
            where=(
                Filter.by_property("importance").less_than(0.65) &
                Filter.by_property("timestamp").less_than(three_days_ago)
            )
        )

    def count_raw_interactions(self):
        memories = self.client.collections.get("LilyMemory")
        # Đếm các bản ghi có metadata_type là 'raw_interaction'
        response = memories.aggregate.over_all(
            filters=Filter.by_property("entry_type").equal("raw_interaction"),
            total_count=True
        )
        return response.total_count

    def close(self):
        self.client.close()