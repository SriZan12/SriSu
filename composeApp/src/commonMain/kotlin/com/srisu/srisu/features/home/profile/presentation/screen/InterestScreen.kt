package com.srisu.srisu.features.home.profile.presentation.screen

import com.srisu.srisu.theme.sriSuFilterChipColors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.srisu.srisu.components.NavigationBackButton
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.home.profile.presentation.state.*
import com.srisu.srisu.theme.pill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun InterestScreen(
    interests: List<InterestResponse.Interest?>? = emptyList(),
    currentInterests: List<User.UserInterest?>? = emptyList(),
    onCancel: () -> Unit = {},
    onInterestSelected: (List<User.UserInterest?>?) -> Unit = {},
    catalogue: InterestCatalogueState = InterestCatalogueState(loading = false, loaded = true),
    onRetry: () -> Unit = {},
) {
    val current = currentInterests.orEmpty()
    val choices = remember(interests, current) { interestChoices(interests.orEmpty(), current) }
    val initialIds = current.filterNotNull().filter { it.removed != true }.mapNotNull { it.interest }.distinct()
    var selected by rememberSaveable(initialIds) { mutableStateOf(initialIds) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(choices, query) { choices.filter {
        it.label.contains(query.trim(), ignoreCase = true) || it.category.contains(query.trim(), ignoreCase = true)
    }.groupBy { it.category } }
    BackHandler(onBack = onCancel)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Interests", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { NavigationBackButton(onCancel) }) },
        bottomBar = {
            Surface {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${choices.count { c -> c.aliases.any { it in selected } }} selected", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SriSuButton("Done", { onInterestSelected(applyInterestSelection(current, choices, selected.toSet())) },
                        enabled = catalogue.loaded || current.isNotEmpty())
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 560.dp).fillMaxSize().testTag("interest-list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item("search") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Pick the things you enjoy. You can change these anytime.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("Search interests") }, label = { Text("Search interests") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } })
                    }
                }
                if (catalogue.loading) item("loading") {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading interests…", style = MaterialTheme.typography.bodySmall)
                }
                if (catalogue.error != null || catalogue.offline) item("error") {
                    Column {
                        Text(if (catalogue.offline) "Showing saved interests. Connect to refresh the list." else "Interests couldn’t load. Your selections are still here.",
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onRetry) { Text("Retry") }
                    }
                }
                if (filtered.isEmpty() && !catalogue.loading && (catalogue.loaded || choices.isNotEmpty())) item("empty") {
                    Text(if (query.isNotBlank()) "No interests match “${query.trim()}”. Try another word." else "No interests are available yet.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                filtered.forEach { (category, options) ->
                    item("category:$category") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(category, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                options.forEach { option ->
                                    val active = option.aliases.any { it in selected }
                                    FilterChip(active, onClick = {
                                        selected = if (active) selected.filterNot { it in option.aliases } else selected + option.id
                                    }, modifier = Modifier.heightIn(min = 48.dp).testTag("interest:${option.id}"), shape = MaterialTheme.shapes.pill,
                                        label = { Text(option.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp)) },
                                        leadingIcon = if (active) ({ Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }) else null, colors = sriSuFilterChipColors())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
