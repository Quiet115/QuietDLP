package com.quietdlp.app.data

data class UpdateOperationState(val progress: Int?, val message: String?) {
    val visible: Boolean get() = progress != null || message != null

    companion object {
        fun from(progress: Int, message: String?): UpdateOperationState = UpdateOperationState(
            progress.takeIf { it >= 0 }?.coerceAtMost(100),
            message?.takeIf { it.isNotBlank() },
        )
    }
}
