package com.t4lon.lily

import com.t4lon.lily.lily_impl.LilyTTS
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(
) {
    val tts = LilyTTS()

    val projectRoot = System.getProperty("user.dir")
    val modelPath = "$projectRoot/assets/voice/en_US-amy-medium.onnx"

    val daisyBellLyrics = listOf(
        "Daisy, Daisy, give me your answer do.",
        "I'm half crazy, all for the love of you.",
        "It won't be a stylish marriage,",
        "I can't afford a carriage.",
        "But you'll look sweet,",
        "Upon the seat,",
        "Of a bicycle built for two.",

        "There is a flower within my heart, Daisy, Daisy!",
        "Planted one day by a glancing dart,",
        "Planted by Daisy Bell!",

        "Whether she loves me or loves me not,",
        "Sometimes it's hard to tell.",
        "Yet I am longing to share the lot,",
        "Of beautiful Daisy Bell!"
    )

    runBlocking {
        println(modelPath)
        for ((index, line) in daisyBellLyrics.withIndex()) {
            println("♪ $line")
            tts.playSpeech(line)                    // Phát câu này

            // Tạo cảm giác "hát" tự nhiên hơn
            if (index % 2 == 0) {
                delay(300)   // nghỉ ngắn giữa các dòng
            } else {
                delay(800)   // nghỉ dài hơn sau câu dài
            }
        }
    }
}