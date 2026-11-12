package com.spendplan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.domain.BudgetPeriod
import com.spendplan.app.domain.BudgetStatus
import com.spendplan.app.domain.CategoryProgress
import com.spendplan.app.domain.CurrencyOption
import com.spendplan.app.domain.MoneyFormat
import com.spendplan.app.ui.theme.StatusColors
import java.time.YearMonth

@Composable
fun MonthSelector(
    month: YearMonth,
    startDay: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(BudgetPeriod.label(month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (startDay != 1) {
                Text(
                    BudgetPeriod.rangeLabel(month, startDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/** Colored circle with the category emoji. */
@Composable
fun CategoryBadge(category: CategoryEntity?, size: Dp = 40.dp) {
    val color = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(category?.emoji ?: "❔", fontSize = (size.value * 0.45f).sp)
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 48.sp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Decimal-keyboard money field. Shows an error when the text isn't a valid amount. */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currency: CurrencyOption,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    val invalid = value.isNotBlank() && MoneyFormat.parse(value) == null
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        prefix = { Text(currency.symbol + " ") },
        singleLine = true,
        isError = invalid || error != null,
        supportingText = when {
            error != null -> ({ Text(error) })
            invalid -> ({ Text("Enter a valid amount (max 2 decimals)") })
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier,
    )
}

/** A category row with spent / budget text and a colored progress bar. */
@Composable
fun BudgetProgressRow(
    category: CategoryEntity?,
    progress: CategoryProgress,
    currency: CurrencyOption,
    modifier: Modifier = Modifier,
) {
    val color = StatusColors.forStatus(progress.status)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(category, 36.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category?.name ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (progress.budget > 0) "${(progress.ratio * 100).toInt()}%" else "No budget",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { if (progress.budget > 0) progress.ratio.coerceIn(0f, 1f) else 1f },
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(4.dp))
            val remainingText = when {
                progress.budget <= 0 -> ""
                progress.status == BudgetStatus.OVER -> " · over by ${MoneyFormat.format(-progress.remaining, currency)}"
                else -> " · ${MoneyFormat.format(progress.remaining, currency)} left"
            }
            Text(
                "${MoneyFormat.format(progress.spent, currency)} of ${MoneyFormat.format(progress.budget, currency)}$remainingText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LabeledAmount(label: String, amount: String, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}
