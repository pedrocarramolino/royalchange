package com.royalchance.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.royalchance.feature.settings.resources.settings_reduced_motion_hint
import com.royalchance.feature.settings.resources.settings_reduced_motion
import com.royalchance.feature.settings.resources.settings_sound_hint
import com.royalchance.feature.settings.resources.settings_sound
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.RoyalPasswordField
import com.royalchance.core.designsystem.component.RoyalTextButton
import com.royalchance.core.designsystem.component.RoyalTopBar
import com.royalchance.core.designsystem.component.SectionHeader
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalSizes
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.AvatarBadge
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.settings.ThemePreference
import com.royalchance.feature.settings.resources.Res
import com.royalchance.feature.settings.resources.settings_cancel
import com.royalchance.feature.settings.resources.settings_delete_account
import com.royalchance.feature.settings.resources.settings_delete_confirm
import com.royalchance.feature.settings.resources.settings_delete_message
import com.royalchance.feature.settings.resources.settings_delete_password
import com.royalchance.feature.settings.resources.settings_delete_password_hint
import com.royalchance.feature.settings.resources.settings_delete_title
import com.royalchance.feature.settings.resources.settings_email_unverified
import com.royalchance.feature.settings.resources.settings_email_verified
import com.royalchance.feature.settings.resources.settings_error
import com.royalchance.feature.settings.resources.settings_error_recent_login
import com.royalchance.feature.settings.resources.settings_privacy
import com.royalchance.feature.settings.resources.settings_section_account
import com.royalchance.feature.settings.resources.settings_section_appearance
import com.royalchance.feature.settings.resources.settings_section_info
import com.royalchance.feature.settings.resources.settings_sign_out
import com.royalchance.feature.settings.resources.settings_sign_out_message
import com.royalchance.feature.settings.resources.settings_sign_out_title
import com.royalchance.feature.settings.resources.settings_terms
import com.royalchance.feature.settings.resources.settings_theme
import com.royalchance.feature.settings.resources.settings_theme_dark
import com.royalchance.feature.settings.resources.settings_theme_light
import com.royalchance.feature.settings.resources.settings_theme_system
import com.royalchance.feature.settings.resources.settings_title
import com.royalchance.feature.settings.resources.settings_verification_sent
import com.royalchance.feature.settings.resources.settings_verify_email
import com.royalchance.feature.settings.resources.settings_version
import com.royalchance.feature.settings.resources.settings_wrong_password
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    appVersion: String,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val user = state.user

    Column(Modifier.fillMaxSize()) {
        RoyalTopBar(title = stringResource(Res.string.settings_title), windowInsets = WindowInsets(0))
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = RoyalSpacing.l, vertical = RoyalSpacing.s),
            ) {
                when (state.error) {
                    SettingsError.Generic -> InfoBanner(message = stringResource(Res.string.settings_error), tone = BannerTone.Error)
                    SettingsError.RequiresRecentLogin ->
                        InfoBanner(message = stringResource(Res.string.settings_error_recent_login), tone = BannerTone.Error)
                    // El error de contraseña se muestra dentro del diálogo de confirmación.
                    SettingsError.WrongPassword, null -> Unit
                }
                if (state.verificationSent) {
                    InfoBanner(
                        message = stringResource(Res.string.settings_verification_sent),
                        tone = BannerTone.Success,
                        icon = RoyalIcons.Mail,
                    )
                }

                SectionHeader(stringResource(Res.string.settings_section_account))
                if (user != null) AccountCard(user)
                if (user != null && !user.isEmailVerified) {
                    SettingsRow(
                        icon = RoyalIcons.Mail,
                        label = stringResource(Res.string.settings_verify_email),
                        enabled = !state.isBusy,
                        onClick = viewModel::sendVerification,
                    )
                }
                SettingsRow(
                    icon = RoyalIcons.Logout,
                    label = stringResource(Res.string.settings_sign_out),
                    enabled = !state.isBusy,
                    onClick = { viewModel.request(PendingConfirmation.SignOut) },
                )
                if (user != null) {
                    SettingsRow(
                        icon = RoyalIcons.Delete,
                        label = stringResource(Res.string.settings_delete_account),
                        color = MaterialTheme.colorScheme.error,
                        enabled = !state.isBusy,
                        onClick = { viewModel.request(PendingConfirmation.DeleteAccount) },
                    )
                }

                SectionHeader(stringResource(Res.string.settings_section_appearance))
                ThemeSelector(selected = state.theme, onSelect = viewModel::setTheme)
                SwitchRow(
                    label = stringResource(Res.string.settings_sound),
                    description = stringResource(Res.string.settings_sound_hint),
                    checked = state.soundEnabled,
                    onCheckedChange = viewModel::setSoundEnabled,
                )
                SwitchRow(
                    label = stringResource(Res.string.settings_reduced_motion),
                    description = stringResource(Res.string.settings_reduced_motion_hint),
                    checked = state.reducedMotion,
                    onCheckedChange = viewModel::setReducedMotion,
                )

                SectionHeader(stringResource(Res.string.settings_section_info))
                SettingsRow(icon = RoyalIcons.Info, label = stringResource(Res.string.settings_terms), onClick = onOpenTerms)
                SettingsRow(icon = RoyalIcons.Lock, label = stringResource(Res.string.settings_privacy), onClick = onOpenPrivacy)
                Text(
                    text = stringResource(Res.string.settings_version, appVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = RoyalSpacing.l).align(Alignment.CenterHorizontally),
                )
            }
        }
    }

    when (state.pendingConfirmation) {
        PendingConfirmation.SignOut -> ConfirmationDialog(
            title = stringResource(Res.string.settings_sign_out_title),
            message = stringResource(Res.string.settings_sign_out_message),
            confirmLabel = stringResource(Res.string.settings_sign_out),
            destructive = false,
            busy = state.isBusy,
            onConfirm = viewModel::confirmSignOut,
            onDismiss = viewModel::dismissConfirmation,
        )
        PendingConfirmation.DeleteAccount -> DeleteAccountDialog(
            busy = state.isBusy,
            wrongPassword = state.error == SettingsError.WrongPassword,
            onPasswordChange = viewModel::onDeletionPasswordChange,
            onConfirm = viewModel::confirmDeletion,
            onDismiss = viewModel::dismissConfirmation,
        )
        null -> Unit
    }
}

/** Confirmación de borrado con la contraseña de la cuenta. */
@Composable
private fun DeleteAccountDialog(
    busy: Boolean,
    wrongPassword: Boolean,
    onPasswordChange: () -> Unit,
    onConfirm: (password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    ConfirmationDialog(
        title = stringResource(Res.string.settings_delete_title),
        message = stringResource(Res.string.settings_delete_message),
        confirmLabel = stringResource(Res.string.settings_delete_confirm),
        destructive = true,
        busy = busy,
        confirmEnabled = password.isNotEmpty(),
        onConfirm = { onConfirm(password) },
        onDismiss = onDismiss,
    ) {
        RoyalPasswordField(
            value = password,
            onValueChange = {
                password = it
                onPasswordChange()
            },
            label = stringResource(Res.string.settings_delete_password),
            supportingText = stringResource(Res.string.settings_delete_password_hint),
            error = if (wrongPassword) stringResource(Res.string.settings_wrong_password) else null,
            enabled = !busy,
        )
    }
}

@Composable
private fun AccountCard(user: AuthUser) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(RoyalSpacing.l)) {
            AvatarBadge(avatar = user.profile?.avatar ?: AvatarId.SpadeGold, size = 56.dp)
            Spacer(Modifier.width(RoyalSpacing.l))
            Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xxs)) {
                user.profile?.let { profile ->
                    Text(text = profile.alias, style = MaterialTheme.typography.titleLarge)
                }
                Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = stringResource(if (user.isEmailVerified) Res.string.settings_email_verified else Res.string.settings_email_unverified),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (user.isEmailVerified) RoyalTheme.casinoColors.success else RoyalTheme.casinoColors.warning,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RoyalSizes.primaryActionHeight)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = RoyalSpacing.s),
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(RoyalSpacing.l))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Fila con interruptor; toda la fila es pulsable y se anuncia como interruptor. */
@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RoyalSizes.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ThemeSelector(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    val options = listOf(
        ThemePreference.Dark to stringResource(Res.string.settings_theme_dark),
        ThemePreference.Light to stringResource(Res.string.settings_theme_light),
        ThemePreference.System to stringResource(Res.string.settings_theme_system),
    )
    Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
        Text(stringResource(Res.string.settings_theme), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (theme, label) ->
                SegmentedButton(
                    selected = theme == selected,
                    onClick = { onSelect(theme) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = RoyalSizes.minTouchTarget),
                )
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    extraContent: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.m)) {
                Text(message)
                extraContent()
            }
        },
        confirmButton = {
            RoyalTextButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled && !busy,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        dismissButton = {
            RoyalTextButton(
                text = stringResource(Res.string.settings_cancel),
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
