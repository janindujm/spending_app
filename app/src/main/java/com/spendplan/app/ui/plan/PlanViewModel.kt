package com.spendplan.app.ui.plan

import androidx.lifecycle.viewModelScope
import com.spendplan.app.AppContainer
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.data.db.PlannedItemEntity
import com.spendplan.app.data.db.TransactionType
import com.spendplan.app.domain.AutoFillSource
import com.spendplan.app.domain.BudgetCalculator
import com.spendplan.app.domain.BudgetPeriod
import com.spendplan.app.domain.CurrencyOption
import com.spendplan.app.domain.MoneyFormat
import com.spendplan.app.ui.MonthViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** The editable text of a month's plan. */
data class PlanFields(
    val month: YearMonth,
    val incomeText: String,
    val budgetTexts: Map<Long, String>,
) {
    val income: Long get() = MoneyFormat.parse(incomeText) ?: 0L
    val budgets: Map<Long, Long> get() = budgetTexts.mapValues { (_, t) -> MoneyFormat.parse(t) ?: 0L }.filterValues { it > 0 }
}

data class PlanUiState(
    val month: YearMonth,
    val startDay: Int,
    val currency: CurrencyOption,
    val fields: PlanFields,
    val expenseCategories: List<CategoryEntity>,
    val categoriesById: Map<Long, CategoryEntity>,
    val spent: Map<Long, Long>,
    val actualIncome: Long,
    val plannedItems: List<PlannedItemEntity>,
    val saving: Boolean,
    val isFuture: Boolean,
) {
    val totalPlanned: Long get() = fields.budgets.values.sum()
    val unallocated: Long get() = BudgetCalculator.unallocated(fields.income, fields.budgets.values)
    val plannedItemsByCategory: Map<Long, Long>
        get() = plannedItems.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(private val container: AppContainer) : MonthViewModel(container, initialOffset = 1) {
    private val plans = container.planRepository
    private val fields = MutableStateFlow<PlanFields?>(null)
    private val saving = MutableStateFlow(false)
    private var dirty = false
    private var saveJob: Job? = null

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    init {
        viewModelScope.launch {
            monthContextFlow.map { it.month }.distinctUntilChanged().collect { month ->
                flush()
                val plan = plans.observe(month).first()
                fields.value = PlanFields(
                    month = month,
                    incomeText = MoneyFormat.editable(plan.expectedIncome),
                    budgetTexts = plan.budgets.mapValues { (_, v) -> MoneyFormat.editable(v) },
                )
                dirty = false
            }
        }
    }

    val state: StateFlow<PlanUiState?> = monthContextFlow.flatMapLatest { ctx ->
        val range = BudgetPeriod.range(ctx.month, ctx.settings.monthStartDay)
        combine(
            fields.filterNotNull(),
            container.transactionRepository.observeRange(range),
            plans.observe(ctx.month),
            container.categoryRepository.categories,
            saving,
        ) { f, txs, plan, cats, isSaving ->
            if (f.month != ctx.month) return@combine null
            PlanUiState(
                month = ctx.month,
                startDay = ctx.settings.monthStartDay,
                currency = ctx.settings.currency,
                fields = f,
                expenseCategories = cats.filter { it.type == TransactionType.EXPENSE },
                categoriesById = cats.associateBy { it.id },
                spent = txs.filter { it.type == TransactionType.EXPENSE }
                    .groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } },
                actualIncome = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor },
                plannedItems = plan.plannedItems,
                saving = isSaving,
                isFuture = range.start.isAfter(LocalDate.now()),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setIncome(text: String) = edit { it.copy(incomeText = text) }

    fun setBudget(categoryId: Long, text: String) = edit { it.copy(budgetTexts = it.budgetTexts + (categoryId to text)) }

    private fun edit(change: (PlanFields) -> PlanFields) {
        val current = fields.value ?: return
        fields.value = change(current)
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            persist()
        }
    }

    private suspend fun persist() {
        val f = fields.value ?: return
        if (!dirty) return
        saving.value = true
        plans.save(f.month, f.income, f.budgets)
        dirty = false
        saving.value = false
        container.onDataChanged()
    }

    /** Saves pending edits in the app scope so they survive leaving the screen. */
    private fun flush() {
        val f = fields.value ?: return
        if (!dirty) return
        saveJob?.cancel()
        dirty = false
        container.appScope.launch {
            plans.save(f.month, f.income, f.budgets)
            container.onDataChanged()
        }
    }

    fun autoFill(source: AutoFillSource, copyPlannedItems: Boolean) {
        val ctx = monthContext.value ?: return
        viewModelScope.launch {
            val result = plans.autoFill(ctx.month, ctx.settings.monthStartDay, source)
            val copied = if (copyPlannedItems) plans.copyPlannedItemsFromPrevious(ctx.month) else 0
            if (result.expectedIncome == 0L && result.budgets.isEmpty()) {
                _messages.value = "No transactions found in previous months to auto-fill from." +
                    if (copied > 0) " Copied $copied planned item(s)." else ""
                return@launch
            }
            fields.value = PlanFields(
                month = ctx.month,
                incomeText = MoneyFormat.editable(result.expectedIncome),
                budgetTexts = result.budgets.mapValues { (_, v) -> MoneyFormat.editable(v) },
            )
            dirty = true
            persist()
            _messages.value = "Plan filled from " +
                (if (source == AutoFillSource.LAST_MONTH) "last month" else "the 3-month average") +
                ". Adjust the values as needed." + if (copied > 0) " Copied $copied planned item(s)." else ""
        }
    }

    fun messageShown() { _messages.value = null }

    fun savePlannedItem(item: PlannedItemEntity) = viewModelScope.launch { plans.savePlannedItem(item) }

    fun deletePlannedItem(item: PlannedItemEntity) = viewModelScope.launch {
        if (item.paidTransactionId != null) plans.markUnpaid(item)
        plans.deletePlannedItem(item)
        container.onDataChanged()
    }

    fun setPaid(item: PlannedItemEntity, paid: Boolean) {
        val ctx = monthContext.value ?: return
        viewModelScope.launch {
            if (paid) {
                plans.markPaid(item, LocalDate.now(), ctx.settings.monthStartDay)
                _messages.value = "Marked as paid — an expense of ${MoneyFormat.format(item.amountMinor, ctx.settings.currency)} was added."
            } else {
                plans.markUnpaid(item)
                _messages.value = "Marked as unpaid — the expense was removed."
            }
            container.onDataChanged()
        }
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }
}
