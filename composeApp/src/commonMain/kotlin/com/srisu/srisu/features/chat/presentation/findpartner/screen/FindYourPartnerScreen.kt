package com.srisu.srisu.features.chat.presentation.findpartner.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cash.paging.compose.collectAsLazyPagingItems
import com.srisu.srisu.components.CountrySelectionBottomSheet
import com.srisu.srisu.components.OutlinedTextFieldCompo
import com.srisu.srisu.components.SriSuButton
import com.srisu.srisu.components.SriSuButtonSize
import com.srisu.srisu.components.SriSuButtonVariant
import com.srisu.srisu.features.chat.presentation.findpartner.state.FindPartnerState
import com.srisu.srisu.features.chat.presentation.findpartner.state.PartnerSearchStatus
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.theme.PartnerLinkTokens
import com.srisu.srisu.theme.SriSuPartnerLinkTypography
import com.srisu.srisu.theme.spacing

@Composable
fun FindYourPartnerScreen(
    findPartnerViewModel: FindPartnerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToInviteSent: () -> Unit,
    onContinue: () -> Unit,
    onNavigateToProfile: (String?) -> Unit,
) {
    val state by findPartnerViewModel.findPartnerUIState.collectAsStateWithLifecycle()
    val invitations = findPartnerViewModel.loveRequests.collectAsLazyPagingItems()
    var showCountries by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val search = {
        focusManager.clearFocus()
        findPartnerViewModel.sendFindYourPartnerRequest()
    }

    LaunchedEffect(key1 = findPartnerViewModel) { findPartnerViewModel.onScreenEntered() }

    LaunchedEffect(key1 = state.navigateToInviteSent) {
        if (state.navigateToInviteSent) {
            onNavigateToInviteSent()
            findPartnerViewModel.onInviteSentNavigated()
        }
    }

    MaterialTheme(typography = SriSuPartnerLinkTypography()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { PartnerLinkTopBar(onNavigateBack) },
        ) { padding ->

            Box(
                Modifier.fillMaxSize().padding(paddingValues = padding)
                    .consumeWindowInsets(paddingValues = padding).imePadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    Modifier.widthIn(max = PartnerLinkTokens.contentMaxWidth).fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = MaterialTheme.spacing.gutter,
                        vertical = MaterialTheme.spacing.medium
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    item(key = "heading") {
                        PartnerLinkHeading(
                            title = "Find your partner",
                            subtitle = "Search their phone number. Works if they already have SriSu."
                        )
                    }

                    item(key = "phone") {
                        PartnerPhoneInput(
                            state,
                            onPhoneChanged = findPartnerViewModel::updatePhoneNumber,
                            onSelectCountry = { showCountries = true },
                            onSearch = search,
                            onCancelSearch = findPartnerViewModel::cancelSearch
                        )
                    }

                    if (state.acceptedPartnerName != null) {
                        item(key = "connected") {
                            PartnerMessage(
                                "You’re connected! Your invitation has been accepted.",
                                action = "Continue",
                                onAction = onContinue
                            )
                        }
                    } else {

                        if (state.sentInvitation != null) {
                            item(key = "pending_invitation") {
                                PartnerMessage(
                                    "Your invitation to ${state.sentInvitation!!.name} is pending.",
                                    action = "View invitation", onAction = onNavigateToInviteSent
                                )
                            }
                        }

                        item(key = "search_status") {
                            PartnerSearchResult(
                                state,
                                onConnect = findPartnerViewModel::sendCoupleConnectionRequest,
                                onCancel = findPartnerViewModel::cancelSearch,
                                onRetry = search,
                                onCheckInvitation = findPartnerViewModel::sendHaveCoupleConnectionRequested
                            )
                        }

                        if (state.searchStatus != PartnerSearchStatus.Found) {
                            partnerInvitationItems(
                                invitations,
                                state,
                                onRespond = { request, status ->
                                    findPartnerViewModel.updateLoveRequest(
                                        loveRequestId = request.id,
                                        request.senderNumber,
                                        request.receiverNumber,
                                        connectionStatus = status
                                    )
                                },
                                onProfile = {
                                    onNavigateToProfile(
                                        findPartnerViewModel.getUserProfile(it)
                                    )
                                })
                        }
                    }

                    item(key = "search_help") {
                        Text(
                            "Can’t find them? Ask them to join SriSu with their phone number.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        CountrySelectionBottomSheet(
            countries = state.countryList,
            show = showCountries,
            onCountrySelected = { country ->
                country.code?.let { code ->
                    country.prefix?.let { prefix ->
                        findPartnerViewModel.updateCountry(
                            code,
                            prefix
                        )
                    }
                }
                showCountries = false
            },
            onClose = { showCountries = false },
        )
    }
}

@Composable
private fun PartnerPhoneInput(
    state: FindPartnerState,
    onPhoneChanged: (String) -> Unit,
    onSelectCountry: () -> Unit,
    onSearch: () -> Unit,
    onCancelSearch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        OutlinedTextFieldCompo(
            modifier = Modifier.fillMaxWidth(),
            value = state.phoneNumber,
            placeholder = "Phone number",
            onValueChange = onPhoneChanged,
            isError = state.validationErrorMsg.isNotEmpty(),
            enabled = !state.isSendingInvitation,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Search,
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            leadingContent = {
                TextButton(onClick = onSelectCountry, enabled = !state.isSendingInvitation) {
                    Text(state.countryPrefix, style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select country code")
                }
            },
            trailingIconDescription = if (state.searchStatus == PartnerSearchStatus.Loading) "Cancel search" else "Search by phone number",
            trailingIcon = if (state.searchStatus == PartnerSearchStatus.Loading) Icons.Default.Close else Icons.Default.Search,
            onClickTrailingIcon = if (state.searchStatus == PartnerSearchStatus.Loading) onCancelSearch else onSearch,
        )
        if (state.validationErrorMsg.isNotEmpty()) PartnerMessage(
            state.validationErrorMsg,
            isError = true
        )
    }
}

@Composable
private fun PartnerSearchResult(
    state: FindPartnerState,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onCheckInvitation: () -> Unit,
) {
    when (state.searchStatus) {
        PartnerSearchStatus.Idle -> Unit
        PartnerSearchStatus.Loading -> PartnerProgress("Searching for your partner…")
        PartnerSearchStatus.NotFound -> PartnerMessage(
            "No partner found with this number. Check the number and country code, or ask them to create an account.",
            action = "Try again",
            onAction = onRetry
        )

        PartnerSearchStatus.Error -> PartnerMessage(
            state.searchError ?: "Couldn’t search for your partner.",
            isError = true,
            action = "Retry search",
            onAction = onRetry
        )

        PartnerSearchStatus.Found -> {
            val partner = state.partnerResponse ?: return
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Is this your partner?",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    SriSuButton(
                        "Cancel search", onCancel, enabled = !state.isSendingInvitation,
                        variant = SriSuButtonVariant.Ghost, size = SriSuButtonSize.Small
                    )
                }
                Card(
                    Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    BoxWithConstraints(
                        Modifier.padding(
                            horizontal = MaterialTheme.spacing.medium,
                            vertical = MaterialTheme.spacing.compact
                        )
                    ) {
                        val stackAction =
                            maxWidth < PartnerLinkTokens.contentMaxWidth / 2 || LocalDensity.current.fontScale > 1.3f
                        val enabled = !state.isSendingInvitation && state.sentInvitation == null &&
                                state.invitationStatusChecked && !state.isCheckingInvitation && state.updatingRequestIds.isEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact)
                            ) {
                                PartnerIdentity(
                                    partner.fullName?.takeIf(String::isNotBlank) ?: partner.username
                                    ?: "Your partner",
                                    detail = partner.phoneNumber.orEmpty(),
                                    photoUrl = partner.profilePhoto,
                                    Modifier.weight(1f)
                                )
                                if (!stackAction) SriSuButton(
                                    label = if (state.isSendingInvitation) "Sending…" else "Connect",
                                    onClick = onConnect,
                                    enabled = enabled,
                                    size = SriSuButtonSize.Small,
                                    textStyle = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (stackAction) SriSuButton(
                                label = if (state.isSendingInvitation) "Sending…" else "Connect",
                                onClick = onConnect,
                                enabled = enabled,
                                size = SriSuButtonSize.Small
                            )
                        }
                    }
                }
                if (state.isSendingInvitation) PartnerProgress("Sending invitation…")
                if (state.isCheckingInvitation) PartnerProgress("Checking existing invitations…")
                state.invitationStatusError?.let {
                    PartnerMessage(
                        it,
                        isError = true,
                        action = "Retry invitation check",
                        onAction = onCheckInvitation
                    )
                }
                state.invitationError?.let {
                    PartnerMessage(
                        it,
                        isError = true,
                        action = "Retry invitation",
                        onAction = onConnect
                    )
                }
            }
        }
    }
}
