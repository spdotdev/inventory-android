package dev.scuttle.inventory.data.error

import android.content.Context
import android.net.ConnectivityManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scuttle.inventory.BuildConfig
import dev.scuttle.inventory.data.api.ErrorApi
import dev.scuttle.inventory.data.dto.ClientErrorRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorLoggerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val api: ErrorApi,
    ) : ErrorLogger {
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private val deviceId: String by lazy {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        }

        override fun log(
            code: String,
            message: String?,
        ) {
            if (!isOnline()) return
            scope.launch {
                runCatching {
                    api.log(
                        ClientErrorRequest(
                            device_id = deviceId,
                            error_code = code,
                            message = message,
                            app_version = BuildConfig.VERSION_NAME,
                        ),
                    )
                }
            }
        }

        private fun isOnline(): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.activeNetwork != null
        }
    }
