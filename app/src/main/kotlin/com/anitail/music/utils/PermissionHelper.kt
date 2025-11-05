package com.anitail.music.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Helper class for managing storage permissions required for MediaStore downloads.
 *
 * Android storage permissions vary by API level:
 * - Android 13+ (API 33+): READ_MEDIA_AUDIO
 * - Android 10-12 (API 29-32): READ_EXTERNAL_STORAGE
 * - Android 9 and below (API ≤28): READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE
 * - Android 10+ (API 29+): WRITE_EXTERNAL_STORAGE not needed for MediaStore
 */
object PermissionHelper {

    /**
     * Check if storage permissions are granted for MediaStore WRITE operations.
     *
     * On Android 10+, while WRITE_EXTERNAL_STORAGE is not technically required for MediaStore writes,
     * we still need READ permissions to verify and access the downloaded files.
     *
     * @param context Application context
     * @return true if all required permissions are granted for writing
     */
    fun hasMediaStoreWritePermission(context: Context): Boolean {
        return when {
            // Android 13+ (API 33+) - Check READ_MEDIA_AUDIO
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                Timber.d("Android 13+: READ_MEDIA_AUDIO ${if (hasPermission) "granted" else "denied"}")
                hasPermission
            }

            // Android 10-12 (API 29-32) - Check READ_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                Timber.d("Android 10-12: READ_EXTERNAL_STORAGE ${if (hasPermission) "granted" else "denied"}")
                hasPermission
            }

            // Android 9 and below (API ≤28) - Requires WRITE_EXTERNAL_STORAGE
            else -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                Timber.d("Android 9 or below: WRITE_EXTERNAL_STORAGE ${if (hasPermission) "granted" else "denied"}")
                hasPermission
            }
        }
    }

    /**
     * Check if storage permissions are granted for MediaStore READ operations
     *
     * @param context Application context
     * @return true if all required permissions are granted for reading
     */
    fun hasStoragePermission(context: Context): Boolean {
        return when {
            // Android 13+ (API 33+) - Requires READ_MEDIA_AUDIO
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }

            // Android 10-12 (API 29-32) - Requires READ_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            // Android 9 and below (API ≤28) - Requires both READ and WRITE
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * Get the required storage permissions for MediaStore WRITE operations
     *
     * @return Array of permission strings needed for writing
     */
    fun getRequiredWritePermissions(): Array<String> {
        return when {
            // Android 13+ (API 33+) - Request READ_MEDIA_AUDIO
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            }

            // Android 10-12 (API 29-32) - Request READ_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            // Android 9 and below (API ≤28) - Request WRITE_EXTERNAL_STORAGE
            else -> {
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    /**
     * Get the required storage permissions for MediaStore READ operations
     *
     * @return Array of permission strings needed for reading
     */
    fun getRequiredPermissions(): Array<String> {
        return when {
            // Android 13+ (API 33+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            }

            // Android 10-12 (API 29-32)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            // Android 9 and below (API ≤28)
            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    /**
     * Create a permission launcher for requesting storage permissions
     *
     * Usage in Activity:
     * ```
     * val permissionLauncher = PermissionHelper.createPermissionLauncher(
     *     activity = this,
     *     onGranted = { /* proceed with download */ },
     *     onDenied = { /* show rationale or error */ }
     * )
     *
     * // Later, when needed:
     * PermissionHelper.requestStoragePermission(this, permissionLauncher)
     * ```
     *
     * @param activity The activity to register the launcher with
     * @param onGranted Callback when permission is granted
     * @param onDenied Callback when permission is denied
     * @return ActivityResultLauncher for permissions
     */
    fun createPermissionLauncher(
        activity: ComponentActivity,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Timber.d("Storage permissions granted")
            onGranted()
        } else {
            Timber.w("Storage permissions denied: $permissions")
            onDenied()
        }
    }

    /**
     * Request storage permissions using the provided launcher
     *
     * @param context Application context (for permission check)
     * @param launcher Permission launcher created with createPermissionLauncher
     */
    fun requestStoragePermission(
        context: Context,
        launcher: ActivityResultLauncher<Array<String>>
    ) {
        if (hasStoragePermission(context)) {
            Timber.d("Storage permissions already granted")
            return
        }

        val permissions = getRequiredPermissions()
        Timber.d("Requesting storage permissions: ${permissions.contentToString()}")
        launcher.launch(permissions)
    }

    /**
     * Check if we should show rationale for storage permissions
     *
     * @param activity The activity to check
     * @return true if we should show permission rationale
     */
    fun shouldShowRationale(activity: ComponentActivity): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.any { permission ->
            activity.shouldShowRequestPermissionRationale(permission)
        }
    }

    /**
     * Get a user-friendly description of why storage permission is needed
     *
     * @return Explanation string
     */
    fun getPermissionRationale(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                "This app needs access to your audio files to download and play music from your device's Music folder."
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                "This app needs storage permission to download and save music to your device's Music folder."
            }

            else -> {
                "This app needs storage permissions to download and save music to your device."
            }
        }
    }
}
