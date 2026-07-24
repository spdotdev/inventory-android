package dev.scuttle.inventory.ui.appupdate

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL
import javax.inject.Inject

class UpdateInstaller
    @Inject
    constructor() {
        /**
         * Downloads the APK at [downloadUrl] and launches the system installer for it.
         * Network/IO failures (timeout, malformed URL, disk error, ...) are caught rather
         * than left to crash the caller's coroutine - the caller can inspect the returned
         * [Result] to surface a failure message to the user.
         */
        suspend fun downloadAndInstall(
            context: Context,
            downloadUrl: String,
        ): Result<Unit> =
            runCatching {
                requireHttps(downloadUrl)

                val apkDir = File(context.cacheDir, "apk").apply { mkdirs() }
                val apkFile = File(apkDir, "update.apk")

                URL(downloadUrl).openStream().use { input ->
                    apkFile.outputStream().use { output -> input.copyTo(output) }
                }

                val uri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile,
                    )
                val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
            }.onFailure { error ->
                Log.e(TAG, "Failed to download/install update from $downloadUrl", error)
            }

        internal companion object {
            const val TAG = "UpdateInstaller"

            /**
             * Rejects any [downloadUrl] that isn't served over HTTPS. The system package
             * installer independently verifies the APK's signing certificate against the
             * currently-installed app before allowing an install, but requiring HTTPS at
             * the download layer is cheap defense-in-depth against network-level tampering
             * in transit (e.g. a misconfigured backend value pointing at `http://`).
             */
            fun requireHttps(downloadUrl: String) {
                val protocol = URL(downloadUrl).protocol
                require(protocol.equals("https", ignoreCase = true)) {
                    "APK download URL must use https, got: $protocol"
                }
            }
        }
    }
