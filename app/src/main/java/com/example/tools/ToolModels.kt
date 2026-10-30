package com.example.tools

data class ToolExecutionResult(
    val callId: String,
    val toolName: String,
    val success: Boolean,
    val message: String,
    val resultJson: String
)

data class ToolNotificationState(
    val id: String,
    val name: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
