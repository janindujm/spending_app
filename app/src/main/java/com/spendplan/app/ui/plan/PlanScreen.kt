package com.spendplan.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.data.db.PlannedItemEntity
import com.spendplan.app.domain.AutoFillSource
import com.spendplan.app.domain.BudgetCalculator
import com.spendplan.app.domain.BudgetPeriod
import com.spendplan.app.domain.CurrencyOption
import com.spendplan.app.domain.MoneyFormat
import com.spendplan.app.ui.appViewModel
import com.spendplan.app.ui.components.AmountField
import com.spendplan.app.ui.components.CategoryBadge
import com.spendplan.app.ui.components.MonthSelector
import com.spendplan.app.ui.components.SectionCard
import com.spendplan.app.ui.theme.StatusColors
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(onOpenSummary: (YearMonth) -> Unit) {
    val vm = appViewModel { PlanViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAutoFill by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<PlannedItemEntity?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan") },
                actions = {
                    state?.let { s ->
                        Text(
                            if (s.saving) "Saving…" else "Saved",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MonthSelector(s.month, s.startDay, vm::previousMonth, vm::nextMonth) }

            item { IncomeCard(s, vm) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { showAutoFill = true }, modifier = Modifier.weight(1f)) { Text("Auto-fill") }
                    OutlinedButton(onClick = { onOpenSummary(s.month) }, modifier = Modifier.weight(1f)) { Text("Month summary") }
                }
            }

            item {
                Text("Category budgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (s.isFuture) {
                    Text(
                        "Planning ahead — spending bars will fill in once this month starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(s.expenseCategories, key = { it.id }) { category ->
                CategoryBudgetRow(
                    category = category,
                    text = s.fields.budgetTexts[category.id] ?: "",
                    spent = s.spent[category.id] ?: 0L,
                    plannedItemsTotal = s.plannedItemsByCategory[category.id] ?: 0L,
                    currency = s.currency,
                    onChange = { vm.setBudget(category.id, it) },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Planned payments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Fixed bills like a lease or rent. Tick to mark as paid — this records the expense.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        editingItem = PlannedItemEntity(
                            month = BudgetPeriod.key(s.month),
                            title = "",
                            amountMinor = 0,
                            categoryId = s.expenseCategories.firstOrNull()?.id ?: 0,
                            dayOfMonth = 1,
                        )
                    }) { Icon(Icons.Filled.Add, contentDescription = "Add planned payment") }
                }
            }

            if (s.plannedItems.isEmpty()) {
                item {
                    Text(
                        "No planned payments. Tap + to add one, e.g. “Lease payment on the 5th”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(s.plannedItems, key = { "item-${it.id}" }) { item ->
                PlannedItemRow(
                    item = item,
                    category = s.categoriesById[item.categoryId],
                    currency = s.currency,
                    onPaidChange = { vm.setPaid(item, it) },
                    onEdit = { editingItem = item },
                    onDelete = { vm.deletePlannedItem(item) },
                )
            }
        }
    }

    if (showAutoFill) {
        AutoFillDialog(
            onDismiss = { showAutoFill = false },
            onApply = { source, copy ->
                showAutoFill = false
                vm.autoFill(source, copy)
            },
        )
    }

    editingItem?.let { item ->
        val s = state
        if (s != null) {
            PlannedItemDialog(
                initial = item,
                categories = s.expenseCategories,
                currency = s.currency,
                onDismiss = { editingItem = null },
                onSave = {
                    vm.savePlannedItem(it)
                    editingItem = null
                },
            )
        }
    }
}

@Composable
private fun IncomeCard(s: PlanUiState, vm: PlanViewModel) {
    SectionCard {
        AmountField(
            value = s.fields.incomeText,
            onValueChange = vm::setIncome,
            label = "Expected income",
            currency = s.currency,
            modifier = Modifier.fillMaxWidth(),
        )
        if (s.actualIncome > 0) {
            Text(
                "Received so far: ${MoneyFormat.format(s.actualIncome, s.currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("Total planned", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(MoneyFormat.format(s.totalPlanned, s.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Unallocated", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    MoneyFormat.format(s.unallocated, s.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StatusColors.forAmount(s.unallocated),
                )
            }
        }
        if (s.unallocated < 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusColors.bad)
                Spacer(Modifier.width(8.dp))
                Text(
                    "You've planned ${MoneyFormat.format(-s.unallocated, s.currency)} more than your expected income.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusColors.bad,
                )
            }
        }
    }
}

@Composable
private fun CategoryBudgetRow(
    category: CategoryEntity,
    text: String,
    spent: Long,
    plannedItemsTotal: Long,
    currency: CurrencyOption,
    onChange: (String) -> Unit,
) {
    val budget = MoneyFormat.parse(text) ?: 0L
    val status = BudgetCalculator.status(spent, budget)
    val color = StatusColors.forStatus(status)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category, 36.dp)
            Text(
                category.name,
                modifier = Modifier.padding(horizontal = 12.dp).weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { new -> onChange(new.filter { it.isDigit() || it == '.' || it == ',' }) },
                singleLine = true,
                placeholder = { Text("0") },
                isError = text.isNotBlank() && MoneyFormat.parse(text) == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.width(150.dp),
                prefix = { Text(currency.symbol + " ") },
            )
        }
        if (budget > 0 || spent > 0) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { if (budget > 0) BudgetCalculator.ratio(spent, budget).coerceIn(0f, 1f) else 1f },
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Text(
                "Spent ${MoneyFormat.format(spent, currency)}" +
                    if (budget > 0) " of ${MoneyFormat.format(budget, currency)} (${(spent * 100 / budget)}%)" else " (no budget)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (plannedItemsTotal > budget) {
            Text(
                "Planned payments here total ${MoneyFormat.format(plannedItemsTotal, currency)} — more than this budget.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusColors.warning,
            )
        }
    }
}

@Composable
private fun PlannedItemRow(
    item: PlannedItemEntity,
    category: CategoryEntity?,
    currency: CurrencyOption,
    onPaidChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.paidTransactionId != null, onCheckedChange = onPaidChange)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${MoneyFormat.format(item.amountMinor, currency)} · on the ${ordinal(item.dayOfMonth)} · ${category?.emoji ?: ""} ${category?.name ?: ""}" +
                    if (item.paidTransactionId != null) " · Paid" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.paidTransactionId != null) StatusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${item.title}") }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${item.title}") }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete planned payment?") },
            text = {
                Text(
                    if (item.paidTransactionId != null) "“${item.title}” is marked as paid. Deleting it also removes the expense it created."
                    else "“${item.title}” will be removed from this month's plan.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun ordinal(day: Int): String {
    val suffix = if (day in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

@Composable
private fun AutoFillDialog(onDismiss: () -> Unit, onApply: (AutoFillSource, Boolean) -> Unit) {
    var source by rememberSaveable { mutableStateOf(AutoFillSource.LAST_MONTH) }
    var copyItems by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-fill plan") },
        text = {
            Column {
                Text("Replace this month's income and budgets with:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                listOf(
                    AutoFillSource.LAST_MONTH to "Last month's actuals",
                    AutoFillSource.THREE_MONTH_AVERAGE to "Average of the last 3 months",
                ).forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = source == value, onClick = { source = value }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = source == value, onClick = null)
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copyItems, onCheckedChange = { copyItems = it })
                    Text("Also copy planned payments from last month", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "You can edit every value afterwards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onApply(source, copyItems) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannedItemDialog(
    initial: PlannedItemEntity,
    categories: List<CategoryEntity>,
    currency: CurrencyOption,
    onDismiss: () -> Unit,
    onSave: (PlannedItemEntity) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var amount by rememberSaveable { mutableStateOf(MoneyFormat.editable(initial.amountMinor)) }
    var day by rememberSaveable { mutableStateOf(initial.dayOfMonth.toString()) }
    var categoryId by rememberSaveable { mutableStateOf(initial.categoryId) }
    var showErrors by remember { mutableStateOf(false) }

    val parsedAmount = MoneyFormat.parse(amount)
    val parsedDay = day.toIntOrNull()
    val titleError = if (title.isBlank()) "Title is required" else null
    val amountError = if (parsedAmount == null || parsedAmount <= 0) "Amount must be greater than 0" else null
    val dayError = if (parsedDay == null || parsedDay !in 1..31) "Day must be 1–31" else null
    val categoryError = if (categories.none { it.id == categoryId }) "Choose a category" else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add planned payment" else "Edit planned payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Title (e.g. Lease payment)") },
                    singleLine = true,
                    isError = showErrors && titleError != null,
                    supportingText = if (showErrors && titleError != null) ({ Text(titleError) }) else null,
                )
                AmountField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "Amount",
                    currency = currency,
                    error = if (showErrors) amountError else null,
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("Day of month") },
                    singleLine = true,
                    isError = showErrors && dayError != null,
                    supportingText = if (showErrors && dayError != null) ({ Text(dayError) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c ->
                        FilterChip(selected = c.id == categoryId, onClick = { categoryId = c.id }, label = { Text("${c.emoji} ${c.name}") })
                    }
                }
                if (showErrors && categoryError != null) {
                    Text(categoryError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (titleError != null || amountError != null || dayError != null || categoryError != null) {
                    showErrors = true
                } else {
                    onSave(initial.copy(title = title.trim(), amountMinor = parsedAmount!!, dayOfMonth = parsedDay!!, categoryId = categoryId))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
