package io.xstarrevival.core.command

interface CommandTransport {
    val name: String
    val supportedCommands: Set<CommandKind>

    suspend fun send(request: CommandRequest): CommandAcknowledgement
    suspend fun awaitCompletion(request: CommandRequest): CommandCompletion
}

sealed interface CommandAcknowledgement {
    data class Accepted(val detail: String? = null) : CommandAcknowledgement
    data class Rejected(val reason: String) : CommandAcknowledgement
    data class Failed(val reason: String) : CommandAcknowledgement
    data class Unsupported(val reason: String) : CommandAcknowledgement
}

sealed interface CommandCompletion {
    data class Completed(val detail: String? = null) : CommandCompletion
    data class Failed(val reason: String) : CommandCompletion
    data class Cancelled(val reason: String? = null) : CommandCompletion
}
