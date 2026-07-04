package dev.scuttle.inventory.data.error

/**
 * Fire-and-forget remote error reporting. An interface so ViewModels depend on
 * an abstraction (and can be unit-tested with a fake); the Android implementation
 * that POSTs to /errors is [ErrorLoggerImpl], bound via Hilt.
 */
interface ErrorLogger {
    fun log(code: String, message: String? = null)
}
