/*
 * Artemis innovation on top of sol4k: Flow-based transaction confirmation.
 *
 * Upstream sol4k exposes `Connection.sendTransaction(tx): String` one-shot.
 * Apps poll `getSignatureStatuses` in a loop to detect confirmation, which
 * is boilerplate every dapp writes independently. This extension gives a
 * cold Flow that emits `Pending -> Processed -> Confirmed -> Finalized` and
 * terminates when the target commitment is reached or the caller cancels.
 */
package org.sol4k

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Confirmation states a transaction moves through. Values align with the
 * Solana commitment level strings that RPC nodes return.
 */
sealed class ConfirmationState {
    abstract val signature: String
    abstract val slot: Long?

    data class Pending(override val signature: String) : ConfirmationState() {
        override val slot: Long? = null
    }
    data class Processed(override val signature: String, override val slot: Long?) : ConfirmationState()
    data class Confirmed(override val signature: String, override val slot: Long?) : ConfirmationState()
    data class Finalized(override val signature: String, override val slot: Long?) : ConfirmationState()
}

/**
 * Send [transaction] and emit confirmation updates until [commitment] is
 * reached. Cancels cleanly when the collector cancels.
 *
 * The polling interval matches Solana's 400ms slot time. Apps that need a
 * tighter loop can re-implement this in their own module.
 *
 * ```kotlin
 * connection.sendAndWatch(tx)
 *     .onEach { state -> ui.update(state) }
 *     .launchIn(viewModelScope)
 * ```
 */
@JvmOverloads
fun Connection.sendAndWatch(
    transaction: Transaction,
    commitment: Commitment = Commitment.CONFIRMED,
    pollIntervalMs: Long = 400
): Flow<ConfirmationState> = flow {
    val signature = sendTransaction(transaction)
    emit(ConfirmationState.Pending(signature))
    while (currentCoroutineContext().isActive) {
        val statuses = try {
            asArtemis().getSignatureStatusesTyped(listOf(signature))
        } catch (_: Exception) {
            delay(pollIntervalMs)
            continue
        }
        val status = statuses.firstOrNull()
        if (status == null) {
            delay(pollIntervalMs)
            continue
        }
        val state: ConfirmationState = when (status.confirmationStatus) {
            "processed" -> ConfirmationState.Processed(signature, status.slot)
            "confirmed" -> ConfirmationState.Confirmed(signature, status.slot)
            "finalized" -> ConfirmationState.Finalized(signature, status.slot)
            else -> ConfirmationState.Pending(signature)
        }
        emit(state)
        val reached = when (commitment) {
            Commitment.PROCESSED -> state is ConfirmationState.Processed ||
                state is ConfirmationState.Confirmed ||
                state is ConfirmationState.Finalized
            Commitment.CONFIRMED -> state is ConfirmationState.Confirmed ||
                state is ConfirmationState.Finalized
            Commitment.FINALIZED -> state is ConfirmationState.Finalized
        }
        if (reached) return@flow
        delay(pollIntervalMs)
    }
}.flowOn(Dispatchers.IO)
