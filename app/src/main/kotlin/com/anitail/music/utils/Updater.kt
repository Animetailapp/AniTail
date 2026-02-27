package com.anitail.music.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.anitail.music.BuildConfig
import com.anitail.music.R
import com.anitail.music.constants.AutoUpdateCheckFrequencyKey
import com.anitail.music.constants.AutoUpdateEnabledKey
import com.anitail.music.constants.UpdateCheckFrequency
import com.anitail.music.extensions.toEnum
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.json.JSONObject
import timber.log.Timber
import java.io.File


object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set

    private const val UPDATE_DIRECTORY_PATH = "AniTail/apk"
    private const val UPDATE_FILE_NAME = "AniTail_update.apk"

    @Volatile
    private var activeDownloadId: Long = -1L

    @Volatile
    private var activeDownloadUrl: String? = null

    private var downloadCompleteReceiver: BroadcastReceiver? = null
    
    data class ReleaseInfo(
        val versionName: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    suspend fun getLatestVersionName(): Result<String> =
        runCatching {
            val response =
                client.get("https://api.github.com/repos/Animetailapp/Anitail/releases/latest")
                    .bodyAsText()
            val json = JSONObject(response)
            val versionName = json.getString("name")
            lastCheckTime = System.currentTimeMillis()
            versionName
        }
        
    suspend fun getLatestReleaseInfo(): Result<ReleaseInfo> =
        runCatching {
            val response = 
                client.get("https://api.github.com/repos/Animetailapp/Anitail/releases/latest")
                    .bodyAsText()
            val json = JSONObject(response)
            val versionName = json.getString("name")
            val releaseNotes = json.getString("body")
            
            // Get universal APK download URL
            val assets = json.getJSONArray("assets")
            var downloadUrl = ""
            val arch = BuildConfig.ARCHITECTURE
            
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")

                if (name.contains("universal") || name == "AniTail.apk") {
                    downloadUrl = asset.getString("browser_download_url")
                }

                if (arch != "universal" && name.contains(arch)) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }
            
            if (downloadUrl.isEmpty()) {
                downloadUrl = "https://github.com/Animetailapp/Anitail/releases/latest/download/AniTail.apk"
            }
            
            lastCheckTime = System.currentTimeMillis()
            ReleaseInfo(versionName, downloadUrl, releaseNotes)
        }
        
    fun shouldCheckForUpdates(context: Context): Boolean {
        val dataStore = context.dataStore
        val lastCheck = lastCheckTime
        val frequency = dataStore[AutoUpdateCheckFrequencyKey].toEnum(UpdateCheckFrequency.DAILY)

        if (!dataStore[AutoUpdateEnabledKey, true]) {
            return false
        }

        if (frequency == UpdateCheckFrequency.NEVER) {
            return false
        }

        return lastCheck == -1L || System.currentTimeMillis() - lastCheck > frequency.toMillis()
    }
    
    fun downloadUpdate(context: Context, downloadUrl: String): Long {
        val appContext = context.applicationContext
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        synchronized(this) {
            val previousDownloadId = activeDownloadId
            if (previousDownloadId != -1L) {
                val previousStatus = getDownloadStatus(downloadManager, previousDownloadId)
                if (previousStatus != null && isDownloadInProgress(previousStatus) && activeDownloadUrl == downloadUrl) {
                    Timber.i(
                        "Update download already in progress (id=%d), skipping duplicate request",
                        previousDownloadId
                    )
                    return previousDownloadId
                }
                unregisterDownloadCompleteReceiver(appContext)
                removeDownload(downloadManager, previousDownloadId)
                activeDownloadId = -1L
                activeDownloadUrl = null
            }

            if (!ensureUpdateDirectoryExists()) {
                Timber.e("Unable to create update directory")
                return -1L
            }

            val apkFile = getUpdateApkFile()
            if (apkFile.exists() && !apkFile.delete()) {
                Timber.e("Unable to delete old update APK: %s", apkFile.absolutePath)
                return -1L
            }

            val request = DownloadManager.Request(downloadUrl.toUri())
                .setTitle(context.getString(R.string.update_notification_title))
                .setDescription(context.getString(R.string.update_notification_description))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "$UPDATE_DIRECTORY_PATH/$UPDATE_FILE_NAME"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadId = downloadManager.enqueue(request)
            activeDownloadId = downloadId
            activeDownloadUrl = downloadUrl
            registerDownloadCompleteReceiver(appContext, downloadManager, downloadId)
            return downloadId
        }
    }

    private fun registerDownloadCompleteReceiver(
        context: Context,
        downloadManager: DownloadManager,
        expectedDownloadId: Long
    ) {
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val completedDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedDownloadId != expectedDownloadId) {
                    return
                }

                val status = getDownloadStatus(downloadManager, expectedDownloadId)
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    installUpdate(receiverContext, UPDATE_FILE_NAME)
                } else {
                    Timber.w(
                        "Update download %d finished with status=%s. Installation skipped.",
                        expectedDownloadId,
                        status
                    )
                }

                synchronized(this@Updater) {
                    if (activeDownloadId == expectedDownloadId) {
                        activeDownloadId = -1L
                        activeDownloadUrl = null
                    }
                }

                unregisterDownloadCompleteReceiver(context)
            }
        }

        ContextCompat.registerReceiver(
            context,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        synchronized(this) {
            downloadCompleteReceiver = onComplete
        }
    }

    private fun unregisterDownloadCompleteReceiver(context: Context) {
        val receiver = synchronized(this) {
            val currentReceiver = downloadCompleteReceiver
            downloadCompleteReceiver = null
            currentReceiver
        } ?: return

        runCatching {
            context.unregisterReceiver(receiver)
        }.onFailure {
            Timber.w(it, "Failed to unregister update download receiver")
        }
    }

    private fun ensureUpdateDirectoryExists(): Boolean {
        val updateDirectory =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                UPDATE_DIRECTORY_PATH
            )
        return updateDirectory.exists() || updateDirectory.mkdirs()
    }

    private fun getUpdateApkFile(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$UPDATE_DIRECTORY_PATH/$UPDATE_FILE_NAME"
        )

    private fun removeDownload(downloadManager: DownloadManager, downloadId: Long) {
        runCatching {
            downloadManager.remove(downloadId)
        }.onFailure {
            Timber.w(it, "Failed to remove download id=%d", downloadId)
        }
    }

    private fun getDownloadStatus(downloadManager: DownloadManager, downloadId: Long): Int? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }
            val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusColumn < 0) {
                return null
            }
            return cursor.getInt(statusColumn)
        }
        return null
    }

    private fun isDownloadInProgress(status: Int): Boolean =
        status == DownloadManager.STATUS_PENDING ||
            status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PAUSED

    private fun installUpdate(context: Context, fileName: String) {
        // Launch the installer activity to handle the APK installation prompt
        val intent = Intent(context, Class.forName("com.anitail.music.installer.UpdateInstallerActivity"))
        intent.putExtra("extra_file_name", "AniTail/apk/$fileName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
