package com.t4lon.lily.lily_impl

import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.grpc.Collections.*
import io.qdrant.client.grpc.JsonWithInt.*
import io.qdrant.client.grpc.Points.*
import io.qdrant.client.grpc.Points.PointsUpdateOperation.*
import io.qdrant.client.grpc.Common.*
import java.net.URI
import java.net.http.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import kotlinx.serialization.json.*
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.*
import io.qdrant.client.VectorsFactory.*

class LilyRepository (
    private val ollamaBaseUrl: String = "http://localhost:11434",
    private val ollamaModel: String = "nomic-embed-text"
): CoroutineScope {
    override val coroutineContext = Dispatchers.IO + SupervisorJob()

    private val client: QdrantClient
    private val httpClient: HttpClient

    private val collectionName = "LilyMemory"
    private val vectorSize = 768L

    init {
        val grpcClient = QdrantGrpcClient.newBuilder("localhost").build()
        this.client = QdrantClient(grpcClient)

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build()

        setupSchema()
    }

    private fun setupSchema() {
        launch {
            try {
                val collections = client.listCollectionsAsync().await()
                if (collections.isEmpty()) {
                    client.createCollectionAsync(
                        collectionName,
                        VectorParams.newBuilder()
                            .setSize(vectorSize)
                            .setDistance(Distance.Cosine)
                            .build()
                    ).await()
                    println(" [Qdrant] Collection '$collectionName' created successfully")
                }
            } catch (e: Exception) {
                println("[Qdrant] Setup warning: ${e.message}")
            }
        }
    }

    private suspend fun embed(text: String): List<Float> = withContext(Dispatchers.IO) {
        if (text.isBlank()) throw IllegalArgumentException("Text for embedding cannot be blank")

        val body = buildJsonObject {
            put("model", ollamaModel)
            put("prompt", text)
        }.toString()

        val request = HttpRequest.newBuilder()
            .uri(URI("$ollamaBaseUrl/api/embeddings"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw RuntimeException("Ollama embedding failed: ${response.statusCode()} - ${response.body()}")
        }

        val json = Json.parseToJsonElement(response.body()).jsonObject
        val embedding = json["embedding"]?.jsonArray
            ?: throw RuntimeException("No embedding field in Ollama response")

        embedding.map { it.jsonPrimitive.float }
    }

    suspend fun saveMemory(
        text: String,
        importance: Double,
        metadataType: String = "raw_interaction",
        manualId: String? = null,
    ) = withContext(Dispatchers.IO) {
        val vector = embed(text)
        val pointId = manualId ?: UUID.randomUUID().toString()
        val timestamp = Instant.now().toString()

        val payload = mapOf(
            "content" to text,
            "importance" to importance,
            "timestamp" to timestamp,
            "entry_type" to metadataType
        )

        val point = PointStruct.newBuilder()
            .setId(PointId.newBuilder().setUuid(pointId).build())
            .setVectors(
                vectors(vector)
            )
            .putAllPayload(payload.mapValues {
                Value.newBuilder().setStringValue(it.value.toString()).build()
            })
            .build()

        client.upsertAsync(
            UpsertPoints.newBuilder()
                .setCollectionName(collectionName)
                .addPoints(point)
                .build()
        ).await()

        println(" [Qdrant] Saved memory (id: ${pointId.take(8)}..., importance: $importance)")
    }

    suspend fun queryMemories(
        queryText: String,
        limit: Int = 5,
    ): List<LilyMemory> = withContext(Dispatchers.IO) {
        val queryFloats = embed(queryText)

        val searchRequest = SearchPoints.newBuilder()
            .setCollectionName(collectionName)
            .addAllVector(queryFloats)
            .setLimit(20)
            .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
            .build()

        val results = client.searchAsync(searchRequest)

        results.await().map { result ->
            val payload = result.payloadMap
            LilyMemory(
                uuid = result.id.uuid,
                content = payload["content"]?.stringValue ?: "",
                importance = payload["importance"]?.doubleValue ?: 0.0,
                timestamp = payload["timestamp"]?.stringValue ?: "",
                entryType = payload["entry_type"]?.stringValue ?: "",
                score = result.score
            )
        }.sortedByDescending { it.score }
            .take(limit)
    }

    suspend fun getMemoriesByRange(
        minThreshold: Double = 0.4,
        maxThreshold: Double = 1.0,
        metadataType: String? = null,
        limit: Int = 30,
    ): List<LilyMemory> = withContext(Dispatchers.IO) {
        val filterBuilder = Filter.newBuilder()

        filterBuilder.addMust(
            Condition.newBuilder()
                .setField(FieldCondition.newBuilder()
                    .setKey("importance")
                    .setRange(Range.newBuilder().setGte(minThreshold).setLt(maxThreshold).build())
                    .build()
                )
        )

        if (metadataType != null) {
            filterBuilder.addMust(
                Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                        .setKey("entry_type")
                        .setMatch(Match.newBuilder().setKeyword(metadataType).build())
                        .build()
                    )
            )
        }

        val scrollRequest = ScrollPoints.newBuilder()
            .setCollectionName(collectionName)
            .setFilter(filterBuilder)
            .setLimit(limit)
            .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
            .setOrderBy(OrderBy.newBuilder().setKey("timestamp").setDirection(Direction.Desc).build())
            .build()

        val response: ScrollResponse = client.scrollAsync(scrollRequest).await()

        val points: List<RetrievedPoint> = response.resultList
        points.map { point ->
            val payload = point.payloadMap

            LilyMemory(
                uuid = payload["uuid"]?.stringValue ?: "",
                content = payload["content"]?.stringValue ?: "",
                importance = payload["importance"]?.doubleValue ?: 0.0,
                timestamp = payload["timestamp"]?.stringValue ?: "",
                entryType = payload["entry_type"]?.stringValue ?: "",
                score = 0f
            )
        }
    }

    suspend fun updateMemories(
        objects: List<LilyMemory>,
        properties: Map<String, Any>,
    ) = withContext(Dispatchers.IO) {
        if (objects.isEmpty()) return@withContext

        val payload: Map<String, Value> = properties.mapValues { (_, value) ->
            when (value) {
                is String -> Value.newBuilder().setStringValue(value).build()
                is Number -> Value.newBuilder().setDoubleValue(value.toDouble()).build()
                is Boolean -> Value.newBuilder().setBoolValue(value).build()
                else -> Value.newBuilder().setStringValue(value.toString()).build()
            }
        }

        val setPayloadOperation = newBuilder()
            .setSetPayload(
                SetPayload.newBuilder()
                    .putAllPayload(payload)
                    .build()
            )
            .build()

        client.batchUpdateAsync(
            collectionName,
            listOf(setPayloadOperation),
        )

        println(" [Qdrant] Updated ${objects.size} memories")
    }

    suspend fun cleanupMemory() = withContext(Dispatchers.IO) {
        val threeDaysAgo: Double = Instant.now().minus(Duration.ofDays(3)).toEpochMilli().toDouble()

        val trashFilter = Filter.newBuilder()
            .addMust(Condition.newBuilder()
                .setField(FieldCondition.newBuilder()
                    .setKey("importance")
                    .setRange(Range.newBuilder().setLt(0.35).build())
                    .build()
                )
                .build()
            )
            .build()

        client.deleteAsync(collectionName, trashFilter).await()

        val greyFilter = Filter.newBuilder()
            .addMust(Condition.newBuilder()
                .setField(FieldCondition.newBuilder()
                    .setKey("importance")
                    .setRange(Range.newBuilder().setLt(0.65).build())
                    .build()
                )
                .build()
            )
            .addMust(Condition.newBuilder()
                .setField(FieldCondition.newBuilder()
                    .setKey("importance")
                    .setRange(Range.newBuilder().setLt(threeDaysAgo).build())
                    .build()
                )
                .build()
            )
            .build()

        client.deleteAsync(collectionName, greyFilter).await()

        println(" [Qdrant] Cleanup completed")
    }

    suspend fun countRawInteraction(): Long = withContext(Dispatchers.IO) {
        val filter = Filter.newBuilder()
            .addMust(Condition.newBuilder()
                .setField(FieldCondition.newBuilder()
                    .setKey("entry_type")
                    .setMatch(Match.newBuilder().setKeyword("raw_interaction").build())
                    .build()
                )
                .build()
            )
            .build()

        client.countAsync(
            collectionName,
            filter,
            true
        ).await()
    }

    fun close() {
        coroutineContext.cancel()
        client.close()
        httpClient.close()
        println(" [Qdrant] Repository closed")
    }
}