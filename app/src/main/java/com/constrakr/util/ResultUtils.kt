package com.constrakr.util

import kotlinx.coroutines.CancellationException

/** Like [runCatching] but rethrows [CancellationException] so tab switches don't become user-visible errors. */
inline fun <T> appResultOf(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

fun Throwable.isUserCancellation(): Boolean =
    this is CancellationException || cause is CancellationException
