package com.anitail.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import com.anitail.music.R

@Composable
fun PlaybackError(
    error: PlaybackException,
    retry: () -> Unit,
) {
    val errorCodeName = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IO_UNSPECIFIED"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "IO_NETWORK_CONNECTION_FAILED"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "IO_NETWORK_CONNECTION_TIMEOUT"
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "IO_INVALID_HTTP_CONTENT_TYPE"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "IO_BAD_HTTP_STATUS"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "IO_FILE_NOT_FOUND"
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "IO_NO_PERMISSION"
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "IO_CLEARTEXT_NOT_PERMITTED"
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "IO_READ_POSITION_OUT_OF_RANGE"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "DECODER_INIT_FAILED"
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "DECODER_QUERY_FAILED"
        PlaybackException.ERROR_CODE_DECODING_FAILED -> "DECODING_FAILED"
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "AUDIO_TRACK_INIT_FAILED"
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "AUDIO_TRACK_WRITE_FAILED"
        PlaybackException.ERROR_CODE_REMOTE_ERROR -> "REMOTE_ERROR"
        else -> "UNSPECIFIED"
    }

    val displayMessage = error.cause?.cause?.message 
        ?: error.cause?.message 
        ?: error.message 
        ?: stringResource(R.string.playback_failed_desc)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 24.dp)
    ) {
        // Red coral exclamation icon badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .background(Color(0xFFEF5350), CircleShape)
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main Error Title
        Text(
            text = stringResource(R.string.playback_failed),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Detailed error description
        Text(
            text = displayMessage,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Technical Code description
        Text(
            text = "Code: $errorCodeName (${error.errorCode})",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Premium Periwinkle/steel-blue retry button
        Button(
            onClick = retry,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.refresh),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.volver_a_intentar),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            )
        }
    }
}
