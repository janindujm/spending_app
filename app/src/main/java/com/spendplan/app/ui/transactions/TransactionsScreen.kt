package com.spendplan.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendplan.app.AppContainer
import com.spendplan.app.data.DeletedTransaction
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.data.db.TransactionEntity
import com.spendplan.app.data.db.TransactionType
import com.spendplan.app.domain.BudgetPeriod
import com.spendplan.app.domain.CurrencyOption
import com.spendplan.app.domain.MoneyFormat
import com.spendplan.app.ui.LocalAppMessenger
import com.spendplan.app.ui.MonthViewModel
import com.spendplan.app.ui.appViewModel
import com.spendplan.app.ui.components.CategoryBadge
import com.spendplan.app.ui.components.EmptyState
import com.spendplan.app.ui.components.MonthSelector
import com.spendplan.app.ui.theme.StatusColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TransactionFilters(
    val type: TransactionType? = null,
    val categoryId: Long? = null,
    val query: String = "",
)

data class TxRow(val transaction: TransactionEntity, val category: CategoryEntity?)

data class DayGroup(val date: LocalDate, val rows: List<TxRow>) {
    val net: Long get() = rows.sumOf { if (it.transaction.type == TransactionType.INCOME) it.transaction.amountMinor else -it.transaction.amountMinor }
}

data class TransactionsUiState(
    val month: YearMonth,
    val startDay: Int,
    val currency: CurrencyOption,
    val filters: TransactionFilters,
    val categories: List<CategoryEntity>,
    val groups: List<DayGroup>,
    val totalInMonth: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(private val container: AppContainer) : MonthViewModel(container) {
    private val filters = MutableStateFlow(TransactionFilters())

    val state: StateFlow<TransactionsUiState?> = monthContextFlow.flatMapLatest { ctx ->
        val range = BudgetPeriod.range(ctx.month, ctx.settings.monthStartDay)
        combine(
            container.transactionRepository.observeRange(range),
            container.categoryRepository.categories,
            filters,
        ) { txs, cats, f ->
            val byId = cats.associateBy { it.id }
            val q = f.query.trim()
            val filtered = txs.filter { t ->
                (f.type == null || t.type == f.type) &&
                    (f.categoryId == null || t.categoryId == f.categoryId) &&
                    (q.isEmpty() || t.note.contains(q, ignoreCase = true) ||
                        (byId[t.categoryId]?.name?.contains(q, ignoreCase = true) == true))
            }
            TransactionsUiState(
                month = ctx.month,
                startDay = ctx.settings.monthStartDay,
                currency = ctx.settings.currency,
                filters = f,
                categories = cats,
                groups = filtered.groupBy { it.date }
                    .toSortedMap(compareByDescending { it })
                    .map { (day, list) -> DayGroup(LocalDate.ofEpochDay(day), list.map { TxRow(it, byId[it.categoryId]) }) },
                totalInMonth = txs.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setType(type: TransactionType?) = filters.value.let { f ->
        // A category filter of the other type would hide everything; clear it.
        val keepCategory = type == null || state.value?.categories?.firstOrNull { it.id == f.categoryId }?.type == type
        filters.value = f.copy(type = type, categoryId = if (keepCategory) f.categoryId else null)
    }

    fun setCategory(id: Long?) { filters.value = filters.value.copy(categoryId = id) }
    fun setQuery(q: String) { filters.value = filters.value.copy(query = q) }

    fun delete(t: TransactionEntity, onDeleted: (DeletedTransaction) -> Unit) {
        viewModelScope.launch { onDeleted(container.transactionRepository.delete(t)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(onEdit: (Long) -> Unit) {
    val vm = appViewModel { TransactionsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current

    Scaffold(topBar = { TopAppBar(title = { Text("Transactions") }) }) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item { MonthSelector(s.month, s.startDay, vm::previousMonth, vm::nextMonth, Modifier.padding(horizontal = 8.dp)) }
            item { Filters(s, vm) }

            if (s.groups.isEmpty()) {
                item {
                    if (s.totalInMonth == 0) {
                        EmptyState("🧾", "No transactions", "Nothing recorded for ${BudgetPeriod.label(s.month)}. Tap + to add one.")
                    } else {
                        EmptyState("🔍", "No matches", "No transactions match the current filters.")
                    }
                }
            }

            s.groups.forEach { group ->
                item(key = "h-${group.date}") { DayHeader(group, s.currency) }
                items(group.rows, key = { it.transaction.id }) { row ->
                    SwipeToDeleteRow(
                        onDelete = { vm.delete(row.transaction) { messenger.showUndoDelete(it) } },
                    ) {
                        TransactionRow(row, s.currency, onClick = { onEdit(row.transaction.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(s: TransactionsUiState, vm: TransactionsViewModel) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = s.filters.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search notes or categories") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (s.filters.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = s.filters.type == null, onClick = { vm.setType(null) }, label = { Text("All") })
            FilterChip(
                selected = s.filters.type == TransactionType.EXPENSE,
                onClick = { vm.setType(TransactionType.EXPENSE) },
                label = { Text("Expenses") },
            )
            FilterChip(
                selected = s.filters.type == TransactionType.INCOME,
                onClick = { vm.setType(TransactionType.INCOME) },
                label = { Text("Income") },
            )
            CategoryFilterChip(s, vm)
        }
    }
}

@Composable
private fun CategoryFilterChip(s: TransactionsUiState, vm: TransactionsViewModel) {
    var open by remember { mutableStateOf(false) }
    val selected = s.categories.firstOrNull { it.id == s.filters.categoryId }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected?.let { "${it.emoji} ${it.name}" } ?: "Category") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All categories") }, onClick = { vm.setCategory(null); open = false })
            s.categories
                .filter { s.filters.type == null || it.type == s.filters.type }
                .forEach { c ->
                    val suffix = if (s.filters.type == null) " (${if (c.type == TransactionType.INCOME) "income" else "expense"})" else ""
                    DropdownMenuItem(
                        text = { Text("${c.emoji} ${c.name}$suffix") },
                        onClick = { vm.setCategory(c.id); open = false },
                    )
                }
        }
    }
}

private val headerFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

@Composable
private fun DayHeader(group: DayGroup, currency: CurrencyOption) {
    val today = LocalDate.now()
    val label = when (group.date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> group.date.format(headerFormat)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(
            (if (group.net > 0) "+" else "") + MoneyFormat.format(group.net, currency),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
fun TransactionRow(row: TxRow, currency: CurrencyOption, onClick: () -> Unit) {
    val t = row.transaction
    val income = t.type == TransactionType.INCOME
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { CategoryBadge(row.category) },
        headlineContent = { Text(row.category?.name ?: "Unknown", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (t.note.isNotBlank()) {
            { Text(t.note, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.recurringRuleId != null) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Recurring",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Text(
                    (if (income) "+" else "-") + MoneyFormat.format(t.amountMinor, currency),
                    color = if (income) StatusColors.good else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}
