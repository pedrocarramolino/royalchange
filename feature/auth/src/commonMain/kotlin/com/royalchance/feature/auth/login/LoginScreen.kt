package com.royalchance.feature.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.GoogleSignInButton
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.RoyalPasswordField
import com.royalchance.core.designsystem.component.RoyalPrimaryButton
import com.royalchance.core.designsystem.component.RoyalTextButton
import com.royalchance.core.designsystem.component.RoyalTextField
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.feature.auth.components.AuthScaffold
import com.royalchance.feature.auth.message
import com.royalchance.feature.auth.resources.Res
import com.royalchance.feature.auth.resources.auth_continue_with_google
import com.royalchance.feature.auth.resources.auth_or
import com.royalchance.feature.auth.resources.field_email
import com.royalchance.feature.auth.resources.field_password
import com.royalchance.feature.auth.resources.login_create_account
import com.royalchance.feature.auth.resources.login_forgot_password
import com.royalchance.feature.auth.resources.login_no_account
import com.royalchance.feature.auth.resources.login_submit
import com.royalchance.feature.auth.resources.login_subtitle
import com.royalchance.feature.auth.resources.login_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LoginScreen(
    viewModel: LoginViewModel,
    onBack: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onCreateAccount: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    AuthScaffold(
        title = stringResource(Res.string.login_title),
        subtitle = stringResource(Res.string.login_subtitle),
        onBack = onBack,
    ) {
        RoyalTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(Res.string.field_email),
            error = state.emailError?.message(),
            leadingIcon = RoyalIcons.Mail,
            contentType = ContentType.EmailAddress,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            enabled = !state.isBusy,
        )
        RoyalPasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(Res.string.field_password),
            error = state.passwordError?.message(),
            contentType = ContentType.Password,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            enabled = !state.isBusy,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            RoyalTextButton(
                text = stringResource(Res.string.login_forgot_password),
                onClick = { onForgotPassword(state.email.trim()) },
            )
        }
        state.authError?.let { InfoBanner(message = it.message(), tone = BannerTone.Error) }
        RoyalPrimaryButton(
            text = stringResource(Res.string.login_submit),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
            enabled = !state.isGoogleLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        OrDivider()
        GoogleSignInButton(
            text = stringResource(Res.string.auth_continue_with_google),
            onClick = viewModel::signInWithGoogle,
            loading = state.isGoogleLoading,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.login_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RoyalTextButton(text = stringResource(Res.string.login_create_account), onClick = onCreateAccount)
        }
    }
}

@Composable
internal fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(Res.string.auth_or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = RoyalSpacing.m),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
