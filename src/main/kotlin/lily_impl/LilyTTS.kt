package com.t4lon.lily.lily_impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LilyTTS() {
    private val projectRoot = System.getProperty("user.dir")
    private val modelPath = "$projectRoot\\assets\\voice\\en_US-amy-medium.onnx"

    suspend fun playSpeech(text: String) = withContext(Dispatchers.IO) {
        val safeText = text.replace("\"", "'").replace("&", "^&").replace("|", "^|").trim()
        if (safeText.isBlank()) return@withContext

        val command = listOf(
            "cmd.exe", "/c",
            """
            echo $safeText | 
            piper --model "$modelPath" --output_raw | 
            sox -t raw -r 22050 -e signed-integer -b 16 -c 1 - -t waveaudio default 
            pitch 280 tempo -s 0.9 
            compand 0.3,1 6:-70,-60,-20 -5 -90 0.2
            """.trimIndent().replace("\n", " ")   // gộp thành 1 dòng cho cmd
        )

        try {
            val process = ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                println(" [LilyTTS] Piper + SoX failed with exit code: $exitCode")
            } else {
                println(" [LilyTTS] Played successfully")
            }
        } catch (e: Exception) {
            println(" [LilyTTS] Play speech error: ${e.message}")
            e.printStackTrace()
        }
    }
}