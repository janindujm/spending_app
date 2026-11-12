package com.spendplan.app.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendplan.app.AppContainer
import com.spendplan.app.data.DeletedTransaction
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.data.db.TransactionEntity
import com.spendplan.app.data.db.TransactionType
import com.spendplan.app.domain.Currencies
import com.spendplan.app.domain.Frequency
import com.spendplan.app.domain.MoneyFormat
import com.spendplan.app.ui.LocalAppMessenger
import com.spendplan.app.ui.appViewModel
import com.spendplan.app.ui.components.AmountField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class EditState(
    val loaded: Boolean = false,
    val existing: TransactionEntity? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val repeat: Frequency? = null,
    val amountError: String? = null,
    val categoryError: String? = null,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = existing == null
}

class TransactionEditViewModel(
    private val container: AppContainer,
    private val id: Long,
    initialType: TransactionType,
) : ViewModel() {
    private val _state = MutableStateFlow(EditState(type = initialType))
    val state: StateFlow<EditState> = _state.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = container.categoryRepository.categories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currency = container.settingsRepository.settings.map { it.currency }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Currencies.LKR)

    init {
        viewModelScope.launch {
            val tx = if (id > 0) container.transactionRepository.get(id) else null
            if (tx == null) {
                _state.update { it.copy(loaded = true) }
            } else {
                val rule = container.transactionRepository.getRule(tx.recurringRuleId)
                _state.value = EditState(
                    loaded = true,
                    existing = tx,
                    type = tx.type,
                    amountText = MoneyFormat.editable(tx.amountMinor),
                    categoryId = tx.categoryId,
                    date = LocalDate.ofEpochDay(tx.date),
                    note = tx.note,
                    repeat = rule?.takeIf { it.active }?.frequency,
                )
            }
        }
    }

    fun setType(type: TransactionType) = _state.update {
        val keep = categories.value.firstOrNull { c -> c.id == it.categoryId }?.type == type
        it.copy(type = type, categoryId = if (keep) it.categoryId else null)
    }

    fun setAmount(text: String) = _state.update { it.copy(amountText = text, amountError = null) }
    fun setCategory(id: Long) = _state.update { it.copy(categoryId = id, categoryError = null) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setNote(note: String) = _state.update { it.copy(note = note.take(200)) }
    fun setRepeat(repeat: Frequency?) = _state.update { it.copy(repeat = repeat) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.saving) return
        val amount = MoneyFormat.parse(s.amountText)
        val amountError = when {
            s.amountText.isBlank() -> "Amount is required"
            amount == null -> "Enter a valid amount (max 2 decimals)"
            amount <= 0 -> "Amount must be greater than 0"
            else -> null
        }
        val categoryError = if (s.categoryId == null || categories.value.none { it.id == s.categoryId }) "Choose a category" else null
        if (amountError != null || categoryError != null) {
            _state.update { it.copy(amountError = amountError, categoryError = categoryError) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val base = s.existing ?: TransactionEntity(amountMinor = 0, type = s.type, categoryId = 0, date = 0)
            container.transactionRepository.save(
                base.copy(
                    amountMinor = amount!!,
                    type = s.type,
                    categoryId = s.categoryId!!,
                    date = s.date.toEpochDay(),
                    note = s.note.trim(),
                ),
                s.repeat,
            )
            if (s.repeat != null) container.runRecurringNow()
            container.onDataChanged()
            onDone()
        }
    }

    fun delete(onDeleted: (DeletedTransaction) -> Unit) {
        val tx = _state.value.existing ?: return
        viewModelScope.launch { onDeleted(container.transactionRepository.delete(tx)) }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditScreen(id: Long, initialType: TransactionType, onBack: () -> Unit) {
    val vm = appViewModel(key = "edit-$id") { TransactionEditViewModel(it, id, initialType) }
    val state by vm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.loaded) {
        // Quick-add: focus the amount with the numeric keyboard straight away.
        if (state.loaded && state.isNew) {
            focus.requestFocus()
            keyboard?.show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Add transaction" else "Edit transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { vm.delete { messenger.showUndoDelete(it); onBack() } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income").forEachIndexed { i, (type, label) ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { vm.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }

            AmountField(
                value = state.amountText,
                onValueChange = vm::setAmount,
                label = "Amount",
                currency = currency,
                error = state.amountError,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )

            Column {
                Text("Category", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.filter { it.type == state.type }.forEach { c ->
                        FilterChip(
                            selected = state.categoryId == c.id,
                            onClick = { vm.setCategory(c.id) },
                            label = { Text("${c.emoji} ${c.name}") },
                        )
                    }
                }
                state.categoryError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = state.date.format(dateFormat),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Filled.DateRange, contentDescription = "Pick date") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row2Buttons(
                onToday = { vm.setDate(LocalDate.now()) },
                onYesterday = { vm.setDate(LocalDate.now().minusDays(1)) },
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = vm::setNote,
                label = { Text("Note (optional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Repeat", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    val options = listOf(null to "Never", Frequency.MONTHLY to "Monthly", Frequency.WEEKLY to "Weekly")
                    options.forEachIndexed { i, (freq, label) ->
                        SegmentedButton(
                            selected = state.repeat == freq,
                            onClick = { vm.setRepeat(freq) },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(label) }
                    }
                }
                val hint = when (state.repeat) {
                    Frequency.MONTHLY -> "Created automatically every month on day ${state.date.dayOfMonth}" +
                        if (state.date.dayOfMonth > 28) " (or the last day of shorter months)." else "."
                    Frequency.WEEKLY -> "Created automatically every ${state.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}."
                    null -> null
                }
                hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(onClick = { vm.save(onBack) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Row2Buttons(onToday: () -> Unit, onYesterday: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedButton(onClick = onToday, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Text("Today")
        }
        androidx.compose.material3.OutlinedButton(onClick = onYesterday, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Text("Yesterday")
        }
    }
}
