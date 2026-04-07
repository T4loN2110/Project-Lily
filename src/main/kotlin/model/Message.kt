package model

data class Message (
    val text: String,
    val role: String = "user",
    val timestamp: Long
)