package com.royalchance.feature.auth.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.royalchance.core.designsystem.component.RoyalPrimaryButton
import com.royalchance.core.designsystem.component.RoyalSecondaryButton
import com.royalchance.core.designsystem.component.linkedText
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.feature.auth.components.AuthScaffold
import com.royalchance.feature.auth.legal.LegalDocument
import com.royalchance.feature.auth.resources.Res
import com.royalchance.feature.auth.resources.legal_privacy_title
import com.royalchance.feature.auth.resources.legal_terms_title
import com.royalchance.feature.auth.resources.welcome_create_account
import com.royalchance.feature.auth.resources.welcome_legal_notice
import com.royalchance.feature.auth.resources.welcome_sign_in
import com.royalchance.feature.auth.resources.welcome_subtitle
import com.royalchance.feature.auth.resources.welcome_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
) {
    AuthScaffold(
        title = stringResource(Res.string.welcome_title),
        subtitle = stringResource(Res.string.welcome_subtitle),
        showBrandOnCompact = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.m)) {
            RoyalPrimaryButton(
                text = stringResource(Res.string.welcome_create_account),
                onClick = onCreateAccount,
                modifier = Modifier.fillMaxWidth(),
            )
            RoyalSecondaryButton(
                text = stringResource(Res.string.welcome_sign_in),
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(RoyalSpacing.s))
        LegalFooter(onOpenLegal)
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
