package com.royalchance.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.royalchance.core.common.locale.regionDisplayName
import com.royalchance.core.designsystem.component.RoyalTextField
import com.royalchance.core.designsystem.component.SearchableSelectionDialog
import com.royalchance.core.designsystem.component.foldForSearch
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.ui.resources.Res
import com.royalchance.core.ui.resources.country_dialog_title
import com.royalchance.core.ui.resources.country_label
import com.royalchance.domain.profile.Countries
import org.jetbrains.compose.resources.stringResource

/** Idioma de la interfaz. Centralizado para cuando la app tenga más idiomas. */
const val UI_LANGUAGE_TAG = "es"

/** Nombre del país en el idioma de la interfaz (datos CLDR de la plataforma). */
fun countryName(isoCode: String): String = regionDisplayName(isoCode, UI_LANGUAGE_TAG) ?: isoCode

/** Campo de país con diálogo de búsqueda. Los nombres se ordenan alfabéticamente sin tener en cuenta tildes. */
@Composable
fun CountryField(
    countryCode: String?,
    onCountrySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(Res.string.country_label)
    val selectedName = countryCode?.let(::countryName)

    Box(modifier) {
        RoyalTextField(
            value = selectedName ?: "",
            onValueChange = {},
            label = label,
            error = error,
            leadingIcon = RoyalIcons.Globe,
            trailingIcon = { Icon(RoyalIcons.ExpandMore, contentDescription = null) },
            readOnly = true,
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = if (error != null) 20.dp else 0.dp)
                .clickable(role = Role.Button) { showDialog = true }
                .semantics { contentDescription = listOfNotNull(label, selectedName, error).joinToString(". ") },
        )
    }

    if (showDialog) {
        val countries = remember {
            Countries.isoCodes
                .map { it to countryName(it) }
                .sortedBy { (_, name) -> name.foldForSearch() }
        }
        SearchableSelectionDialog(
            title = stringResource(Res.string.country_dialog_title),
            items = countries,
            itemLabel = { (_, name) -> name },
            selected = countries.firstOrNull { it.first == countryCode },
            onSelect = { (code, _) ->
                onCountrySelected(code)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}
