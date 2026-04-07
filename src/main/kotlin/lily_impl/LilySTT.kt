package com.t4lon.lily.lily_impl

import io.github.givimad.whisperjni.*
import kotlinx.io.Segment
import java.nio.file.Path

class LilySTT {
    private val projectRoot = System.getProperty("user.dir")
    private val modelPath = "$projectRoot\\assets\\whisper\\ggml-medium.en-q5_0.bin"

    private var whisper: WhisperJNI? = null

    init {
        WhisperJNI.loadLibrary()
        WhisperJNI.setLibraryLogger(null)
        whisper = WhisperJNI()
    }

    fun transcribe(audioData: FloatArray): String {
        val currentWhisper = whisper ?: return "Whisper chưa được khởi tạo"

        var ctx = currentWhisper.init(Path.of(modelPath))

        val params = WhisperFullParams()
        params.language = "en"
        params.nThreads = 4
        params.printProgress = false
        params.noContext = false
        params.singleSegment = false

        val result = currentWhisper.full(ctx, params, audioData, audioData.size)

        return if (result == 0) {
            val sb = StringBuilder()
            val segmentsCount = currentWhisper.fullNSegments(ctx)
            for (i in 0 until segmentsCount) {
                sb.append(currentWhisper.fullNSegments(ctx))
            }
            ctx.close()
            sb.toString()
        } else {
            ctx.close()
            "Lỗi trong quá trình xử lý âm thanh (Mã lỗi: $result)"
        }
    }
}