package com.example.myapp.data.model

data class ApiErrorResponse(
    val error: ErrorDetail?
)

data class ErrorDetail(
    val message: String?,
    val type: String?,
    val code: String?
)










