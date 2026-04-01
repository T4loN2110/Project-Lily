package com.t4lon.lily.lily_impl

data class LilyMemory(
    val uuid: String,
    val content: String,
    val importance: Double,
    val timestamp: String,
    val entryType: String,
    val score: Float = 0f,
)
