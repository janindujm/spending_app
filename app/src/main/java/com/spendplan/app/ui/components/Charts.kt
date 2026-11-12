package com.spendplan.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendplan.app.domain.MoneyFormat

data class ChartSlice(val label: String, val value: Long, val color: Color)

/** Donut chart of category spending, drawn with Canvas. Shows the total in the middle. */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val gapColor = MaterialTheme.colorScheme.surfaceContainer
    val description = slices.joinToString { "${it.label} ${(it.value * 100 / total)}%" }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(150.dp)
                    .semantics { contentDescription = "Spending by category: $description" },
            ) {
                val strokeWidth = size.minDimension * 0.18f
                val inset = strokeWidth / 2
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth))
                var start = -90f
                slices.forEach { slice ->
                    val sweep = 360f * slice.value / total
                    drawArc(slice.color, start, sweep, false, topLeft, arcSize, style = Stroke(strokeWidth))
                    // thin separators between slices
                    if (slices.size > 1) {
                        drawArc(gapColor, start + sweep - 0.8f, 0.8f, false, topLeft, arcSize, style = Stroke(strokeWidth))
                    }
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(centerValue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shown = slices.take(6)
            shown.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(slice.color, CircleShape))
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp).weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${slice.value * 100 / total}%", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (slices.size > shown.size) {
                Text(
                    "+${slices.size - shown.size} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Daily spending bars. [values] has one entry per day of the period (minor units).
 * Optional [averageLine] draws a dashed reference line (e.g. daily budget).
 */
@Composable
fun DailyBarChart(
    values: List<Long>,
    dayLabels: List<String>,
    modifier: Modifier = Modifier,
    averageLine: Long? = null,
    highlightIndex: Int? = null,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.tertiary
    val maxValue = maxOf(values.maxOrNull() ?: 0L, averageLine ?: 0L).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                MoneyFormat.compact(maxValue),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = "Daily spending bar chart" },
        ) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = (slot * 0.65f).coerceAtLeast(1f)
            // baseline + mid grid
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            drawLine(
                grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = size.height * (v.toFloat() / maxValue)
                drawRoundRect(
                    color = if (i == highlightIndex) highlight else barColor,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 3, barWidth / 3),
                )
            }
            averageLine?.takeIf { it > 0 }?.let {
                val y = size.height - size.height * (it.toFloat() / maxValue)
                drawLine(
                    lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val labels = if (dayLabels.isEmpty()) emptyList() else listOf(dayLabels.first(), dayLabels[dayLabels.size / 2], dayLabels.last())
            labels.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
