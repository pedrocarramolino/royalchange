package com.royalchance.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.resources.Res
import com.royalchance.core.designsystem.resources.ds_accept
import com.royalchance.core.designsystem.resources.ds_cancel
import com.royalchance.core.designsystem.resources.ds_close
import com.royalchance.core.designsystem.resources.ds_no_results
import com.royalchance.core.designsystem.resources.ds_search
import com.royalchance.core.designsystem.resources.ds_select_date
import com.royalchance.core.designsystem.theme.RoyalSizes
import com.royalchance.core.designsystem.theme.RoyalSpacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Campo de fecha: muestra la fecha elegida (dd/mm/aaaa) y abre el calendario de Material 3 al
 * pulsarlo. El calendario arranca en modo de escritura, lo más rápido para fechas lejanas
 * como la de nacimiento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoyalDateField(
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    maxDate: LocalDate,
    modifier: Modifier = Modifier,
    error: String? = null,
    supportingText: String? = null,
    initialDisplayedDate: LocalDate = maxDate,
    minYear: Int = maxDate.year - 120,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val openDescription = "$label. ${stringResource(Res.string.ds_select_date)}"

    Box(modifier) {
        RoyalTextField(
            value = value?.format() ?: "",
            onValueChange = {},
            label = label,
            error = error,
            supportingText = supportingText,
            leadingIcon = RoyalIcons.Calendar,
            contentType = ContentType.BirthDateFull,
            readOnly = true,
        )
        // Capa pulsable sobre el campo (solo lectura): abre el calendario con toque, ratón o teclado.
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = if (error != null || supportingText != null) 20.dp else 0.dp)
                .clickable(role = Role.Button) { showPicker = true }
                .semantics { contentDescription = listOfNotNull(openDescription, value?.format(), error).joinToString(". ") },
        )
    }

    if (showPicker) {
        val maxMillis = maxDate.toUtcMillis()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.toUtcMillis(),
            initialDisplayedMonthMillis = (value ?: initialDisplayedDate).toUtcMillis(),
            yearRange = minYear..maxDate.year,
            initialDisplayMode = DisplayMode.Input,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= maxMillis
                override fun isSelectableYear(year: Int) = year <= maxDate.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                RoyalTextButton(
                    text = stringResource(Res.string.ds_accept),
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { onValueChange(it.toUtcLocalDate()) }
                        showPicker = false
                    },
                )
            },
            dismissButton = {
                RoyalTextButton(text = stringResource(Res.string.ds_cancel), onClick = { showPicker = false })
            },
        ) {
            DatePicker(state = state, showModeToggle = true)
        }
    }
}

/**
 * Diálogo de selección con búsqueda, para listas largas (países…). La búsqueda ignora mayúsculas y
 * tildes: "espana" encuentra "España".
 */
@Composable
fun <T> SearchableSelectionDialog(
    title: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    selected: T? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val filtered = remember(items, query) {
        val needle = query.foldForSearch()
        if (needle.isEmpty()) items else items.filter { itemLabel(it).foldForSearch().contains(needle) }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected?.let { items.indexOf(it) }?.coerceAtLeast(0) ?: 0)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = 640.dp),
        ) {
            Column(Modifier.padding(vertical = RoyalSpacing.l)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = RoyalSpacing.xl, vertical = RoyalSpacing.s),
                )
                RoyalTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(Res.string.ds_search),
                    leadingIcon = RoyalIcons.Search,
                    modifier = Modifier
                        .padding(horizontal = RoyalSpacing.l)
                        .focusRequester(focusRequester),
                )
                HorizontalDivider(Modifier.padding(top = RoyalSpacing.s))
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.ds_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(RoyalSpacing.xl),
                    )
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                    items(filtered) { item ->
                        SelectionRow(
                            label = itemLabel(item),
                            isSelected = item == selected,
                            onClick = { onSelect(item) },
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = RoyalSpacing.s), contentAlignment = Alignment.CenterEnd) {
                    RoyalTextButton(text = stringResource(Res.string.ds_close), onClick = onDismiss)
                }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun SelectionRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RoyalSizes.minTouchTarget)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = RoyalSpacing.xl),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (isSelected) {
            Icon(
                RoyalIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

private fun LocalDate.format(): String =
    "${day.toString().padStart(2, '0')}/${month.number.toString().padStart(2, '0')}/$year"

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toUtcLocalDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

private val diacritics = mapOf(
    'á' to 'a', 'à' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a', 'å' to 'a',
    'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
    'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
    'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
    'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
    'ñ' to 'n', 'ç' to 'c', 'ý' to 'y', 'ÿ' to 'y',
)

/** Minúsculas y sin tildes, para comparar textos al buscar. */
fun String.foldForSearch(): String = trim().lowercase().map { diacritics[it] ?: it }.joinToString("")
