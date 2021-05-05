/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.internal

import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Exclusive slot for waiting.
 * Only one waiter allowed.
 *
 * TODO: replace [Job] -> [Continuation] when all coroutines problems are fixed.
 */
internal class AwaitingSlot {
    private val suspension: AtomicRef<Continuation<Unit>?> = atomic(null)

    init {
        makeShared()
    }

    /**
     * Wait for other [sleep] or resume.
     */
    public suspend fun sleep() {
        if (trySuspend()) {
            return
        }

        resume()
    }

    /**
     * Resume waiter.
     */
    public fun resume() {
        suspension.getAndSet(null)?.resume(Unit)
    }

    /**
     * Cancel waiter.
     */
    public fun cancel(cause: Throwable?) {
        val continuation = suspension.getAndSet(null) ?: return

        if (cause != null) {
            continuation.resumeWithException(cause)
        } else {
            continuation.resume(Unit)
        }
    }

    private suspend fun trySuspend(): Boolean {
        var suspended = false

        suspendCoroutineUninterceptedOrReturn<Unit> {
            if (suspension.compareAndSet(null, it)) {
                suspended = true

                return@suspendCoroutineUninterceptedOrReturn COROUTINE_SUSPENDED
            }

            Unit
        }

        return suspended
    }
}
