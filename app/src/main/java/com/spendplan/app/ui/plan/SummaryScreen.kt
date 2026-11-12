package com.spendplan.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendplan.app.AppContainer
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.data.db.TransactionType
import com.spendplan.app.domain.BudgetCalculator
import com.spendplan.app.domain.BudgetPeriod
import com.spendplan.app.domain.CurrencyOption
import com.spendplan.app.domain.MonthSummary
import com.spendplan.app.domain.MoneyFormat
import com.spendplan.app.ui.MonthViewModel
import com.spendplan.app.ui.appViewModel
import com.spendplan.app.ui.components.CategoryBadge
import com.spendplan.app.ui.components.EmptyState
import com.spendplan.app.ui.components.LabeledAmount
import com.spendplan.app.ui.components.MonthSelector
import com.spendplan.app.ui.components.SectionCard
import com.spendplan.app.ui.theme.StatusColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class SummaryUiState(
    val month: YearMonth,
    val startDay: Int,
    val currency: CurrencyOption,
    val summary: MonthSummary,
    val categories: Map<Long, CategoryEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModel(container: AppContainer, month: YearMonth) : MonthViewModel(container, initialMonth = month) {
    val state: StateFlow<SummaryUiState?> = monthContextFlow.flatMapLatest { ctx ->
        val range = BudgetPeriod.range(ctx.month, ctx.settings.monthStartDay)
        combine(
            container.transactionRepository.observeRange(range),
            container.planRepository.observe(ctx.month),
            container.categoryRepository.categories,
        ) { txs, plan, cats ->
            val actual = txs.filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
            SummaryUiState(
                month = ctx.month,
                startDay = ctx.settings.monthStartDay,
                currency = ctx.settings.currency,
                summary = BudgetCalculator.summarize(
                    planned = plan.budgets,
                    actual = actual,
                    plannedIncome = plan.expectedIncome,
                    actualIncome = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor },
                ),
                categories = cats.associateBy { it.id },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(month: YearMonth, onBack: () -> Unit) {
    val vm = appViewModel(key = "summary-$month") { SummaryViewModel(it, month) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Month summary") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        val sum = s.summary
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MonthSelector(s.month, s.startDay, vm::previousMonth, vm::nextMonth) }

            item {
                SectionCard(title = "Savings") {
                    Text(
                        MoneyFormat.format(sum.savings, s.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = StatusColors.forAmount(sum.savings),
                    )
                    Text(
                        "Actual income minus actual expenses" +
                            if (sum.plannedIncome > 0) " · planned savings ${MoneyFormat.format(sum.plannedSavings, s.currency)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = "Totals") {
                    LabeledAmount("Planned income", MoneyFormat.format(sum.plannedIncome, s.currency))
                    LabeledAmount("Actual income", MoneyFormat.format(sum.actualIncome, s.currency))
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    LabeledAmount("Planned expenses", MoneyFormat.format(sum.totalPlanned, s.currency))
                    LabeledAmount("Actual expenses", MoneyFormat.format(sum.totalActual, s.currency))
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    LabeledAmount("Total over budget", MoneyFormat.format(sum.totalOver, s.currency), StatusColors.bad)
                    LabeledAmount("Total under budget", MoneyFormat.format(sum.totalUnder, s.currency), StatusColors.good)
                    LabeledAmount(
                        if (sum.netDifference >= 0) "Net under budget" else "Net over budget",
                        MoneyFormat.format(kotlin.math.abs(sum.netDifference), s.currency),
                        StatusColors.forAmount(sum.netDifference),
                    )
                }
            }

            if (sum.rows.isEmpty()) {
                item { EmptyState("📊", "Nothing to compare", "There's no plan or spending for this month yet.") }
            } else {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Category", Modifier.weight(1.4f), style = MaterialTheme.typography.labelMedium)
                        Text("Planned", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                        Text("Actual", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                        Text("Diff", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                }
                items(sum.rows, key = { it.categoryId }) { row ->
                    val cat = s.categories[row.categoryId]
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1.4f), verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(cat, 28.dp)
                            Text(
                                cat?.name ?: "Unknown",
                                Modifier.padding(start = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(Modifier.weight(3f)) {
                            Row {
                                AmountCell(row.planned, Modifier.weight(1f))
                                AmountCell(row.actual, Modifier.weight(1f))
                                Text(
                                    (if (row.difference > 0) "+" else "") + MoneyFormat.compact(row.difference),
                                    Modifier.weight(1f),
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StatusColors.forAmount(row.difference),
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Diff = planned − actual. Positive means you spent less than planned.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountCell(value: Long, modifier: Modifier) {
    Text(MoneyFormat.compact(value), modifier, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
}
