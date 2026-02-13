package com.peonping.jetbrains

enum class CespCategory(val key: String) {
    SESSION_START("session.start"),
    TASK_ACKNOWLEDGE("task.acknowledge"),
    TASK_COMPLETE("task.complete"),
    TASK_ERROR("task.error"),
    INPUT_REQUIRED("input.required"),
    RESOURCE_LIMIT("resource.limit"),
    USER_SPAM("user.spam"),
}

