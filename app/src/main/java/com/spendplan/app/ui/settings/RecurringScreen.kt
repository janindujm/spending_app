package com.spendplan.app.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendplan.app.AppContainer
import com.spendplan.app.data.AppSettings
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.data.db.RecurringRuleEntity
import com.spendplan.app.data.db.TransactionType
import com.spendplan.app.data.toSpec
import com.spendplan.app.domain.Frequency
import com.spendplan.app.domain.MoneyFormat
import com.spendplan.app.domain.RecurrenceScheduler
import com.spendplan.app.ui.appViewModel
import com.spendplan.app.ui.components.CategoryBadge
import com.spendplan.app.ui.components.EmptyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class RecurringUiState(
    val rules: List<RecurringRuleEntity>,
    val categories: Map<Long, CategoryEntity>,
    val settings: AppSettings,
)

class RecurringViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<RecurringUiState?> = combine(
        container.recurringRepository.rules,
        container.categoryRepository.categories,
        container.settingsRepository.settings,
    ) { rules, cats, s -> RecurringUiState(rules, cats.associateBy { it.id }, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setActive(rule: RecurringRuleEntity, active: Boolean) = viewModelScope.launch {
        container.recurringRepository.setActive(rule, active)
        if (active) container.runRecurringNow()
    }

    fun delete(rule: RecurringRuleEntity) = viewModelScope.launch { container.recurringRepository.delete(rule) }
}

private val nextFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(onBack: () -> Unit) {
    val vm = appViewModel { RecurringViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<RecurringRuleEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring transactions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (s.rules.isEmpty()) {
                item {
                    EmptyState(
                        "🔁",
                        "No recurring transactions",
                        "When adding a transaction, set Repeat to Monthly or Weekly. SpendPlan then creates it automatically — even if the app wasn't opened for a while.",
                    )
                }
            }
            items(s.rules, key = { it.id }) { rule ->
                val category = s.categories[rule.categoryId]
                val schedule = when (rule.frequency) {
                    Frequency.MONTHLY -> "Monthly on day ${rule.dayOfMonth}"
                    Frequency.WEEKLY -> "Every " + DayOfWeek.of(rule.dayOfWeek.coerceIn(1, 7)).getDisplayName(TextStyle.FULL, Locale.getDefault())
                }
                val spec = rule.toSpec()
                val next = RecurrenceScheduler.nextOccurrence(spec, maxOf(spec.lastGenerated ?: spec.startDate.minusDays(1), LocalDate.now().minusDays(1)))
                val sign = if (rule.type == TransactionType.INCOME) "+" else "-"
                ListItem(
                    leadingContent = { CategoryBadge(category) },
                    headlineContent = {
                        Text("$sign${MoneyFormat.format(rule.amountMinor, s.settings.currency)} · ${rule.note.ifBlank { category?.name ?: "" }}")
                    },
                    supportingContent = {
                        Text(if (rule.active) "$schedule · next ${next.format(nextFormat)}" else "$schedule · paused")
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = rule.active, onCheckedChange = { vm.setActive(rule, it) })
                            IconButton(onClick = { confirmDelete = rule }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                        }
                    },
                )
            }
        }
    }

    confirmDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Stop this recurring transaction?") },
            text = { Text("Future occurrences won't be created. Transactions already created are kept.") },
            confirmButton = { TextButton(onClick = { vm.delete(rule); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
