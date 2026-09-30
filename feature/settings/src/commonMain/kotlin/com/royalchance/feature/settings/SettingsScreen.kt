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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.InfoBanner
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
import com.royalchance.feature.settings.resources.settings_create_account
import com.royalchance.feature.settings.resources.settings_delete_account
import com.royalchance.feature.settings.resources.settings_delete_confirm
import com.royalchance.feature.settings.resources.settings_delete_message
import com.royalchance.feature.settings.resources.settings_delete_title
import com.royalchance.feature.settings.resources.settings_email_unverified
import com.royalchance.feature.settings.resources.settings_email_verified
import com.royalchance.feature.settings.resources.settings_error
import com.royalchance.feature.settings.resources.settings_guest_banner
import com.royalchance.feature.settings.resources.settings_guest_name
import com.royalchance.feature.settings.resources.settings_privacy
import com.royalchance.feature.settings.resources.settings_section_account
import com.royalchance.feature.settings.resources.settings_section_appearance
import com.royalchance.feature.settings.resources.settings_section_info
import com.royalchance.feature.settings.resources.settings_sign_out
import com.royalchance.feature.settings.resources.settings_sign_out_guest_message
import com.royalchance.feature.settings.resources.settings_sign_out_message
import com.royalchance.feature.settings.resources.settings_sign_out_title
import com.royalchance.feature.settings.resources.settings_terms
import com.royalchance.feature.settings.resources.settings_theme
import com.royalchance.feature.settings.resources.settings_theme_dark
import com.royalchance.feature.settings.resources.settings_theme_light
import com.royalchance.feature.settings.resources.settings_theme_system
import com.royalchance.feature.settings.resources.settings_title
import com.royalchance.feature.settings.resources.settings_verify_email
import com.royalchance.feature.settings.resources.settings_version
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    appVersion: String,
    onCreateAccount: () -> Unit,
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
                if (state.showError) InfoBanner(message = stringResource(Res.string.settings_error), tone = BannerTone.Error)

                SectionHeader(stringResource(Res.string.settings_section_account))
                if (user != null) AccountCard(user)
                if (user?.isGuest == true) {
                    InfoBanner(
                        message = stringResource(Res.string.settings_guest_banner),
                        tone = BannerTone.Warning,
                        actionLabel = stringResource(Res.string.settings_create_account),
                        onAction = onCreateAccount,
                    )
                }
                if (user != null && !user.isGuest && !user.isEmailVerified) {
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
                if (user != null && !user.isGuest) {
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
            message = stringResource(
                if (user?.isGuest == true) Res.string.settings_sign_out_guest_message else Res.string.settings_sign_out_message,
            ),
            confirmLabel = stringResource(Res.string.settings_sign_out),
            destructive = user?.isGuest == true,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissConfirmation,
        )
        PendingConfirmation.DeleteAccount -> ConfirmationDialog(
            title = stringResource(Res.string.settings_delete_title),
            message = stringResource(Res.string.settings_delete_message),
            confirmLabel = stringResource(Res.string.settings_delete_confirm),
            destructive = true,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissConfirmation,
        )
        null -> Unit
    }
}

@Composable
private fun AccountCard(user: AuthUser) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(RoyalSpacing.l)) {
            AvatarBadge(avatar = user.profile?.avatar ?: AvatarId.SpadeGold, size = 56.dp)
            Spacer(Modifier.width(RoyalSpacing.l))
            Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xxs)) {
                Text(
                    text = user.profile?.alias ?: stringResource(Res.string.settings_guest_name),
                    style = MaterialTheme.typography.titleLarge,
                )
                user.email?.let { email ->
                    Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = stringResource(if (user.isEmailVerified) Res.string.settings_email_verified else Res.string.settings_email_unverified),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (user.isEmailVerified) RoyalTheme.casinoColors.success else RoyalTheme.casinoColors.warning,
                    )
                }
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
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            RoyalTextButton(
                text = confirmLabel,
                onClick = onConfirm,
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
