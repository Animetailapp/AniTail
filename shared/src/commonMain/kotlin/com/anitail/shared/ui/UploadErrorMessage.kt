package com.anitail.shared.ui

import com.anitail.shared.api.UploadFailure
import com.anitail.shared.api.UploadSongResult
import com.anitail.shared.generated.resources.Res
import com.anitail.shared.generated.resources.upload_already_exists
import com.anitail.shared.generated.resources.upload_empty_file
import com.anitail.shared.generated.resources.upload_failed
import com.anitail.shared.generated.resources.upload_http_error
import com.anitail.shared.generated.resources.upload_sign_in_required
import com.anitail.shared.generated.resources.upload_too_large
import com.anitail.shared.generated.resources.upload_unsupported_type
import org.jetbrains.compose.resources.getString

suspend fun UploadSongResult.localizedErrorMessage(): String =
    when (failure) {
        UploadFailure.SIGN_IN_REQUIRED -> getString(Res.string.upload_sign_in_required)
        UploadFailure.UNSUPPORTED_TYPE -> getString(Res.string.upload_unsupported_type)
        UploadFailure.EMPTY_FILE -> getString(Res.string.upload_empty_file)
        UploadFailure.TOO_LARGE -> getString(Res.string.upload_too_large)
        UploadFailure.ALREADY_UPLOADED -> getString(Res.string.upload_already_exists)
        UploadFailure.HTTP -> httpStatus?.let { getString(Res.string.upload_http_error, it) } ?: getString(Res.string.upload_failed)
        else -> getString(Res.string.upload_failed)
    }
