package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate

enum class ChartGrain(val label: String) { DAY("Dia"), WEEK("Semana"), MONTH("Mês") }
enum class ChartBreakdown(val label: String) { CATEGORY("Categoria"), ACCOUNT("Conta"), CARD("Cartão") }
data class FinanceChartPoint(val date: LocalDate, val incomeMinor: Long, val expenseMinor: Long) {
    val resultMinor: Long get() = Math.subtractExact(incomeMinor, expenseMinor)
}
data class FinanceChartSlice(val label: String, val amountMinor: Long)
data class FinanceChartData(val points: List<FinanceChartPoint>, val groups: Map<ChartBreakdown, List<FinanceChartSlice>>) {
    fun slices(breakdown: ChartBreakdown, limit: Int = 8): List<FinanceChartSlice> {
        require(limit >= 2)
        val rows = groups[breakdown].orEmpty()
        if (rows.size <= limit) return rows
        val remaining = rows.drop(limit - 1).fold(0L) { sum, row -> Math.addExact(sum, row.amountMinor) }
        return rows.take(limit - 1) + FinanceChartSlice("Outros grupos (agrupados)", remaining)
    }
    companion object {
        fun build(report: FinanceReport, basis: ReportBasis, from: LocalDate, through: LocalDate,
            grain: ChartGrain, all: List<Item>, today: LocalDate = LocalDate.now()): FinanceChartData {
            require(!through.isBefore(from) && !through.isAfter(from.plusYears(10)))
            fun bucket(date: LocalDate) = when (grain) {
                ChartGrain.DAY -> date
                ChartGrain.WEEK -> date.minusDays((date.dayOfWeek.value - 1).toLong())
                ChartGrain.MONTH -> date.withDayOfMonth(1)
            }
            fun next(date: LocalDate) = when (grain) {
                ChartGrain.DAY -> date.plusDays(1)
                ChartGrain.WEEK -> date.plusWeeks(1)
                ChartGrain.MONTH -> date.plusMonths(1)
            }
            val rows = FinanceReports.realizedEntries(report.entries, basis, through, today)
            val byDate = rows.groupBy { bucket(if (basis == ReportBasis.CASH) FinancialDomain.bookedDate(it) else FinancialDomain.transactionDate(it)) }
            val points = generateSequence(bucket(from), ::next).takeWhile { it <= bucket(through) }.map { date ->
                fun sum(type: String) = byDate[date].orEmpty().filter { it.type == type }
                    .fold(0L) { sum, item -> Math.addExact(sum, FinancialDomain.amount(item)) }
                FinanceChartPoint(date, sum("income"), sum("expense"))
            }.toList()
            val names = all.associateBy { it.id }
            val expenses = rows.filter { it.type == "expense" }
            val groups = ChartBreakdown.entries.associateWith { breakdown ->
                val grouped = expenses.groupBy { item -> when (breakdown) {
                    ChartBreakdown.CATEGORY -> item.value("category").ifBlank { "Sem categoria" }
                    ChartBreakdown.ACCOUNT -> item.value("account")
                    ChartBreakdown.CARD -> item.value("card")
                } }
                fun label(key: String): String = if (breakdown == ChartBreakdown.CATEGORY) key
                    else if (key.isBlank()) if (breakdown == ChartBreakdown.ACCOUNT) "Sem conta" else "Fora do cartão"
                    else names[key]?.title ?: "Registro indisponível • ${key.takeLast(6)}"
                val duplicates = grouped.keys.groupingBy(::label).eachCount()
                grouped.map { (key, entries) ->
                    val title = label(key)
                    FinanceChartSlice(if ((duplicates[title] ?: 0) > 1) "$title • ${key.takeLast(6)}" else title,
                        entries.fold(0L) { sum, item -> Math.addExact(sum, FinancialDomain.amount(item)) })
                }
                    .sortedWith(compareByDescending<FinanceChartSlice> { it.amountMinor }.thenBy { it.label })
            }
            return FinanceChartData(points, groups)
        }
    }
}
