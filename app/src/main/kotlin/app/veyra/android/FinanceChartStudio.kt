package app.veyra.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.veyra.feature.finance.*
import app.veyra.model.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class ChartView(val label: String) {
    BARS("Barras"), LINE("Linhas"), AREA("Área"), DONUT("Rosca"), PIE("Pizza"), TABLE("Tabela")
}
private val chartPalette = listOf(Color(0xFFB7A1FF), Color(0xFF88D5BA), Color(0xFFF3C58A),
    Color(0xFF83BFEF), Color(0xFFEB9AC4), Color(0xFFBBC2CE), Color(0xFFD7DD82), Color(0xFFBFAD91))

@Composable internal fun FinanceChartStudio(report: FinanceReport, basis: ReportBasis, from: LocalDate,
    through: LocalDate, all: List<Item>, currency: String, hidden: Boolean) {
    var viewName by rememberSaveable { mutableStateOf(ChartView.BARS.name) }
    var grainName by rememberSaveable { mutableStateOf(ChartGrain.WEEK.name) }
    var breakdownName by rememberSaveable { mutableStateOf(ChartBreakdown.CATEGORY.name) }
    var metric by rememberSaveable { mutableStateOf("Entradas e gastos") }
    val view = ChartView.valueOf(viewName)
    val grain = ChartGrain.valueOf(grainName)
    val breakdown = ChartBreakdown.valueOf(breakdownName)
    val data by produceState<Result<FinanceChartData>?>(null, report, basis, from, through, grain, all, hidden) {
        value = null
        if (!hidden) value = withContext(Dispatchers.Default) {
            runCatching { FinanceChartData.build(report, basis, from, through, grain, all) }
        }
    }
    StudioCard {
        SectionTitle("Gráficos da seleção")
        Text("Mesmo período, moeda e filtros do relatório. Somente valores realizados; previsões ficam fora dos gráficos.", style = MaterialTheme.typography.bodySmall)
        Choice("Visualização", ChartView.entries.map { it.name }, viewName, { viewName = it }) { ChartView.valueOf(it).label }
        if (view == ChartView.DONUT || view == ChartView.PIE) {
            Choice("Distribuir gastos por", ChartBreakdown.entries.map { it.name }, breakdownName, { breakdownName = it }) { ChartBreakdown.valueOf(it).label }
        } else {
            Choice("Agrupar por", ChartGrain.entries.map { it.name }, grainName, { grainName = it }) { ChartGrain.valueOf(it).label }
            if (view != ChartView.TABLE) Choice("Comparar", listOf("Entradas e gastos", "Resultado do período"), metric, { metric = it })
            if (grain == ChartGrain.WEEK) Text("Semanas começam na segunda-feira; só entram registros dentro do período escolhido.", style = MaterialTheme.typography.bodySmall)
        }
        if (hidden) Text("Gráficos ocultos pelo modo privacidade.")
        else {
            val chart = data?.getOrNull()
            when {
                data == null -> Text("Preparando gráfico…")
                chart == null -> Text("Não foi possível calcular este gráfico. Confira os registros e tente um período menor.", color = MaterialTheme.colorScheme.error)
                report.incomeMinor == 0L && report.expenseMinor == 0L -> Text("Nenhum valor realizado nesta seleção para desenhar o gráfico.")
                view == ChartView.DONUT || view == ChartView.PIE -> FinanceDistribution(chart.slices(breakdown), view == ChartView.DONUT, currency)
                view == ChartView.TABLE -> FinanceChartTable(chart.points, currency)
                else -> FinanceTimeline(chart.points, view, metric == "Resultado do período", currency)
            }
        }
    }
}

@Composable private fun FinanceTimeline(points: List<FinanceChartPoint>, view: ChartView, net: Boolean, currency: String) {
    var selected by remember(points) { mutableIntStateOf(0) }
    val primary = MaterialTheme.colorScheme.primary
    val income = MaterialTheme.colorScheme.secondary
    val expense = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val series = if (net) listOf(points.map { it.resultMinor } to primary)
        else listOf(points.map { it.incomeMinor } to income, points.map { it.expenseMinor } to expense)
    val low = minOf(0L, series.minOf { it.first.minOrNull() ?: 0L })
    val high = maxOf(0L, series.maxOf { it.first.maxOrNull() ?: 0L })
    Text(financeMoney(high, currency), style = MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(210.dp).semantics {
        contentDescription = "Gráfico de ${view.label.lowercase()}. ${if (net) "Resultado" else "Entradas e gastos"}. Selecione o período nos botões abaixo."
    }.pointerInput(points) { detectTapGestures { position ->
        selected = ((position.x / size.width) * points.size).toInt().coerceIn(points.indices)
    } }) {
        val padding = 8.dp.toPx()
        val width = size.width - padding * 2
        val step = width / points.size
        val range = (high.toDouble() - low.toDouble()).coerceAtLeast(1.0)
        fun y(amount: Long) = (padding + (high.toDouble() - amount.toDouble()) / range * (size.height - padding * 2)).toFloat()
        fun x(index: Int) = padding + step * (index + .5f)
        val zero = y(0L)
        repeat(4) { tick ->
            val height = padding + (size.height - padding * 2) * tick / 3
            drawLine(grid.copy(alpha = .4f), Offset(padding, height), Offset(size.width - padding, height), 1.dp.toPx())
        }
        drawLine(grid, Offset(padding, zero), Offset(size.width - padding, zero), 1.dp.toPx())
        series.forEachIndexed { s, (values, color) ->
            if (view == ChartView.BARS) values.forEachIndexed { i, value ->
                val barWidth = step * .72f / series.size
                val left = x(i) - step * .36f + barWidth * s
                drawRect(color.copy(alpha = if (i == selected) 1f else .65f),
                    Offset(left, minOf(zero, y(value))), Size(barWidth, kotlin.math.abs(y(value) - zero)))
            } else {
                val line = Path().apply { values.forEachIndexed { i, value -> if (i == 0) moveTo(x(i), y(value)) else lineTo(x(i), y(value)) } }
                if (view == ChartView.AREA) {
                    val area = Path().apply { addPath(line); lineTo(x(values.lastIndex), zero); lineTo(x(0), zero); close() }
                    drawPath(area, color.copy(alpha = .16f))
                }
                drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(color, 5.dp.toPx(), Offset(x(selected), y(values[selected])))
            }
        }
    }
    Text(financeMoney(low, currency), style = MaterialTheme.typography.labelSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(dateLabel(points.first().date.toString()), style = MaterialTheme.typography.labelSmall)
        Text(dateLabel(points.last().date.toString()), style = MaterialTheme.typography.labelSmall)
    }
    Text(if (net) "Lilás: entradas menos gastos. Resultado do período, sem saldo inicial de contas."
        else "Verde: entradas. Dourado: gastos.", style = MaterialTheme.typography.bodySmall)
    val point = points[selected]
    Text("Período selecionado: ${dateLabel(point.date.toString())}", style = MaterialTheme.typography.titleSmall)
    Text("Entradas ${financeMoney(point.incomeMinor, currency)} · gastos ${financeMoney(point.expenseMinor, currency)}")
    Text("Resultado ${financeMoney(point.resultMinor, currency)}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { selected-- }, enabled = selected > 0) { Text("Anterior") }
        OutlinedButton(onClick = { selected++ }, enabled = selected < points.lastIndex) { Text("Próximo") }
    }
}

@Composable private fun FinanceDistribution(slices: List<FinanceChartSlice>, donut: Boolean, currency: String) {
    if (slices.isEmpty()) { Text("Nenhum gasto realizado nesta seleção."); return }
    val total = slices.fold(0L) { sum, slice -> Math.addExact(sum, slice.amountMinor) }
    if (total <= 0L) { Text("Nenhum gasto realizado nesta seleção."); return }
    var selected by remember(slices) { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(195.dp).semantics { contentDescription = "Distribuição dos gastos em ${if (donut) "rosca" else "pizza"}. Grupos e valores listados abaixo." }) {
            var angle = -90f
            val inset = if (donut) 17.dp.toPx() else 4.dp.toPx()
            slices.forEachIndexed { i, slice ->
                val sweep = (slice.amountMinor.toDouble() / total * 360).toFloat()
                drawArc(chartPalette[i], angle, sweep, !donut, Offset(inset, inset),
                    Size(size.width - inset * 2, size.height - inset * 2),
                    style = if (donut) Stroke(if (i == selected) 25.dp.toPx() else 18.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill)
                angle += sweep
            }
        }
    }
    Text("Total de gastos: ${financeMoney(total, currency)}", style = MaterialTheme.typography.titleSmall)
    Text("Selecionado: ${slices[selected].label}", style = MaterialTheme.typography.labelLarge)
    slices.forEachIndexed { i, slice ->
        Column(Modifier.fillMaxWidth().clickable { selected = i }.padding(vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("●", color = chartPalette[i])
                Text(slice.label, Modifier.weight(1f))
                Text(String.format(Brazilian, "%.1f%%", slice.amountMinor.toDouble() / total * 100))
            }
            Text(financeMoney(slice.amountMinor, currency), style = MaterialTheme.typography.labelLarge)
        }
    }
    Text("Até oito grupos no gráfico; os demais são somados em Outros grupos. Nenhum valor é descartado.", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun FinanceChartTable(points: List<FinanceChartPoint>, currency: String) {
    var page by remember(points) { mutableIntStateOf(0) }
    val size = 12
    Text("Entradas, gastos e resultado por período", style = MaterialTheme.typography.titleSmall)
    points.drop(page * size).take(size).forEach { point ->
        HorizontalDivider()
        Text(dateLabel(point.date.toString()), style = MaterialTheme.typography.labelLarge)
        Text("Entradas ${financeMoney(point.incomeMinor, currency)} · gastos ${financeMoney(point.expenseMinor, currency)}", style = MaterialTheme.typography.bodySmall)
        Text("Resultado ${financeMoney(point.resultMinor, currency)}", style = MaterialTheme.typography.bodySmall)
    }
    Text("Página ${page + 1} de ${(points.size + size - 1) / size}", style = MaterialTheme.typography.labelSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { page-- }, enabled = page > 0) { Text("Página anterior") }
        OutlinedButton(onClick = { page++ }, enabled = (page + 1) * size < points.size) { Text("Próxima página") }
    }
}
