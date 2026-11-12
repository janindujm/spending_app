package com.spendplan.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendplan.app.AppContainer
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.data.db.TransactionType
import com.spendplan.app.domain.BudgetCalculator
import com.spendplan.app.domain.BudgetPeriod
import com.spendplan.app.domain.CategoryProgress
import com.spendplan.app.domain.CurrencyOption
import com.spendplan.app.domain.MoneyFormat
import com.spendplan.app.ui.MonthViewModel
import com.spendplan.app.ui.appViewModel
import com.spendplan.app.ui.components.BudgetProgressRow
import com.spendplan.app.ui.components.CategoryBadge
import com.spendplan.app.ui.components.ChartSlice
import com.spendplan.app.ui.components.DailyBarChart
import com.spendplan.app.ui.components.DonutChart
import com.spendplan.app.ui.components.EmptyState
import com.spendplan.app.ui.components.MonthSelector
import com.spendplan.app.ui.components.SectionCard
import com.spendplan.app.ui.theme.StatusColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val month: YearMonth,
    val startDay: Int,
    val currency: CurrencyOption,
    val income: Long,
    val expense: Long,
    val plannedTotal: Long,
    val percentUsed: Float?,
    val slices: List<ChartSlice>,
    val daily: List<Long>,
    val dayLabels: List<String>,
    val todayIndex: Int?,
    val top: List<Pair<CategoryEntity?, Long>>,
    val progress: List<Pair<CategoryEntity?, CategoryProgress>>,
    val transactionCount: Int,
) {
    val net: Long get() = income - expense
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : MonthViewModel(container) {
    private val transactions = container.transactionRepository
    private val plans = container.planRepository
    private val categories = container.categoryRepository.categories

    val state: StateFlow<HomeUiState?> = monthContextFlow.flatMapLatest { ctx ->
        val range = BudgetPeriod.range(ctx.month, ctx.settings.monthStartDay)
        combine(transactions.observeRange(range), plans.observe(ctx.month), categories) { txs, plan, cats ->
            val byId = cats.associateBy { it.id }
            val expenses = txs.filter { it.type == TransactionType.EXPENSE }
            val spentByCat = expenses.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
            val days = range.days
            val dailyMap = expenses.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
            val plannedTotal = plan.budgets.values.sum()
            val expense = expenses.sumOf { it.amountMinor }
            val todayIdx = days.indexOf(LocalDate.now()).takeIf { it >= 0 }
            HomeUiState(
                month = ctx.month,
                startDay = ctx.settings.monthStartDay,
                currency = ctx.settings.currency,
                income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor },
                expense = expense,
                plannedTotal = plannedTotal,
                percentUsed = BudgetCalculator.percentUsed(expense, plannedTotal),
                slices = spentByCat.entries.sortedByDescending { it.value }.map { (id, v) ->
                    val c = byId[id]
                    ChartSlice(c?.name ?: "Unknown", v, c?.let { Color(it.color) } ?: Color.Gray)
                },
                daily = days.map { dailyMap[it.toEpochDay()] ?: 0L },
                dayLabels = days.map { "${it.dayOfMonth}/${it.monthValue}" },
                todayIndex = todayIdx,
                top = BudgetCalculator.topCategories(spentByCat).map { (id, v) -> byId[id] to v },
                progress = BudgetCalculator.progress(plan.budgets, spentByCat).map { byId[it.categoryId] to it },
                transactionCount = txs.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSummary: (YearMonth) -> Unit, onOpenPlan: () -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("SpendPlan") }) }) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MonthSelector(s.month, s.startDay, vm::previousMonth, vm::nextMonth) }
            item { SummaryCards(s) }

            if (s.transactionCount == 0) {
                item {
                    EmptyState(
                        emoji = "🪙",
                        title = "No transactions this month",
                        message = "Tap + to add your first income or expense.",
                    )
                }
            }

            if (s.slices.isNotEmpty()) {
                item {
                    SectionCard(title = "Spending by category") {
                        DonutChart(s.slices, "Spent", MoneyFormat.format(s.expense, s.currency, showDecimals = false))
                    }
                }
                item {
                    SectionCard(title = "Daily spending") {
                        val dailyBudget = if (s.plannedTotal > 0 && s.daily.isNotEmpty()) s.plannedTotal / s.daily.size else null
                        DailyBarChart(s.daily, s.dayLabels, averageLine = dailyBudget, highlightIndex = s.todayIndex)
                        if (dailyBudget != null) {
                            Text(
                                "Dashed line: planned daily average ${MoneyFormat.format(dailyBudget, s.currency, false)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = "Top categories") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            s.top.forEachIndexed { i, (cat, amount) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                                    CategoryBadge(cat, 32.dp)
                                    Text(
                                        cat?.name ?: "Unknown",
                                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(MoneyFormat.format(amount, s.currency), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Budget vs actual") {
                    if (s.progress.isEmpty()) {
                        Text(
                            "No budget planned for this month yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onOpenPlan) { Text("Create a plan") }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            s.progress.forEach { (cat, p) -> BudgetProgressRow(cat, p, s.currency) }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onOpenSummary(s.month) }) { Text("Month summary") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCards(s: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Income", MoneyFormat.format(s.income, s.currency), StatusColors.good, Modifier.weight(1f))
            StatCard("Expenses", MoneyFormat.format(s.expense, s.currency), StatusColors.bad, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Net balance", MoneyFormat.format(s.net, s.currency), StatusColors.forAmount(s.net), Modifier.weight(1f))
            val pct = s.percentUsed
            val pctColor = when {
                pct == null -> MaterialTheme.colorScheme.onSurfaceVariant
                pct > 100f -> StatusColors.bad
                pct >= 80f -> StatusColors.warning
                else -> StatusColors.good
            }
            StatCard(
                "Budget used",
                pct?.let { "${it.toInt()}%" } ?: "No plan",
                pctColor,
                Modifier.weight(1f),
                subtitle = if (s.plannedTotal > 0) "of ${MoneyFormat.format(s.plannedTotal, s.currency, false)}" else null,
            )
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, valueColor: Color, modifier: Modifier, subtitle: String? = null) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
