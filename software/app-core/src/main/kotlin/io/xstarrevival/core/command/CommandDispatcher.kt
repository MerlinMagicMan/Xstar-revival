package io.xstarrevival.core.command

import io.xstarrevival.core.model.XStarState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class CommandDispatcher(
    private val scope: CoroutineScope,
    private val stateProvider: () -> XStarState,
    private val transport: CommandTransport,
    private val validator: CommandSafetyValidator = CommandSafetyValidator(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val sequence = AtomicLong(0)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val transitionLock = Any()
    private val mutableStatuses = MutableStateFlow<Map<String, CommandStatus>>(emptyMap())
    private val mutableLatest = MutableStateFlow<CommandStatus?>(null)
    private val mutableHistory = MutableStateFlow<List<CommandStatus>>(emptyList())

    val statuses: StateFlow<Map<String, CommandStatus>> = mutableStatuses.asStateFlow()
    val latest: StateFlow<CommandStatus?> = mutableLatest.asStateFlow()
    val history: StateFlow<List<CommandStatus>> = mutableHistory.asStateFlow()

    fun dispatch(command: AircraftCommand, timeoutMs: Long = defaultTimeoutMs(command.kind)): String {
        val now = clock()
        val request = CommandRequest(
            id = "cmd-${sequence.incrementAndGet()}",
            command = command,
            createdAtEpochMs = now,
            timeoutMs = timeoutMs.coerceAtLeast(1L)
        )
        transition(request, CommandPhase.IDLE, "Queued for ${transport.name}")
        val job = scope.launch(start = CoroutineStart.LAZY) { execute(request) }
        jobs[request.id] = job
        job.invokeOnCompletion { jobs.remove(request.id, job) }
        job.start()
        return request.id
    }

    suspend fun dispatchAndAwait(command: AircraftCommand, timeoutMs: Long = defaultTimeoutMs(command.kind)): CommandStatus {
        val id = dispatch(command, timeoutMs)
        return statuses.first { current -> current[id]?.phase?.isTerminal == true }.getValue(id)
    }

    fun cancel(id: String): Boolean {
        val job = jobs[id] ?: return false
        val request = mutableStatuses.value[id]?.request ?: return false
        if (!transition(request, CommandPhase.CANCELLED, "Command cancelled by operator")) return false
        job.cancel(CancellationException("Command cancelled by operator"))
        return true
    }

    fun cancelAll() {
        jobs.keys.toList().forEach(::cancel)
    }

    private suspend fun execute(request: CommandRequest) {
        try {
            transition(request, CommandPhase.VALIDATING)
            val activeKinds = mutableStatuses.value.values
                .filter { it.request.id != request.id && !it.phase.isTerminal }
                .map { it.request.command.kind }
                .toSet()
            val validation = validator.validate(
                command = request.command,
                state = stateProvider(),
                supportedCommands = transport.supportedCommands,
                activeCommands = activeKinds
            )
            if (!validation.supported) {
                transition(request, CommandPhase.UNSUPPORTED, validation.issues.joinToString { it.message })
                return
            }
            if (!validation.canDispatch) {
                transition(request, CommandPhase.REJECTED, validation.issues.joinToString { it.message })
                return
            }

            transition(request, CommandPhase.READY, "Safety validation passed")
            transition(request, CommandPhase.SENDING, "Sending through ${transport.name}")
            withTimeout(request.timeoutMs) {
                when (val acknowledgement = transport.send(request)) {
                    is CommandAcknowledgement.Accepted -> {
                        transition(request, CommandPhase.ACKNOWLEDGED, acknowledgement.detail)
                        transition(request, CommandPhase.ACTIVE, "Awaiting state reconciliation")
                        when (val completion = transport.awaitCompletion(request)) {
                            is CommandCompletion.Completed -> transition(request, CommandPhase.COMPLETED, completion.detail)
                            is CommandCompletion.Failed -> transition(request, CommandPhase.FAILED, completion.reason)
                            is CommandCompletion.Cancelled -> transition(request, CommandPhase.CANCELLED, completion.reason)
                        }
                    }
                    is CommandAcknowledgement.Rejected -> transition(request, CommandPhase.REJECTED, acknowledgement.reason)
                    is CommandAcknowledgement.Failed -> transition(request, CommandPhase.FAILED, acknowledgement.reason)
                    is CommandAcknowledgement.Unsupported -> transition(request, CommandPhase.UNSUPPORTED, acknowledgement.reason)
                }
            }
        } catch (_: TimeoutCancellationException) {
            transition(request, CommandPhase.TIMED_OUT, "No acknowledgement or reconciled state before timeout")
        } catch (cancelled: CancellationException) {
            if (mutableStatuses.value[request.id]?.phase?.isTerminal != true) {
                transition(request, CommandPhase.CANCELLED, cancelled.message)
            }
            throw cancelled
        } catch (error: Throwable) {
            transition(request, CommandPhase.FAILED, error.message ?: error::class.simpleName)
        } finally {
            jobs.remove(request.id)
        }
    }

    private fun transition(request: CommandRequest, phase: CommandPhase, detail: String? = null): Boolean =
        synchronized(transitionLock) {
            if (mutableStatuses.value[request.id]?.phase?.isTerminal == true) return@synchronized false
            val status = CommandStatus(request, phase, detail, clock())
            mutableStatuses.value = mutableStatuses.value + (request.id to status)
            mutableLatest.value = status
            mutableHistory.value = (mutableHistory.value + status).takeLast(MAX_HISTORY)
            true
        }

    private companion object {
        const val MAX_HISTORY = 500

        fun defaultTimeoutMs(kind: CommandKind): Long = when (kind) {
            CommandKind.TAKEOFF, CommandKind.LAND, CommandKind.EMERGENCY_LAND -> 30_000L
            CommandKind.RETURN_TO_HOME -> 5 * 60_000L
            CommandKind.START_ORBIT -> 10 * 60_000L
            CommandKind.START_FOLLOW -> 30 * 60_000L
            CommandKind.START_WAYPOINT_MISSION -> 30 * 60_000L
            CommandKind.CALIBRATE_GIMBAL -> 60_000L
            else -> 5_000L
        }
    }
}
