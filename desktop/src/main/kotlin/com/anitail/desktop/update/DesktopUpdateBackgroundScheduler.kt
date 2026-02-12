package com.anitail.desktop.update

import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.UpdateCheckFrequency
import java.io.File
import java.util.Locale

object DesktopUpdateBackgroundScheduler {
    private const val TaskName = "AniTail Desktop Update Check"

    data class SchedulerStatus(
        val supported: Boolean,
        val enabled: Boolean,
        val message: String,
    )

    fun startOrUpdate(preferences: DesktopPreferences): SchedulerStatus {
        if (!isWindows()) {
            return SchedulerStatus(
                supported = false,
                enabled = false,
                message = "Background update check with app closed is only supported on Windows.",
            )
        }

        val enabled = preferences.autoUpdateEnabled.value &&
            preferences.autoUpdateCheckFrequency.value != UpdateCheckFrequency.NEVER

        if (!enabled) {
            val removed = removeWindowsTask()
            return SchedulerStatus(
                supported = true,
                enabled = false,
                message = if (removed) "Background update task removed." else "Background update task disabled.",
            )
        }

        val exePath = currentExecutablePath()
        if (exePath == null) {
            return SchedulerStatus(
                supported = true,
                enabled = false,
                message = "Unable to resolve packaged executable path for scheduler.",
            )
        }

        val schedule = windowsScheduleToken(preferences.autoUpdateCheckFrequency.value)
        val command = "\"$exePath\" --check-updates-once"
        val ok = createOrUpdateWindowsTask(command = command, schedule = schedule)
        return SchedulerStatus(
            supported = true,
            enabled = ok,
            message = if (ok) {
                "Background update task enabled ($schedule)."
            } else {
                "Failed to enable background update task."
            },
        )
    }

    fun remove(): Boolean {
        if (!isWindows()) return false
        return removeWindowsTask()
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")
    }

    private fun currentExecutablePath(): String? {
        val commandPath = runCatching {
            ProcessHandle.current().info().command().orElse("")
        }.getOrDefault("")
            .trim()
        if (commandPath.isBlank()) return null

        val file = File(commandPath)
        val name = file.name.lowercase(Locale.ROOT)

        // Ignore dev runtime (java.exe/javaw.exe). Scheduler should only target packaged app executable.
        if (name == "java.exe" || name == "javaw.exe") return null
        if (!file.exists()) return null
        return file.absolutePath
    }

    private fun windowsScheduleToken(frequency: UpdateCheckFrequency): String {
        return when (frequency) {
            UpdateCheckFrequency.DAILY -> "DAILY"
            UpdateCheckFrequency.WEEKLY -> "WEEKLY"
            UpdateCheckFrequency.MONTHLY -> "MONTHLY"
            UpdateCheckFrequency.NEVER -> "DAILY"
        }
    }

    private fun createOrUpdateWindowsTask(command: String, schedule: String): Boolean {
        val args = mutableListOf(
            "schtasks",
            "/Create",
            "/TN",
            TaskName,
            "/SC",
            schedule,
            "/TR",
            command,
            "/F",
        )
        if (schedule == "WEEKLY") {
            args += listOf("/D", "MON")
        } else if (schedule == "MONTHLY") {
            args += listOf("/D", "1")
        }
        return runProcess(args)
    }

    private fun removeWindowsTask(): Boolean {
        return runProcess(
            listOf("schtasks", "/Delete", "/TN", TaskName, "/F"),
            treatMissingAsSuccess = true,
        )
    }

    private fun runProcess(
        command: List<String>,
        treatMissingAsSuccess: Boolean = false,
    ): Boolean {
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            if (code == 0) return@runCatching true
            if (treatMissingAsSuccess &&
                (output.contains("cannot find the file specified", ignoreCase = true) ||
                    output.contains("No se puede encontrar el archivo especificado", ignoreCase = true))
            ) {
                return@runCatching true
            }
            false
        }.getOrDefault(false)
    }
}
