package com.royalchance.feature.auth.forgot

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.RoyalPrimaryButton
import com.royalchance.core.designsystem.component.RoyalSecondaryButton
import com.royalchance.core.designsystem.component.RoyalTextField
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.feature.auth.components.AuthScaffold
import com.royalchance.feature.auth.message
import com.royalchance.feature.auth.resources.Res
import com.royalchance.feature.auth.resources.field_email
import com.royalchance.feature.auth.resources.forgot_back_to_login
import com.royalchance.feature.auth.resources.forgot_body
import com.royalchance.feature.auth.resources.forgot_sent
import com.royalchance.feature.auth.resources.forgot_submit
import com.royalchance.feature.auth.resources.forgot_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AuthScaffold(
        title = stringResource(Res.string.forgot_title),
        subtitle = stringResource(Res.string.forgot_body),
        onBack = onBack,
    ) {
        RoyalTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(Res.string.field_email),
            error = state.emailError?.message(),
            leadingIcon = RoyalIcons.Mail,
            contentType = ContentType.EmailAddress,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSend = { viewModel.submit() }),
            enabled = !state.isSubmitting,
        )
        state.authError?.let { InfoBanner(message = it.message(), tone = BannerTone.Error) }
        if (state.sent) {
            InfoBanner(message = stringResource(Res.string.forgot_sent), tone = BannerTone.Success, icon = RoyalIcons.Mail)
            RoyalSecondaryButton(
                text = stringResource(Res.string.forgot_back_to_login),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            RoyalPrimaryButton(
                text = stringResource(Res.string.forgot_submit),
                onClick = viewModel::submit,
                loading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
