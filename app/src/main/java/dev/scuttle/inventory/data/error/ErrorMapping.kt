package dev.scuttle.inventory.data.error

import retrofit2.HttpException
import java.io.IOException

/**
 * Turn a thrown [Throwable] into a user-facing message. Network failures
 * (any [IOException] — no connection, DNS, timeout) and well-known HTTP status
 * codes get a friendly, consistent message instead of a raw "Unable to resolve
 * host …" or a stack message leaking to the UI. Anything unrecognized falls back
 * to [fallback] (the caller's context-specific line) or the throwable's message.
 */
fun Throwable.toUserMessage(fallback: String): String =
    when (this) {
        is IOException -> "Can't reach the server. Check your connection and try again."
        is HttpException ->
            when (code()) {
                401 -> "Your session has expired. Please sign in again."
                403 -> "You don't have access to that."
                404 -> "That could not be found — it may have been removed."
                422 -> "Please check your input and try again."
                429 -> "Too many requests. Please wait a moment and try again."
                in 500..599 -> "Server error. Please try again in a moment."
                else -> fallback
            }
        else -> message ?: fallback
    }
