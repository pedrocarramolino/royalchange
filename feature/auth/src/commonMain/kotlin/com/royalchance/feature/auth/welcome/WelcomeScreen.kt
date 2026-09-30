package com.royalchance.feature.auth.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.GoogleSignInButton
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.RoyalPrimaryButton
import com.royalchance.core.designsystem.component.RoyalSecondaryButton
import com.royalchance.core.designsystem.component.RoyalTextButton
import com.royalchance.core.designsystem.component.linkedText
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.feature.auth.components.AuthScaffold
import com.royalchance.feature.auth.legal.LegalDocument
import com.royalchance.feature.auth.message
import com.royalchance.feature.auth.resources.Res
import com.royalchance.feature.auth.resources.auth_continue_with_google
import com.royalchance.feature.auth.resources.guest_dialog_body
import com.royalchance.feature.auth.resources.guest_dialog_cancel
import com.royalchance.feature.auth.resources.guest_dialog_confirm
import com.royalchance.feature.auth.resources.guest_dialog_legal
import com.royalchance.feature.auth.resources.guest_dialog_title
import com.royalchance.feature.auth.resources.legal_privacy_title
import com.royalchance.feature.auth.resources.legal_terms_title
import com.royalchance.feature.auth.resources.welcome_create_account
import com.royalchance.feature.auth.resources.welcome_guest
import com.royalchance.feature.auth.resources.welcome_legal_notice
import com.royalchance.feature.auth.resources.welcome_sign_in
import com.royalchance.feature.auth.resources.welcome_subtitle
import com.royalchance.feature.auth.resources.welcome_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AuthScaffold(
        title = stringResource(Res.string.welcome_title),
        subtitle = stringResource(Res.string.welcome_subtitle),
        showBrandOnCompact = true,
    ) {
        state.error?.let { error ->
            InfoBanner(message = error.message(), tone = BannerTone.Error)
        }
        Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.m)) {
            RoyalPrimaryButton(
                text = stringResource(Res.string.welcome_create_account),
                onClick = onCreateAccount,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            RoyalSecondaryButton(
                text = stringResource(Res.string.welcome_sign_in),
                onClick = onSignIn,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            GoogleSignInButton(
                text = stringResource(Res.string.auth_continue_with_google),
                onClick = viewModel::signInWithGoogle,
                enabled = !state.isBusy,
                loading = state.isGoogleLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            RoyalTextButton(
                text = stringResource(Res.string.welcome_guest),
                onClick = viewModel::requestGuestAccess,
                enabled = !state.isBusy,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.height(RoyalSpacing.s))
        LegalFooter(onOpenLegal)
    }

    if (state.showGuestConsent) {
        GuestConsentDialog(onConfirm = viewModel::confirmGuestAccess, onDismiss = viewModel::dismissGuestConsent)
    }
}

@Composable
private fun LegalFooter(onOpenLegal: (LegalDocument) -> Unit) {
    val terms = stringResource(Res.string.legal_terms_title)
    val privacy = stringResource(Res.string.legal_privacy_title)
    Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.welcome_legal_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = linkedText("$terms  ·  $privacy", terms to { onOpenLegal(LegalDocument.Terms) }, privacy to { onOpenLegal(LegalDocument.Privacy) }),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GuestConsentDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.guest_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.m)) {
                Text(stringResource(Res.string.guest_dialog_body), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(Res.string.guest_dialog_legal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { RoyalTextButton(text = stringResource(Res.string.guest_dialog_confirm), onClick = onConfirm) },
        dismissButton = {
            RoyalTextButton(
                text = stringResource(Res.string.guest_dialog_cancel),
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
