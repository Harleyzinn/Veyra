package app.veyra.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.veyra.feature.finance.*
import app.veyra.model.Item

@Composable internal fun FinanceReportPanel(all: List<Item>, report: FinanceReport?, basis: ReportBasis,
    changeBasis: (ReportBasis) -> Unit, from: String, through: String, changeFrom: (String) -> Unit,
    changeThrough: (String) -> Unit, account: String, card: String, category: String, query: String,
    changeAccount: (String) -> Unit, changeCard: (String) -> Unit, changeCategory: (String) -> Unit,
    changeQuery: (String) -> Unit, currency: String, hidden: Boolean, csv: () -> Unit, pdf: () -> Unit,
    reset: () -> Unit, open: (Item) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    StudioCard {
        SectionTitle("Seu relatório, do seu jeito")
        Choice("Analisar", ReportBasis.entries.map { it.name }, basis.name, { changeBasis(ReportBasis.valueOf(it)) }) {
            ReportBasis.valueOf(it).label
        }
        Text(if (basis == ReportBasis.CASH) "Pagamentos e recebimentos confirmados pela data do pagamento. A quitação da fatura entra uma vez; compras atuais no cartão não duplicam o caixa."
            else "Gastos pela data da compra, incluindo cartão. O pagamento da fatura não duplica a despesa; previsões ficam identificadas.", style = MaterialTheme.typography.bodySmall)
        Text("${dateLabel(from)} a ${dateLabel(through)}", style = MaterialTheme.typography.labelLarge)
        FinanceSection("Período e filtros do relatório") {
        FinanceField("De • AAAA-MM-DD", from, changeFrom)
        FinanceField("Até • AAAA-MM-DD", through, changeThrough)
        FinanceField("Buscar no relatório", query, changeQuery)
        Choice("Conta do relatório", listOf("") + all.filter { it.type == "account" && it.deletedAt == 0L && FinancialDomain.currency(it) == currency }.map { it.id }, account, changeAccount) { id -> all.firstOrNull { it.id == id }?.title ?: "Todas" }
        Choice("Cartão do relatório", listOf("") + all.filter { it.type == "card" && it.deletedAt == 0L && FinancialDomain.currency(it) == currency }.map { it.id }, card, changeCard) { id -> all.firstOrNull { it.id == id }?.title ?: "Todos" }
        FinanceField("Categoria do relatório", category, changeCategory)
        TextButton(onClick = reset) { Text("Limpar filtros do relatório") }
        }
        if (report == null) Text("Confira as datas: use um período em ordem crescente de até dez anos.", color = MaterialTheme.colorScheme.error)
        else {
            Text("${report.entries.size} ${if(report.entries.size==1) "registro selecionado" else "registros selecionados"}", style = MaterialTheme.typography.labelLarge)
            Text("Entradas: ${financeMoney(report.incomeMinor, currency, hidden)}")
            Text("${if (basis == ReportBasis.CASH) "Saídas pagas" else "Gastos reconhecidos"}: ${financeMoney(report.expenseMinor, currency, hidden)}")
            Text("Resultado: ${financeMoney(report.netMinor, currency, hidden)}", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = csv, modifier = Modifier.weight(1f)) { Text("Exportar CSV") }
                Button(onClick = pdf, modifier = Modifier.weight(1f)) { Text("Exportar PDF") }
            }
            Text("Arquivos incluem somente os registros e totais desta seleção. CSV é uma exportação de lançamentos; use o backup para restaurar seu espaço.", style = MaterialTheme.typography.bodySmall)
            if (report.entries.isEmpty()) Text("Nenhum registro corresponde aos filtros.")
            report.entries.take(5).forEach { FinanceTransactionRow(it, currency, hidden, open) }
            if (report.entries.size > 5) Text("Prévia dos primeiros cinco registros; a exportação inclui todos.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (report != null) FinanceChartStudio(report, basis, java.time.LocalDate.parse(from),
        java.time.LocalDate.parse(through), all, currency, hidden)
    }
}
