package com.anitail.music.ui.screens.settings

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.DiscordActivityNameKey
import com.anitail.music.constants.DiscordActivityTypeKey
import com.anitail.music.constants.DiscordAdvancedModeKey
import com.anitail.music.constants.DiscordAvatarKey
import com.anitail.music.constants.DiscordAvatarUrlKey
import com.anitail.music.constants.DiscordButton1EnabledKey
import com.anitail.music.constants.DiscordButton1LabelKey
import com.anitail.music.constants.DiscordButton1UrlKey
import com.anitail.music.constants.DiscordButton2EnabledKey
import com.anitail.music.constants.DiscordButton2LabelKey
import com.anitail.music.constants.DiscordButton2UrlKey
import com.anitail.music.constants.DiscordDetailsTemplateKey
import com.anitail.music.constants.DiscordInfoDismissedKey
import com.anitail.music.constants.DiscordNameKey
import com.anitail.music.constants.DiscordStateTemplateKey
import com.anitail.music.constants.DiscordUserStatusKey
import com.anitail.music.constants.DiscordUsernameKey
import com.anitail.music.constants.EnableDiscordRPCKey
import com.anitail.music.db.entities.Song
import com.anitail.music.discord.DiscordDefaults
import com.anitail.music.discord.DiscordRpcManager
import com.anitail.music.discord.DiscordTemplateRenderer
import com.anitail.music.discord.DiscordTokenStore
import com.anitail.music.ui.component.DefaultDialog
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.component.ListDialog
import com.anitail.music.ui.component.PreferenceEntry
import com.anitail.music.ui.component.PreferenceGroupTitle
import com.anitail.music.ui.component.SwitchPreference
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.ui.utils.tvClickable
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.makeTimeString
import com.anitail.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun DiscordSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val song by playerConnection.currentSong.collectAsStateWithLifecycle(null)
    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    var discordAvatar by rememberPreference(DiscordAvatarKey, "")
    var discordAvatarUrl by rememberPreference(DiscordAvatarUrlKey, "")
    var discordAccessToken by remember { mutableStateOf<String?>(null) }
    var infoDismissed by rememberPreference(DiscordInfoDismissedKey, false)

    val (discordRPC, onDiscordRPCChange) = rememberPreference(EnableDiscordRPCKey, true)
    val (advancedMode, onAdvancedModeChange) = rememberPreference(DiscordAdvancedModeKey, false)

    val (activityType, onActivityTypeChange) = rememberPreference(DiscordActivityTypeKey, DiscordDefaults.ACTIVITY_TYPE)
    val (activityName, onActivityNameChange) = rememberPreference(DiscordActivityNameKey, DiscordDefaults.ACTIVITY_NAME)
    val (stateTemplate, onStateTemplateChange) = rememberPreference(DiscordStateTemplateKey, DiscordDefaults.STATE_TEMPLATE)
    val (detailsTemplate, onDetailsTemplateChange) = rememberPreference(DiscordDetailsTemplateKey, DiscordDefaults.DETAILS_TEMPLATE)
    val (btn1Enabled, onBtn1EnabledChange) = rememberPreference(DiscordButton1EnabledKey, true)
    val (btn1Label, onBtn1LabelChange) = rememberPreference(DiscordButton1LabelKey, DiscordDefaults.BUTTON1_LABEL)
    val (btn1Url, onBtn1UrlChange) = rememberPreference(DiscordButton1UrlKey, DiscordDefaults.BUTTON1_URL_TEMPLATE)
    val (btn2Enabled, onBtn2EnabledChange) = rememberPreference(DiscordButton2EnabledKey, true)
    val (btn2Label, onBtn2LabelChange) = rememberPreference(DiscordButton2LabelKey, DiscordDefaults.BUTTON2_LABEL)
    val (btn2Url, onBtn2UrlChange) = rememberPreference(DiscordButton2UrlKey, DiscordDefaults.BUTTON2_URL)
    val (userStatus, onUserStatusChange) = rememberPreference(DiscordUserStatusKey, DiscordDefaults.USER_STATUS)

    var showActivityTypeDialog by remember { mutableStateOf(false) }
    var showActivityNameDialog by remember { mutableStateOf(false) }
    var showStateDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showBtn1LabelDialog by remember { mutableStateOf(false) }
    var showBtn1UrlDialog by remember { mutableStateOf(false) }
    var showBtn2LabelDialog by remember { mutableStateOf(false) }
    var showBtn2UrlDialog by remember { mutableStateOf(false) }
    var showUserStatusDialog by remember { mutableStateOf(false) }

    val isLoggedIn = remember(discordName) { discordName.isNotEmpty() }
    var isBusy by remember { mutableStateOf(false) }

    val listenManager = playerConnection.service.discordListenAlongManager
    val activeLobbyId by (listenManager?.activeLobbyId ?: kotlinx.coroutines.flow.MutableStateFlow(
        null
    )).collectAsStateWithLifecycle()
    val isListenAlongHost by (listenManager?.isHost ?: kotlinx.coroutines.flow.MutableStateFlow(
        false
    )).collectAsStateWithLifecycle()

    val connectionStatus by DiscordRpcManager.connectionStatus.collectAsState()

    val statusText = when {
        connectionStatus == DiscordRpcManager.Status.Connected -> "Connected"
        connectionStatus == DiscordRpcManager.Status.Authorizing -> "Authorizing..."
        !DiscordRpcManager.isInitialized() -> "Not initialized"
        DiscordRpcManager.isAuthorized() -> "Authorized"
        else -> ""
    }

    LaunchedEffect(Unit) {
        if (!DiscordRpcManager.isInitialized()) {
            DiscordRpcManager.init(context)
        }
    }

    LaunchedEffect(Unit) {
        val token = DiscordTokenStore.retrieveSuspend()
        if (token != null) {
            discordAccessToken = token
            if (!DiscordRpcManager.isAuthorized()) {
                DiscordRpcManager.reconnectWithToken(token)
            }
        }
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
            }
        }
    }

    val activityTypeLabel = when (activityType) {
        "0" -> stringResource(R.string.discord_activity_playing)
        "3" -> stringResource(R.string.discord_activity_watching)
        "5" -> stringResource(R.string.discord_activity_competing)
        else -> stringResource(R.string.discord_activity_listening)
    }

    val userStatusLabel = when (userStatus) {
        "idle" -> stringResource(R.string.discord_status_idle)
        "dnd" -> stringResource(R.string.discord_status_dnd)
        else -> stringResource(R.string.discord_status_online)
    }

    fun onPrefChanged() {
        DiscordRpcManager.notifySettingsChanged()
    }

    Column(
        modifier =
            Modifier
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )

        AnimatedVisibility(visible = !infoDismissed) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.discord_information_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { infoDismissed = true },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                }
            }
        }

        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Card(
            shape = RoundedCornerShape(28.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 20.dp,
                            bottom = if (isLoggedIn) 20.dp else 8.dp,
                        )
                        .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(56.dp)) {
                    if (isLoggedIn && discordAvatar.isNotEmpty()) {
                        AsyncImage(
                            model = discordAvatar,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.discord),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .align(Alignment.Center)
                                    .alpha(0.4f),
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (isLoggedIn) {
                                discordName
                            } else {
                                stringResource(R.string.not_logged_in)
                            },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(if (isLoggedIn) 1f else 0.5f),
                    )
                    if (discordUsername.isNotEmpty()) {
                        Text(
                            text = "@$discordUsername",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isLoggedIn) {
                        Text(
                            text = stringResource(R.string.discord_connect_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isLoggedIn) {
                    OutlinedButton(onClick = {
                        discordName = ""
                        discordUsername = ""
                        discordAvatar = ""
                        discordAvatarUrl = ""
                        discordAccessToken = null
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                DiscordRpcManager.logout()
                            } catch (e: Exception) {
                                Timber.e(e, "Discord logout failed")
                            }
                        }
                    }) {
                        Text(stringResource(R.string.action_logout))
                    }
                }
            }

            if (!isLoggedIn) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!isBusy) {
                        OutlinedButton(
                            onClick = {
                                isBusy = true
                                DiscordRpcManager.authorize { success ->
                                    isBusy = false
                                    if (success) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val token = DiscordRpcManager.getAccessToken()
                                            if (token != null) {
                                                val user = DiscordRpcManager.fetchCurrentUser(token)
                                                if (user != null) {
                                                    withContext(Dispatchers.Main) {
                                                        discordAccessToken = token
                                                        discordUsername = user.username
                                                        discordName = user.name
                                                        discordAvatar = user.avatar ?: ""
                                                        discordAvatarUrl = user.avatar ?: ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.action_login))
                        }
                    }
                }
            }

            if (isBusy) {
                LinearProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.options)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_discord_rpc)) },
            checked = discordRPC,
            onCheckedChange = {
                onDiscordRPCChange(it)
                onPrefChanged()
            },
            isEnabled = isLoggedIn,
        )

        val isDiscordReady = connectionStatus == DiscordRpcManager.Status.Connected

        if (activeLobbyId == null) {
            SwitchPreference(
                title = { Text(stringResource(R.string.discord_listen_along_host)) },
                description = stringResource(R.string.discord_listen_along_host_desc),
                checked = isListenAlongHost,
                onCheckedChange = { checked ->
                    if (checked) {
                        listenManager?.startHostSession()
                    } else {
                        listenManager?.leaveSession()
                    }
                },
                isEnabled = isLoggedIn && discordRPC && isDiscordReady,
            )
            if (isListenAlongHost) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        } else {
            if (isListenAlongHost) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.discord_listen_along_active_host)) },
                    description = stringResource(
                        R.string.discord_listen_along_lobby_id,
                        activeLobbyId.toString()
                    ),
                    onClick = {
                        listenManager?.leaveSession()
                    },
                    trailingContent = {
                        Text(
                            text = stringResource(R.string.discord_listen_along_stop),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            } else {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.discord_listen_along_active_guest)) },
                    description = stringResource(
                        R.string.discord_listen_along_lobby_id,
                        activeLobbyId.toString()
                    ),
                    onClick = {
                        listenManager?.leaveSession()
                    },
                    trailingContent = {
                        Text(
                            text = stringResource(R.string.discord_listen_along_leave),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            }
        }

        SwitchPreference(
            title = { Text(stringResource(R.string.discord_advanced_mode)) },
            description = stringResource(R.string.discord_advanced_mode_description),
            checked = advancedMode,
            onCheckedChange = {
                onAdvancedModeChange(it)
                onPrefChanged()
            },
            isEnabled = isLoggedIn,
        )

        AnimatedVisibility(
            visible = advancedMode && isLoggedIn,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                PreferenceGroupTitle(
                    title = stringResource(R.string.discord_presence)
                )

                PreferenceEntry(
                    title = { Text(stringResource(R.string.discord_activity_type)) },
                    description = activityTypeLabel,
                    onClick = { showActivityTypeDialog = true },
                )

                PreferenceEntry(
                    title = { Text(stringResource(R.string.discord_activity_name)) },
                    description = activityName.ifEmpty { "(default)" },
                    onClick = { showActivityNameDialog = true },
                )

                PreferenceEntry(
                    title = { Text(stringResource(R.string.discord_state)) },
                    description = stateTemplate,
                    onClick = { showStateDialog = true },
                )

                PreferenceEntry(
                    title = { Text(stringResource(R.string.discord_details)) },
                    description = detailsTemplate,
                    onClick = { showDetailsDialog = true },
                )

                PreferenceGroupTitle(
                    title = stringResource(R.string.discord_buttons)
                )

                SwitchPreference(
                    title = { Text(stringResource(R.string.discord_enable_button_1)) },
                    checked = btn1Enabled,
                    onCheckedChange = {
                        onBtn1EnabledChange(it)
                        onPrefChanged()
                    },
                )

                if (btn1Enabled) {
                    PreferenceEntry(
                        title = { Text("${stringResource(R.string.discord_button_1)} — ${stringResource(R.string.discord_button_label)}") },
                        description = btn1Label,
                        onClick = { showBtn1LabelDialog = true },
                    )
                    PreferenceEntry(
                        title = { Text("${stringResource(R.string.discord_button_1)} — ${stringResource(R.string.discord_button_url)}") },
                        description = btn1Url,
                        onClick = { showBtn1UrlDialog = true },
                    )
                }

                SwitchPreference(
                    title = { Text(stringResource(R.string.discord_enable_button_2)) },
                    checked = btn2Enabled,
                    onCheckedChange = {
                        onBtn2EnabledChange(it)
                        onPrefChanged()
                    },
                )

                if (btn2Enabled) {
                    PreferenceEntry(
                        title = { Text("${stringResource(R.string.discord_button_2)} — ${stringResource(R.string.discord_button_label)}") },
                        description = btn2Label,
                        onClick = { showBtn2LabelDialog = true },
                    )
                    PreferenceEntry(
                        title = { Text("${stringResource(R.string.discord_button_2)} — ${stringResource(R.string.discord_button_url)}") },
                        description = btn2Url,
                        onClick = { showBtn2UrlDialog = true },
                    )
                }

                PreferenceGroupTitle(
                    title = stringResource(R.string.discord_status)
                )

                PreferenceEntry(
                    title = { Text(stringResource(R.string.discord_status)) },
                    description = userStatusLabel,
                    onClick = { showUserStatusDialog = true },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.discord_rpc_preview),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
        )

        RichPresence(
            song = song,
            currentPlaybackTimeMillis = position,
            advancedMode = advancedMode,
            activityType = activityType,
            stateTemplate = stateTemplate,
            detailsTemplate = detailsTemplate,
            btn1Label = btn1Label,
            btn1Url = btn1Url,
            btn1Enabled = btn1Enabled,
            btn2Label = btn2Label,
            btn2Url = btn2Url,
            btn2Enabled = btn2Enabled,
        )

        Spacer(Modifier.height(24.dp))
    }

    if (showActivityTypeDialog) {
        val values = listOf(DiscordDefaults.ACTIVITY_TYPE_LISTENING, DiscordDefaults.ACTIVITY_TYPE_PLAYING, DiscordDefaults.ACTIVITY_TYPE_WATCHING, DiscordDefaults.ACTIVITY_TYPE_COMPETING)
        ListDialog(
            onDismiss = { showActivityTypeDialog = false },
        ) {
            items(values) { value ->
                val label = when (value) {
                    "0" -> stringResource(R.string.discord_activity_playing)
                    "3" -> stringResource(R.string.discord_activity_watching)
                    "5" -> stringResource(R.string.discord_activity_competing)
                    else -> stringResource(R.string.discord_activity_listening)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .tvClickable {
                            showActivityTypeDialog = false
                            onActivityTypeChange(value)
                            coroutineScope.launch(Dispatchers.IO) {
                                context.dataStore.edit { it[DiscordActivityTypeKey] = value }
                                withContext(Dispatchers.Main) { onPrefChanged() }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButton(
                        selected = value == activityType,
                        onClick = null,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }

    if (showActivityNameDialog) {
        TemplateFieldDialog(
            title = stringResource(R.string.discord_activity_name),
            value = activityName,
            onDone = {
                onActivityNameChange(it)
                coroutineScope.launch(Dispatchers.IO) {
                    context.dataStore.edit { prefs -> prefs[DiscordActivityNameKey] = it }
                    withContext(Dispatchers.Main) { onPrefChanged() }
                }
            },
            onDismiss = { showActivityNameDialog = false },
        )
    }

    if (showStateDialog) {
        TemplateFieldDialog(
            title = stringResource(R.string.discord_state),
            value = stateTemplate,
            onDone = {
                onStateTemplateChange(it)
                coroutineScope.launch(Dispatchers.IO) {
                    context.dataStore.edit { prefs -> prefs[DiscordStateTemplateKey] = it }
                    withContext(Dispatchers.Main) { onPrefChanged() }
                }
            },
            onDismiss = { showStateDialog = false },
        )
    }

    if (showDetailsDialog) {
        TemplateFieldDialog(
            title = stringResource(R.string.discord_details),
            value = detailsTemplate,
            onDone = {
                onDetailsTemplateChange(it)
                coroutineScope.launch(Dispatchers.IO) {
                    context.dataStore.edit { prefs -> prefs[DiscordDetailsTemplateKey] = it }
                    withContext(Dispatchers.Main) { onPrefChanged() }
                }
            },
            onDismiss = { showDetailsDialog = false },
        )
    }

    if (showBtn1LabelDialog) {
        TemplateFieldDialog(
            title = "${stringResource(R.string.discord_button_1)} — ${stringResource(R.string.discord_button_label)}",
            value = btn1Label,
            onDone = {
                onBtn1LabelChange(it)
                coroutineScope.launch(Dispatchers.IO) {
                    context.dataStore.edit { prefs -> prefs[DiscordButton1LabelKey] = it }
                    withContext(Dispatchers.Main) { onPrefChanged() }
                }
            },
            onDismiss = { showBtn1LabelDialog = false },
        )
    }

    if (showBtn1UrlDialog) {
        TemplateFieldDialog(
            title = "${stringResource(R.string.discord_button_1)} — ${stringResource(R.string.discord_button_url)}",
            value = btn1Url,
            onDone = {
                onBtn1UrlChange(it)
                coroutineScope.launch(Dispatchers.IO) {
                    context.dataStore.edit { prefs -> prefs[DiscordButton1UrlKey] = it }
                    withContext(Dispatchers.Main) { onPrefChanged() }
                }
            },
            onDismiss = { showBtn1UrlDialog = false },
        )
    }

    if (showBtn2LabelDialog) {
        TemplateFieldDialog(
            title = "${stringResource(R.string.discord_button_2)} — ${stringResource(R.string.discord_button_label)}",
            value = btn2Label,
            onDone = {
                onBtn2LabelChange(it)
                coroutineScope.launch(Dispatchers.IO) {
                    context.dataStore.edit { prefs -> prefs[DiscordButton2LabelKey] = it }
                    withContext(Dispatchers.Main) { onPrefChanged() }
                }
            },
            onDismiss = { showBtn2LabelDialog = false },
        )
    }

    if (showBtn2UrlDialog) {
        TemplateFieldDialog(
            title = "${stringResource(R.string.discord_button_2)} — ${stringResource(R.string.discord_button_url)}",
            value = btn2Url,
            onDone = {
                onBtn2UrlChange(it)
                coroutineScope.launch(Dispatchers.IO) {
                    context.dataStore.edit { prefs -> prefs[DiscordButton2UrlKey] = it }
                    withContext(Dispatchers.Main) { onPrefChanged() }
                }
            },
            onDismiss = { showBtn2UrlDialog = false },
        )
    }

    if (showUserStatusDialog) {
        val values = listOf(DiscordDefaults.USER_STATUS, DiscordDefaults.STATUS_IDLE, DiscordDefaults.STATUS_DND)
        ListDialog(
            onDismiss = { showUserStatusDialog = false },
        ) {
            items(values) { value ->
                val label = when (value) {
                    "idle" -> stringResource(R.string.discord_status_idle)
                    "dnd" -> stringResource(R.string.discord_status_dnd)
                    else -> stringResource(R.string.discord_status_online)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .tvClickable {
                            showUserStatusDialog = false
                            onUserStatusChange(value)
                            coroutineScope.launch(Dispatchers.IO) {
                                context.dataStore.edit { prefs ->
                                    prefs[DiscordUserStatusKey] = value
                                }
                                withContext(Dispatchers.Main) { onPrefChanged() }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButton(
                        selected = value == userStatus,
                        onClick = null,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.discord_integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TemplateFieldDialog(
    title: String,
    value: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(value) }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(text = title) },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(onClick = {
                onDone(text)
                onDismiss()
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(title) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone(text); onDismiss() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DiscordTemplateRenderer.PLACEHOLDERS.forEach { placeholder ->
                SuggestionChip(
                    onClick = { text += placeholder },
                    label = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun RichPresence(
    song: Song?,
    currentPlaybackTimeMillis: Long = 0L,
    advancedMode: Boolean = false,
    activityType: String = DiscordDefaults.ACTIVITY_TYPE,
    stateTemplate: String = DiscordDefaults.STATE_TEMPLATE,
    detailsTemplate: String = DiscordDefaults.DETAILS_TEMPLATE,
    btn1Label: String = DiscordDefaults.BUTTON1_LABEL,
    btn1Url: String = DiscordDefaults.BUTTON1_URL_TEMPLATE,
    btn1Enabled: Boolean = true,
    btn2Label: String = DiscordDefaults.BUTTON2_LABEL,
    btn2Url: String = DiscordDefaults.BUTTON2_URL,
    btn2Enabled: Boolean = true,
) {
    val context = LocalContext.current

    val previewSongTitle = song?.song?.title ?: "Song Title"
    val previewArtistName = song?.artists?.joinToString { it.name } ?: "Artist"
    val previewAlbumName = song?.album?.title

    val renderedState = if (advancedMode) {
        DiscordTemplateRenderer.render(stateTemplate.ifEmpty { DiscordDefaults.STATE_TEMPLATE }, previewSongTitle, previewArtistName, previewAlbumName, song?.song?.id ?: "")
    } else {
        previewArtistName
    }
    val renderedDetails = if (advancedMode) {
        DiscordTemplateRenderer.render(detailsTemplate.ifEmpty { DiscordDefaults.DETAILS_TEMPLATE }, previewSongTitle, previewArtistName, previewAlbumName, song?.song?.id ?: "")
    } else {
        previewSongTitle
    }
    val renderedBtn1Label = if (advancedMode) {
        DiscordTemplateRenderer.render(btn1Label.ifEmpty { DiscordDefaults.BUTTON1_LABEL }, previewSongTitle, previewArtistName, previewAlbumName, song?.song?.id ?: "")
    } else {
        DiscordDefaults.BUTTON1_LABEL
    }
    val renderedBtn2Label = if (advancedMode) {
        DiscordTemplateRenderer.render(btn2Label.ifEmpty { DiscordDefaults.BUTTON2_LABEL }, previewSongTitle, previewArtistName, previewAlbumName, song?.song?.id ?: "")
    } else {
        DiscordDefaults.BUTTON2_LABEL
    }

    val activityPrefix = when (activityType) {
        "0" -> stringResource(R.string.discord_activity_playing)
        "3" -> stringResource(R.string.discord_activity_watching)
        "5" -> stringResource(R.string.discord_activity_competing)
        else -> stringResource(R.string.discord_activity_listening)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$activityPrefix $renderedState",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(108.dp)) {
                    AsyncImage(
                        model = song?.song?.thumbnailUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .align(Alignment.TopStart)
                                .run {
                                    if (song == null) {
                                        border(
                                            2.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            RoundedCornerShape(3.dp),
                                        )
                                    } else {
                                        this
                                    }
                                },
                    )

                    song?.artists?.firstOrNull()?.thumbnailUrl?.let {
                        Box(
                            modifier =
                                Modifier
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        CircleShape,
                                    )
                                    .padding(2.dp)
                                    .align(Alignment.BottomEnd),
                        ) {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                            )
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = renderedDetails,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = renderedState,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (song != null) {
                        SongProgressBar(
                            currentTimeMillis = currentPlaybackTimeMillis,
                            durationMillis = song.song.duration.times(1000L),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (btn1Enabled) {
                OutlinedButton(
                    enabled = song != null,
                    onClick = {
                        val intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "${DiscordDefaults.YOUTUBE_WATCH_URL}${song?.song?.id}".toUri(),
                                )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(renderedBtn1Label)
                }
            }

            if (btn2Enabled) {
                OutlinedButton(
                    onClick = {
                        val intent =
                            Intent(
                                Intent.ACTION_VIEW,
                                DiscordDefaults.BUTTON2_URL.toUri(),
                            )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(renderedBtn2Label)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongProgressBar(
    currentTimeMillis: Long,
    durationMillis: Long,
) {
    val progress = if (durationMillis > 0) currentTimeMillis.toFloat() / durationMillis else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))

        LinearWavyProgressIndicator(
            progress = { progress },
            amplitude = { 1f },
            wavelength = 16.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = makeTimeString(currentTimeMillis),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                fontSize = 12.sp,
            )
            Text(
                text = makeTimeString(durationMillis),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                fontSize = 12.sp,
            )
        }
    }
}
