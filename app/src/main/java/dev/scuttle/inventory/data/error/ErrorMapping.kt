package dev.scuttle.inventory.data.error

import androidx.annotation.StringRes
import dev.scuttle.inventory.R
import retrofit2.HttpException
import java.io.IOException

/**
 * Turn a thrown [Throwable] into a user-facing message, as an Android string resource id rather
 * than a raw literal — this app is EN+NL, and a hardcoded English string returned here would
 * bypass localization for every caller (H3). Network failures (any [IOException] — no
 * connection, DNS, timeout) and well-known HTTP status codes get a friendly, consistent message
 * resource instead of a raw "Unable to resolve host …" or a stack message leaking to the UI.
 * Anything unrecognized falls back to [fallback] (the caller's context-specific resource id).
 *
 * Scope-down (H3): the previous version's catch-all branch showed the throwable's own
 * `message` verbatim when it wasn't an [IOException]/[HttpException] (`message ?: fallback`).
 * That can't be represented as a resource id — a server- or exception-authored string has no
 * R.string.* counterpart — so that branch now always resolves to [fallback] too. In practice
 * this app's real failures are always IOException (network) or HttpException (server response),
 * so this only changes behavior for the rare non-network, non-HTTP throwable a caller passes
 * through here (mostly test doubles).
 */
fun Throwable.toUserMessageRes(
    @StringRes fallback: Int,
): Int =
    when (this) {
        is IOException -> R.string.error_network_unreachable
        is HttpException ->
            when (code()) {
                401 -> R.string.error_session_expired
                403 -> R.string.error_forbidden
                404 -> R.string.error_not_found
                409 -> R.string.error_sole_owner_transfer_first
                422 -> R.string.error_invalid_input
                429 -> R.string.error_too_many_requests
                in 500..599 -> R.string.error_server
                else -> fallback
            }
        else -> fallback
    }
