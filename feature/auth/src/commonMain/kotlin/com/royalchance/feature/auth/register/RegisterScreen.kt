package com.royalchance.feature.auth.register

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.ChecklistItem
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.LabeledCheckbox
import com.royalchance.core.designsystem.component.RoyalDateField
import com.royalchance.core.designsystem.component.RoyalPasswordField
import com.royalchance.core.designsystem.component.RoyalPrimaryButton
import com.royalchance.core.designsystem.component.RoyalTextButton
import com.royalchance.core.designsystem.component.RoyalTextField
import com.royalchance.core.designsystem.component.SectionHeader
import com.royalchance.core.designsystem.component.SegmentedMeter
import com.royalchance.core.designsystem.component.linkedText
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.ui.AvatarPicker
import com.royalchance.core.ui.CountryField
import com.royalchance.domain.auth.validation.AgeRules
import com.royalchance.domain.auth.validation.FieldError
import com.royalchance.domain.auth.validation.PasswordPolicy
import com.royalchance.domain.auth.validation.PasswordRequirement
import com.royalchance.domain.auth.validation.RegistrationField
import com.royalchance.domain.auth.validation.RegistrationMode
import com.royalchance.feature.auth.color
import com.royalchance.feature.auth.components.AuthScaffold
import com.royalchance.feature.auth.label
import com.royalchance.feature.auth.legal.LegalDocument
import com.royalchance.feature.auth.message
import com.royalchance.feature.auth.segments
import com.royalchance.feature.auth.resources.Res
import com.royalchance.feature.auth.resources.complete_profile_sign_out
import com.royalchance.feature.auth.resources.complete_profile_submit
import com.royalchance.feature.auth.resources.complete_profile_subtitle
import com.royalchance.feature.auth.resources.complete_profile_title
import com.royalchance.feature.auth.resources.error_country_required
import com.royalchance.feature.auth.resources.field_email
import com.royalchance.feature.auth.resources.field_password
import com.royalchance.feature.auth.resources.legal_privacy_title
import com.royalchance.feature.auth.resources.legal_terms_title
import com.royalchance.feature.auth.resources.register_alias
import com.royalchance.feature.auth.resources.register_alias_help
import com.royalchance.feature.auth.resources.register_birth_date
import com.royalchance.feature.auth.resources.register_birth_date_help
import com.royalchance.feature.auth.resources.register_marketing
import com.royalchance.feature.auth.resources.register_password_confirmation
import com.royalchance.feature.auth.resources.register_section_access
import com.royalchance.feature.auth.resources.register_section_personal
import com.royalchance.feature.auth.resources.register_section_profile
import com.royalchance.feature.auth.resources.register_section_terms
import com.royalchance.feature.auth.resources.register_submit
import com.royalchance.feature.auth.resources.register_subtitle
import com.royalchance.feature.auth.resources.register_terms
import com.royalchance.feature.auth.resources.register_title
import com.royalchance.feature.auth.resources.register_virtual_chips
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.stringResource

/** Registro completo con email, o compleción del perfil de una cuenta ya autenticada. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RegisterScreen(
    viewModel: RegisterViewModel,
    onBack: (() -> Unit)?,
    onOpenLegal: (LegalDocument) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    val emailMode = state.mode == RegistrationMode.EmailAccount
    val focusManager = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    val requesters = remember { RegistrationField.entries.associateWith { BringIntoViewRequester() } }
    fun Modifier.anchor(field: RegistrationField) = bringIntoViewRequester(requesters.getValue(field))
    fun errorOf(field: RegistrationField): FieldError? = state.errors[field]

    LaunchedEffect(state.failedSubmissions) {
        if (state.failedSubmissions == 0) return@LaunchedEffect
        // Espera a que se dibujen los mensajes de error para medir sobre el diseño definitivo.
        withFrameNanos { }
        // Tras un envío con errores, lleva a la vista el primer campo a corregir (en orden de pantalla).
        RegistrationField.entries.firstOrNull { it in state.errors }?.let { requesters.getValue(it).bringIntoView() }
    }

    AuthScaffold(
        title = stringResource(if (emailMode) Res.string.register_title else Res.string.complete_profile_title),
        subtitle = stringResource(if (emailMode) Res.string.register_subtitle else Res.string.complete_profile_subtitle),
        onBack = onBack,
    ) {
        SectionHeader(stringResource(Res.string.register_section_profile))
        AvatarPicker(selected = form.avatar, onSelect = { avatar -> viewModel.updateForm { it.copy(avatar = avatar) } })
        RoyalTextField(
            value = form.alias,
            onValueChange = { value -> viewModel.updateForm { it.copy(alias = value) } },
            label = stringResource(Res.string.register_alias),
            error = errorOf(RegistrationField.Alias)?.message(),
            supportingText = stringResource(Res.string.register_alias_help),
            leadingIcon = RoyalIcons.Person,
            contentType = ContentType.NewUsername,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            keyboardActions = next,
            modifier = Modifier.anchor(RegistrationField.Alias),
        )

        if (emailMode) {
            SectionHeader(stringResource(Res.string.register_section_access))
            RoyalTextField(
                value = form.email,
                onValueChange = { value -> viewModel.updateForm { it.copy(email = value) } },
                label = stringResource(Res.string.field_email),
                error = errorOf(RegistrationField.Email)?.message(),
                leadingIcon = RoyalIcons.Mail,
                contentType = ContentType.EmailAddress,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                keyboardActions = next,
                modifier = Modifier.anchor(RegistrationField.Email),
            )
            Column(Modifier.anchor(RegistrationField.Password), verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
                RoyalPasswordField(
                    value = form.password,
                    onValueChange = { value -> viewModel.updateForm { it.copy(password = value) } },
                    label = stringResource(Res.string.field_password),
                    error = errorOf(RegistrationField.Password)?.message(),
                    contentType = ContentType.NewPassword,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = next,
                )
                PasswordFeedback(form.password)
            }
            RoyalPasswordField(
                value = form.passwordConfirmation,
                onValueChange = { value -> viewModel.updateForm { it.copy(passwordConfirmation = value) } },
                label = stringResource(Res.string.register_password_confirmation),
                error = errorOf(RegistrationField.PasswordConfirmation)?.message(),
                contentType = ContentType.NewPassword,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = next,
                modifier = Modifier.anchor(RegistrationField.PasswordConfirmation),
            )
        }

        SectionHeader(stringResource(Res.string.register_section_personal))
        RoyalDateField(
            value = form.birthDate,
            onValueChange = { date -> viewModel.updateForm { it.copy(birthDate = date) } },
            label = stringResource(Res.string.register_birth_date),
            maxDate = state.today,
            initialDisplayedDate = state.today.minus(AgeRules.MINIMUM_AGE + 7, DateTimeUnit.YEAR),
            minYear = state.today.year - AgeRules.MAXIMUM_PLAUSIBLE_AGE,
            error = errorOf(RegistrationField.BirthDate)?.message(),
            supportingText = stringResource(Res.string.register_birth_date_help),
            modifier = Modifier.anchor(RegistrationField.BirthDate),
        )
        CountryField(
            countryCode = form.countryCode,
            onCountrySelected = { code -> viewModel.updateForm { it.copy(countryCode = code) } },
            error = errorOf(RegistrationField.Country)?.let { stringResource(Res.string.error_country_required) },
            modifier = Modifier.anchor(RegistrationField.Country),
        )

        SectionHeader(stringResource(Res.string.register_section_terms))
        val terms = stringResource(Res.string.legal_terms_title)
        val privacy = stringResource(Res.string.legal_privacy_title)
        LabeledCheckbox(
            checked = form.acceptsTerms,
            onCheckedChange = { checked -> viewModel.updateForm { it.copy(acceptsTerms = checked) } },
            text = linkedText(
                stringResource(Res.string.register_terms),
                terms to { onOpenLegal(LegalDocument.Terms) },
                privacy to { onOpenLegal(LegalDocument.Privacy) },
            ),
            error = errorOf(RegistrationField.Terms)?.message(),
            modifier = Modifier.anchor(RegistrationField.Terms),
        )
        LabeledCheckbox(
            checked = form.acknowledgesVirtualChips,
            onCheckedChange = { checked -> viewModel.updateForm { it.copy(acknowledgesVirtualChips = checked) } },
            text = AnnotatedString(stringResource(Res.string.register_virtual_chips)),
            error = errorOf(RegistrationField.VirtualChips)?.message(),
            modifier = Modifier.anchor(RegistrationField.VirtualChips),
        )
        LabeledCheckbox(
            checked = form.marketingOptIn,
            onCheckedChange = { checked -> viewModel.updateForm { it.copy(marketingOptIn = checked) } },
            text = AnnotatedString(stringResource(Res.string.register_marketing)),
        )

        state.authError?.let { InfoBanner(message = it.message(), tone = BannerTone.Error) }
        RoyalPrimaryButton(
            text = stringResource(if (emailMode) Res.string.register_submit else Res.string.complete_profile_submit),
            onClick = viewModel::submit,
            // Tras el éxito la raíz de la app cambia sola al casino: el botón sigue ocupado hasta entonces.
            loading = state.isSubmitting || state.completed,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!emailMode) {
            RoyalTextButton(
                text = stringResource(Res.string.complete_profile_sign_out),
                onClick = viewModel::signOut,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Indicador de fortaleza y lista de requisitos, visibles en cuanto se empieza a escribir. */
@Composable
private fun PasswordFeedback(password: String) {
    if (password.isEmpty()) return
    val strength = PasswordPolicy.strength(password)
    val unmet = PasswordPolicy.unmetRequirements(password)
    Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xs)) {
        SegmentedMeter(filledSegments = strength.segments, color = strength.color(), label = strength.label())
        PasswordRequirement.entries.forEach { requirement ->
            ChecklistItem(text = requirement.label(), met = requirement !in unmet)
        }
    }
}
