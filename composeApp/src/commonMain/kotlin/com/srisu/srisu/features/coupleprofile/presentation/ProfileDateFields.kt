package com.srisu.srisu.features.coupleprofile.presentation

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.srisu.srisu.theme.*
import androidx.compose.runtime.*
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable fun ProfileDateFields(state: CoupleProfileState, vm: CoupleProfileViewModel, plan: Boolean) {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val value = state.draft?.values?.get(if(plan) "starts_at" else "anniversary_date").orEmpty()
    val existing = if(plan) runCatching { Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull() else null
    val date = existing?.date ?: runCatching { LocalDate.parse(value) }.getOrNull() ?: now.date
    val time = existing?.time ?: LocalTime(now.hour, now.minute)
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val saveDateTime: (LocalDate, LocalTime) -> Unit = { d,t -> vm.edit(if(plan) "starts_at" else "anniversary_date", if(plan) LocalDateTime(d,t).toInstant(TimeZone.currentSystemDefault()).toString() else d.toString()) }
    ProfileDateChoice(stringResource(Res.string.cp_date_label),
        if (value.isBlank()) stringResource(Res.string.cp_choose_date) else date.toString(), !state.saving, false) { pickingDate = true }
    if (plan) ProfileDateChoice(stringResource(Res.string.cp_time), time.toString(), !state.saving, true) { pickingTime = true }
    if(pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), selectableDates=object: SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = if(plan) utcTimeMillis >= now.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() else utcTimeMillis <= now.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        })
        DatePickerDialog(onDismissRequest={pickingDate=false},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let { saveDateTime(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date,time) };pickingDate=false}){Text(stringResource(Res.string.cp_continue))}},dismissButton={TextButton(onClick={pickingDate=false}){Text(stringResource(Res.string.cp_cancel))}}) { DatePicker(picker) }
    }
    if(pickingTime) {
        val picker=rememberTimePickerState(initialHour=time.hour,initialMinute=time.minute)
        AlertDialog(onDismissRequest={pickingTime=false},confirmButton={TextButton(onClick={saveDateTime(date,LocalTime(picker.hour,picker.minute));pickingTime=false}){Text(stringResource(Res.string.cp_continue))}},dismissButton={TextButton(onClick={pickingTime=false}){Text(stringResource(Res.string.cp_cancel))}},text={TimeInput(picker)})
    }
}

@OptIn(ExperimentalTime::class)
internal fun localPlanTime(value: String): String = runCatching {
    val local = Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault())
    "${local.date} · ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}.getOrDefault(value)

@Composable private fun ProfileDateChoice(label: String, value: String, enabled: Boolean, time: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.field,
        contentPadding = PaddingValues(MaterialTheme.spacing.medium)) {
        Icon(if (time) Icons.Default.Schedule else Icons.Default.CalendarMonth, null)
        Spacer(Modifier.width(MaterialTheme.spacing.compact))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
