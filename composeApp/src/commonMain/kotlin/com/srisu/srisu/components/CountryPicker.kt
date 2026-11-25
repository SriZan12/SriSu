package com.srisu.srisu.components

import com.srisu.srisu.theme.selectedSurface

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.srisu.srisu.theme.sheetScrim
import com.srisu.srisu.theme.spacing
import com.srisu.srisu.utils.Country
import com.srisu.srisu.utils.CountryModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

/** A small bundled image, cached by Compose resources on both platforms. */
@Composable
fun CountryFlag(code: String?, modifier: Modifier = Modifier) {
    val resource = remember(code) { Res.allDrawableResources["country_${code?.lowercase()}"] }
    Box(modifier.size(32.dp, 24.dp), contentAlignment = Alignment.Center) {
        if (resource != null) Image(painterResource(resource), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun rememberCountryLabels(): Map<String, String> {
    val entries = stringArrayResource(Res.array.country_labels)
    return remember(entries) { entries.filter { '|' in it }.associate { it.substringBefore('|') to it.substringAfter('|') } }
}

@Composable
fun countryDisplayName(country: CountryModel): String =
    rememberCountryLabels()[country.code] ?: country.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectionBottomSheet(
    modifier: Modifier = Modifier,
    countries: List<CountryModel>,
    show: Boolean,
    onCountrySelected: (CountryModel) -> Unit,
    selectedCode: String? = null,
    callingCodes: Boolean = true,
    onClose: () -> Unit,
) {
    if (!show) return
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun finish() { focus.clearFocus(); keyboard?.hide(); onClose() }
    ModalBottomSheet(
        onDismissRequest = ::finish,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 560.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.sheetScrim,
    ) {
        CountryPickerContent(countries, selectedCode, callingCodes,
            onCountrySelected = {
                focus.clearFocus(); keyboard?.hide()
                // Reselecting a location must not clear the profile's saved city.
                if (!callingCodes && it.code != null && it.code.equals(selectedCode, true)) finish()
                else onCountrySelected(it)
            },
            onClose = ::finish, modifier = modifier.fillMaxWidth().fillMaxHeight(0.86f))
    }
}

/** Shared content is also rendered directly by Android/iOS layout tests. */
@Composable
internal fun CountryPickerContent(
    countries: List<CountryModel>,
    selectedCode: String?,
    callingCodes: Boolean,
    onCountrySelected: (CountryModel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Localized labels also participate in search. The catalogue itself never
    // depends on the network or locale-specific server names.
    val labels = rememberCountryLabels()
    val localizedNames = remember(countries, labels) { countries.map { labels[it.code] ?: it.displayName } }
    val filtered = remember(countries, localizedNames, query, callingCodes) {
        val matches = Country.search(countries, query, callingCodes)
        if (query.isBlank()) matches else {
            val queryKey = Country.searchKey(query)
            val translated = if (queryKey.isBlank()) emptySet() else countries.zip(localizedNames)
                .filter { (_, label) -> Country.searchKey(label).contains(queryKey) }.map { it.first.code }.toSet()
            Country.search(countries, "", callingCodes).filter { it in matches || it.code in translated }
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(query) { list.scrollToItem(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = MaterialTheme.spacing.large, end = MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (callingCodes) Res.string.country_picker_phone_title else Res.string.country_picker_title),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(Res.string.country_picker_close), Modifier.size(20.dp)) }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small).testTag("country-search"),
                placeholder = { Text(stringResource(if (callingCodes) Res.string.country_picker_phone_search else Res.string.country_picker_search), style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(Res.string.country_picker_clear), Modifier.size(18.dp)) } }) else null,
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
            )
            if (filtered.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(MaterialTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    Text(stringResource(if (countries.isEmpty()) Res.string.country_picker_unavailable else Res.string.country_picker_empty), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (countries.isEmpty()) Res.string.country_picker_unavailable_help else Res.string.country_picker_empty_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("country-list"), state = list, contentPadding = PaddingValues(bottom = MaterialTheme.spacing.large)) {
                items(filtered, key = { it.code ?: it.name.orEmpty() }, contentType = { "country" }) { country ->
                    val selected = country.code != null && country.code.equals(selectedCode, ignoreCase = true)
                    Row(Modifier.fillMaxWidth().testTag("country-${country.code}")
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onCountrySelected(country) })
                        .background(if (selected) MaterialTheme.colorScheme.selectedSurface else MaterialTheme.colorScheme.surface)
                        .heightIn(min = 56.dp).padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.compact),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
                        CountryFlag(country.code)
                        Text(labels[country.code] ?: country.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (callingCodes) Text(country.prefix.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (selected) Icon(Icons.Outlined.Check, stringResource(Res.string.country_picker_selected), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
