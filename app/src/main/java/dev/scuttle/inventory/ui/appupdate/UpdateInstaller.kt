package dev.scuttle.inventory.ui.appupdate

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL
import javax.inject.Inject

class UpdateInstaller
    @Inject
    constructor() {
        suspend fun downloadAndInstall(
            context: Context,
            downloadUrl: String,
        ) {
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
        }
    }
