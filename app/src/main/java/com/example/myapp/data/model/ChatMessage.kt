package com.example.myapp.data.model

data class Message(
    val role: String,
    val content: String
)

data class Choice(
    val message: Message
)










