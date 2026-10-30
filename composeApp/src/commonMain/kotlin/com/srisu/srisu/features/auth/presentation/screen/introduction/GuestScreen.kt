@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.srisu.srisu.features.auth.presentation.screen.introduction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.srisu.srisu.components.OutlinedTextFieldCompo
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonVariant
import com.srisu.srisu.features.home.profile.data.InterestCatalogueRepository
import com.srisu.srisu.features.home.profile.presentation.state.InterestCatalogueState
import com.srisu.srisu.features.home.profile.presentation.state.InterestCatalogueStateHolder
import com.srisu.srisu.theme.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import srisu.composeapp.generated.resources.*

/** Public shell. No account ViewModel, socket, private cache or authenticated graph is created. */
@Composable
fun GuestScreen(onAuthenticate: () -> Unit) {
    val repository: InterestCatalogueRepository = koinInject()
    val scope = rememberCoroutineScope()
    val catalogue = remember(repository, scope) { InterestCatalogueStateHolder(scope, repository::load) }
    val state by catalogue.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAccountPrompt by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(tab) { if (tab == 1 && !state.loaded) catalogue.refresh() }
    BackHandler(enabled = tab != 0 && !showAccountPrompt) { tab = 0 }

    MaterialTheme(typography = SriSuPartnerLinkTypography()) {
        Scaffold(bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.Home, null) },
                    label = { Text(stringResource(Res.string.guest_home)) })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.Search, null) },
                    label = { Text(stringResource(Res.string.guest_explore)) })
                NavigationBarItem(false, { showAccountPrompt = true }, icon = { Icon(Icons.Default.Person, null) },
                    label = { Text(stringResource(Res.string.guest_space)) })
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = PartnerLinkTokens.contentMaxWidth).fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.gutter),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.guest_label), style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onAuthenticate) { Text(stringResource(Res.string.intro_login)) }
                    }
                    if (tab == 0) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.spacing.gutter),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)) {
                            Text(stringResource(Res.string.guest_title), style = MaterialTheme.typography.displaySmall,
                                modifier = Modifier.semantics { heading() })
                            Text(stringResource(Res.string.guest_description), style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IntroductionButton(stringResource(Res.string.guest_browse), { tab = 1 })
                            SpaceFeatureCards()
                            SriSuButton(stringResource(Res.string.guest_create_account), { showAccountPrompt = true },
                                Modifier.fillMaxWidth(), variant = SriSuButtonVariant.Outline)
                            Spacer(Modifier.height(MaterialTheme.spacing.small))
                        }
                    } else {
                        GuestInterests(state, query, { query = it }, { catalogue.refresh(force = true) })
                    }
                }
            }
        }
        if (showAccountPrompt) AlertDialog(
            onDismissRequest = { showAccountPrompt = false },
            title = { Text(stringResource(Res.string.guest_gate_title)) },
            text = { Text(stringResource(Res.string.guest_gate_description)) },
            confirmButton = { TextButton(onClick = onAuthenticate) { Text(stringResource(Res.string.guest_create_account)) } },
            dismissButton = { TextButton(onClick = { showAccountPrompt = false }) { Text(stringResource(Res.string.guest_keep_exploring)) } },
        )
    }
}

@Composable
private fun GuestInterests(state: InterestCatalogueState, query: String, onQuery: (String) -> Unit, onRetry: () -> Unit) {
    val more = stringResource(Res.string.guest_other_interests)
    val groups = remember(state.items, query, more) {
        val search = query.trim()
        state.items.filterNotNull().filter { interest ->
            interest.name.orEmpty().contains(search, ignoreCase = true) ||
                interest.category?.name.orEmpty().contains(search, ignoreCase = true) ||
                interest.category?.label.orEmpty().contains(search, ignoreCase = true)
        }.groupBy { it.category?.label?.takeIf(String::isNotBlank) ?: it.category?.name?.takeIf(String::isNotBlank) ?: more }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(MaterialTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        item {
            Text(stringResource(Res.string.guest_interests_title), style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() })
        }
        item { Text(stringResource(Res.string.guest_interests_description), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            OutlinedTextFieldCompo(value = query, placeholder = stringResource(Res.string.guest_search), onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Search,
                leadingContent = { Icon(Icons.Default.Search, null) })
        }
        if (state.loading) item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)) {
                CircularProgressIndicator(Modifier.size(MaterialTheme.spacing.chromeIcon))
                Text(stringResource(Res.string.guest_loading))
            }
        }
        if (state.offline) item { Text(stringResource(Res.string.guest_offline), style = MaterialTheme.typography.bodySmall) }
        if (state.error != null) item {
            Column {
                if (!state.offline) Text(state.error.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry, enabled = !state.loading) { Text(stringResource(Res.string.auth_retry)) }
            }
        }
        if (!state.loading && state.loaded && groups.isEmpty()) item {
            Text(stringResource(if (state.items.isEmpty()) Res.string.guest_empty else Res.string.guest_no_results))
        }
        groups.forEach { (category, interests) ->
            item(key = "category:$category") {
                Text(category, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            items(interests, key = { "interest:${it.id}" }) { interest ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(interest.name.orEmpty(), Modifier.padding(MaterialTheme.spacing.medium), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
