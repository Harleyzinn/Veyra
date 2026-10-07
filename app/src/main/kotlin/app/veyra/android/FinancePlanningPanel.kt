package app.veyra.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.veyra.feature.finance.*
import app.veyra.model.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable internal fun FinancePlanningPanel(base: CashProjection, currency: String, hidden: Boolean,
    horizon: String, changeHorizon: (String) -> Unit) {
    var income by rememberSaveable(currency) { mutableStateOf("") }
    var expense by rememberSaveable(currency) { mutableStateOf("") }
    var reserve by rememberSaveable(currency) { mutableStateOf("") }
    var date by rememberSaveable(base.points.first().date.toString()) { mutableStateOf(base.points.first().date.toString()) }
    val result = remember(base, income, expense, reserve, date, currency) { runCatching {
        fun amount(text: String) = if (text.isBlank()) 0L else FinancialDomain.parseMinor(text, currency)
        val scenarioDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: error("Informe uma data válida no formato AAAA-MM-DD.")
        FinancePlanning.simulate(base, scenarioDate, amount(income), amount(expense), amount(reserve))
    } }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StudioCard {
            SectionTitle("Teste antes de decidir")
            Text("Veja como uma entrada ou gasto extra afeta o caixa. Este cenário não cria lançamentos nem altera seus saldos.")
            Choice("Horizonte", listOf("7 dias", "15 dias", "30 dias", "60 dias", "90 dias", "Fim do mês", "Fim do ano"), horizon, changeHorizon)
            if (hidden) Text("Exiba os valores no topo para usar o simulador.") else {
                FinanceField("Entrada extra", income, { income = it }, true)
                FinanceField("Gasto extra", expense, { expense = it }, true)
                FinanceField("Data do cenário • AAAA-MM-DD", date, { date = it })
                FinanceField("Reserva mínima no caixa", reserve, { reserve = it }, true)
                Text("A reserva é um piso para comparação; não é descontada como despesa.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { income = ""; expense = ""; reserve = ""; date = base.points.first().date.toString() }) { Text("Limpar cenário") }
            }
        }
        if (!hidden) result.fold(onSuccess = { scenario ->
            StudioCard {
                SectionTitle("Seu cenário")
                Text(financeMoney(scenario.projection.projectedBalance, currency), style = MaterialTheme.typography.headlineLarge)
                Text("Saldo final sem cenário: ${financeMoney(base.projectedBalance, currency)}")
                Text("Menor saldo diário: ${financeMoney(scenario.lowestBalance, currency)}")
                Text("Margem diária adicional: ${financeMoney(scenario.dailyMargin, currency)}", style = MaterialTheme.typography.titleMedium)
                Text("Dividida igualmente desde hoje, sem baixar da reserva nos dias previstos. Considera apenas o caixa registrado; dados ausentes mudam o resultado.", style = MaterialTheme.typography.bodySmall)
                scenario.firstBelowReserve?.let { Text("Abaixo da reserva em ${dateLabel(it.toString())}", color = MaterialTheme.colorScheme.error) }
                FinanceFlowChart(scenario.projection.points, currency, false)
            }
        }, onFailure = { failure -> StudioCard { Text(failure.message ?: "Confira valores e data do cenário.", color = MaterialTheme.colorScheme.error) } })
    }
}

@Composable internal fun FinanceReviewPanel(items: List<Item>, period: FinancialPeriod, currency: String,
    hidden: Boolean, open: (Item) -> Unit) {
    val review by produceState<Result<FinanceReview>?>(null, items, period, currency) {
        value = withContext(Dispatchers.Default) { runCatching { FinancePlanning.review(items, period.start, period.end, currency) } }
    }
    var limit by remember(period, currency) { mutableIntStateOf(20) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StudioCard {
            SectionTitle("Conferência dos registros")
            Text("${dateLabel(period.start.toString())} a ${dateLabel(period.end.toString())}")
            Text("Possíveis duplicidades combinam descrição, data, valor, tipo e conta/cartão. Revise antes de corrigir; compras iguais podem ser legítimas.")
        }
        if (review == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        review?.exceptionOrNull()?.let { StudioCard { Text(it.message ?: "Não foi possível conferir os registros.") } }
        review?.getOrNull()?.let { data ->
            StudioCard {
                Text("${data.duplicates.size} grupos para conferir · ${data.uncategorized.size} sem categoria", style = MaterialTheme.typography.titleMedium)
                Text("Recorrências, parcelas automáticas e pagamentos de fatura não são comparados como duplicidades.", style = MaterialTheme.typography.bodySmall)
            }
            if (data.duplicates.isEmpty() && data.uncategorized.isEmpty()) EmptyCard("Conferência em dia", "Nenhuma dessas pendências foi encontrada neste período.")
            data.duplicates.take(limit).forEach { group -> StudioCard {
                var groupLimit by remember(group.map { it.id }) { mutableIntStateOf(5) }
                SectionTitle("Possível duplicidade · ${group.size} registros")
                group.take(groupLimit).forEach { item -> TextButton(onClick = { open(item) }) { Text("${item.title} · ${financeMoney(FinancialDomain.amount(item), currency, hidden)} · ${dateLabel(item.date)}") } }
                if (group.size > groupLimit) TextButton(onClick = { groupLimit += 5 }) { Text("Mais registros deste grupo") }
            } }
            if (data.uncategorized.isNotEmpty()) StudioCard {
                SectionTitle("Sem categoria")
                data.uncategorized.take(limit).forEach { item -> TextButton(onClick = { open(item) }) { Text("${item.title} · ${financeMoney(FinancialDomain.amount(item), currency, hidden)}") } }
            }
            if (data.duplicates.size > limit || data.uncategorized.size > limit) OutlinedButton(onClick = { limit += 20 }) { Text("Mostrar mais registros") }
        }
    }
}
