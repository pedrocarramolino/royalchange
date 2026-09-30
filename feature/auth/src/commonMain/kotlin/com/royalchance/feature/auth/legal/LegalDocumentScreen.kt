package com.royalchance.feature.auth.legal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.domain.auth.LegalDocumentVersions
import com.royalchance.feature.auth.components.AuthScaffold
import com.royalchance.feature.auth.resources.Res
import com.royalchance.feature.auth.resources.legal_draft_notice
import com.royalchance.feature.auth.resources.legal_privacy_body
import com.royalchance.feature.auth.resources.legal_privacy_title
import com.royalchance.feature.auth.resources.legal_terms_body
import com.royalchance.feature.auth.resources.legal_terms_title
import org.jetbrains.compose.resources.stringResource

/** Documento legal a pantalla completa. La versión mostrada es la que se registra al aceptarlo. */
@Composable
internal fun LegalDocumentScreen(document: LegalDocument, onBack: () -> Unit) {
    val (title, body, version) = when (document) {
        LegalDocument.Terms -> Triple(Res.string.legal_terms_title, Res.string.legal_terms_body, LegalDocumentVersions.TERMS)
        LegalDocument.Privacy -> Triple(Res.string.legal_privacy_title, Res.string.legal_privacy_body, LegalDocumentVersions.PRIVACY)
    }
    AuthScaffold(title = stringResource(title), subtitle = "v$version", onBack = onBack) {
        InfoBanner(message = stringResource(Res.string.legal_draft_notice), tone = BannerTone.Warning)
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
    }
}
