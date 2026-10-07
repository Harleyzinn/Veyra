package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class SeriesScope(val label: String) { THIS("Somente esta"), THIS_AND_FUTURE("Esta e próximas"), ALL("Toda a série") }
enum class RecurrenceFrequency(val label: String, val unit: String, val step: Int) {
    DAILY("Diária", "days", 1), WEEKLY("Semanal", "days", 7), FORTNIGHTLY("Quinzenal", "days", 14),
    MONTHLY("Mensal", "months", 1), BIMONTHLY("Bimestral", "months", 2), QUARTERLY("Trimestral", "months", 3),
    SEMIANNUAL("Semestral", "months", 6), YEARLY("Anual", "months", 12), CUSTOM("Personalizada", "days", 1)
}

/** Rules are expanded only in the requested window. No database writes are needed for a forecast. */
object RecurrenceEngine {
    private fun mergeFields(base: Map<String, String>, replacement: Map<String, String>): Map<String, String> {
        val clean = base.toMutableMap()
        for (key in listOf("amount", "opening", "limit", "current", "saved", "paid", "payment")) {
            val minorKey = if (key == "amount") "amountMinor" else "${key}Minor"
            if (key in replacement && replacement[minorKey].isNullOrBlank()) clean.remove(minorKey)
        }
        return clean + replacement
    }
    fun isRule(item: Item) = item.type in setOf("recurring_rule", "subscription")
    fun frequency(rule: Item) = RecurrenceFrequency.entries.firstOrNull {
        rule.value("frequency").equals(it.name, true) || rule.value("frequency").equals(it.label, true)
    } ?: RecurrenceFrequency.MONTHLY
    private fun start(rule: Item) = LocalDate.parse(rule.value("startDate").ifBlank { rule.date })
    private fun end(rule: Item) = rule.value("endDate").takeIf(String::isNotBlank)?.let(LocalDate::parse)
    private fun paused(rule: Item) = rule.done || rule.value("paused") in setOf("yes", "Sim", "true")
    private fun pauseWindows(rule: Item): List<Pair<LocalDate, LocalDate>> {
        require(rule.value("pauseWindows").length <= 10000) { "O histórico de pausas excede o limite suportado" }
        val windows = rule.value("pauseWindows").split(';').filter(String::isNotBlank).map { text ->
        val parts = text.split('/')
        require(parts.size == 2) { "Histórico de pausas inválido" }
        val start = LocalDate.parse(parts[0]); val end = LocalDate.parse(parts[1])
        require(!end.isBefore(start)) { "Histórico de pausas inválido" }
        start to end
        }.sortedBy { it.first }
        val merged = mutableListOf<Pair<LocalDate, LocalDate>>()
        windows.forEach { range ->
            val last = merged.lastOrNull()
            if (last != null && !range.first.isAfter(last.second.plusDays(1))) merged[merged.lastIndex] = last.first to maxOf(last.second, range.second)
            else merged += range
        }
        return merged
    }
    private fun step(rule: Item): Pair<String, Int> {
        val frequency = frequency(rule)
        val multiplier = rule.value("interval").toIntOrNull() ?: 1
        require(multiplier in 1..365) { "Intervalo de recorrência inválido" }
        val unit = if (frequency == RecurrenceFrequency.CUSTOM) rule.value("customUnit").ifBlank { "days" } else frequency.unit
        require(unit in setOf("days", "weeks", "months", "years")) { "Unidade de recorrência inválida" }
        return when (unit) {
            "weeks" -> "days" to Math.multiplyExact(multiplier, 7)
            "years" -> "months" to Math.multiplyExact(multiplier, 12)
            else -> unit to Math.multiplyExact(multiplier, frequency.step)
        }
    }
    fun validate(rule: Item) {
        require(isRule(rule))
        require(rule.value("frequency").isBlank() || RecurrenceFrequency.entries.any { rule.value("frequency").equals(it.name, true) || rule.value("frequency").equals(it.label, true) }) { "Frequência de recorrência inválida" }
        val start = start(rule)
        require(end(rule)?.isBefore(start) != true) { "O fim precisa ser posterior ao início" }
        step(rule)
        if (rule.value("occurrenceCount").isNotBlank()) require(rule.value("occurrenceCount").toIntOrNull() in 1..10000) { "Use de 1 a 10 mil ocorrências" }
        if (rule.value("dayOfMonth").isNotBlank()) require(rule.value("dayOfMonth").toIntOrNull() in 1..31) { "Dia deve ficar entre 1 e 31" }
        weekdays(rule)
        pauseWindows(rule)
        if (rule.value("pausedAt").isNotBlank()) LocalDate.parse(rule.value("pausedAt"))
        require(rule.value("transactionType").ifBlank { "expense" } in setOf("income", "expense"))
        require(FinancialDomain.amount(rule) > 0) { "Defina o valor da recorrência" }
    }
    private fun weekdays(rule: Item): Set<Int> = rule.value("weekdays").split(',').filter(String::isNotBlank).map {
        it.trim().toInt().also { n -> require(n in 1..7) { "Dias da semana: use 1 a 7" } }
    }.toSet()
    private fun lastBusiness(date: LocalDate): LocalDate {
        var day = YearMonth.from(date).atEndOfMonth()
        while (day.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) day = day.minusDays(1)
        return day
    }
    private fun candidate(rule: Item, start: LocalDate, unit: String, interval: Int, index: Long): LocalDate {
        return if (unit == "months") {
            val month = YearMonth.from(start).plusMonths(Math.multiplyExact(index, interval.toLong()))
            val day = month.atDay((rule.value("dayOfMonth").toIntOrNull() ?: start.dayOfMonth).coerceAtMost(month.lengthOfMonth()))
            if (rule.value("lastBusinessDay") in setOf("yes", "Sim", "true")) lastBusiness(day) else day
        } else start.plusDays(Math.multiplyExact(index, interval.toLong()))
    }
    fun dates(rule: Item, from: LocalDate, through: LocalDate): List<LocalDate> {
        require(!through.isBefore(from) && ChronoUnit.DAYS.between(from, through) <= 3660) { "Use uma janela de até dez anos" }
        validate(rule)
        val start = start(rule)
        val pauseDate = if (paused(rule)) rule.value("pausedAt").takeIf(String::isNotBlank)?.let(LocalDate::parse) else null
        val stop = minOf(through, end(rule) ?: through, pauseDate?.minusDays(1) ?: through)
        if (stop.isBefore(from) || stop.isBefore(start) || rule.deletedAt != 0L || paused(rule) && pauseDate == null) return emptyList()
        val pauseWindows = pauseWindows(rule)
        fun inPause(date: LocalDate) = pauseWindows.any { !date.isBefore(it.first) && !date.isAfter(it.second) }
        val (unit, interval) = step(rule)
        val selectedWeekdays = weekdays(rule)
        val countLimit = rule.value("occurrenceCount").toIntOrNull()
        val output = mutableListOf<LocalDate>()
        if (selectedWeekdays.isNotEmpty() && frequency(rule) in setOf(RecurrenceFrequency.DAILY, RecurrenceFrequency.WEEKLY)) {
            val first = maxOf(start, from)
            var day = if (countLimit != null) start else first
            var accepted = 0
            var inspected = 0
            while (!day.isAfter(stop)) {
                require(inspected++ < 100000) { "Regra ultrapassa o intervalo suportado" }
                val week = ChronoUnit.WEEKS.between(start.with(DayOfWeek.MONDAY), day.with(DayOfWeek.MONDAY))
                val matching = day.dayOfWeek.value in selectedWeekdays && if (frequency(rule) == RecurrenceFrequency.WEEKLY) week % (interval / 7) == 0L else ChronoUnit.DAYS.between(start, day) % interval == 0L
                if (matching && !inPause(day)) {
                    if (countLimit != null && accepted >= countLimit) break
                    accepted++
                    if (!day.isBefore(from)) output += day
                }
                day = day.plusDays(1)
            }
            return output
        }
        var index = if (unit == "months") (ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(from)) / interval).coerceAtLeast(0)
            else (ChronoUnit.DAYS.between(start, from) / interval).coerceAtLeast(0)
        val firstIndex = if (candidate(rule, start, unit, interval, 0).isBefore(start)) 1L else 0L
        index = (index - 1).coerceAtLeast(firstIndex)
        var accepted = if (countLimit == null) 0 else countBefore(rule, candidate(rule, start, unit, interval, index))
        while (true) {
            val day = candidate(rule, start, unit, interval, index)
            if (day.isAfter(stop)) break
            if (!day.isBefore(start) && !inPause(day)) {
                if (countLimit != null && accepted >= countLimit) break
                if (!day.isBefore(from)) output += day
                accepted++
            }
            require(output.size <= 10000) { "A janela contém mais de 10 mil ocorrências" }
            index++
        }
        return output.distinct()
    }
    private fun countBefore(rule: Item, before: LocalDate): Int {
        val scheduled = scheduledBefore(rule, before)
        val skipped = pauseWindows(rule).filter { it.first.isBefore(before) }.sumOf { range ->
            scheduledBefore(rule, minOf(before, range.second.plusDays(1))) - scheduledBefore(rule, range.first)
        }
        return (scheduled - skipped).coerceAtLeast(0)
    }
    private fun scheduledBefore(rule: Item, before: LocalDate): Int {
        val start = start(rule)
        if (!before.isAfter(start)) return 0
        val (unit, interval) = step(rule)
        val selected = weekdays(rule)
        if (selected.isNotEmpty() && frequency(rule) in setOf(RecurrenceFrequency.DAILY, RecurrenceFrequency.WEEKLY)) {
            var date = start
            var count = 0
            var inspected = 0
            while (date.isBefore(before)) {
                require(inspected++ < 100000) { "A série ultrapassa o intervalo suportado" }
                val week = ChronoUnit.WEEKS.between(start.with(DayOfWeek.MONDAY), date.with(DayOfWeek.MONDAY))
                if (date.dayOfWeek.value in selected && if (frequency(rule) == RecurrenceFrequency.WEEKLY) week % (interval / 7) == 0L else ChronoUnit.DAYS.between(start, date) % interval == 0L) count++
                date = date.plusDays(1)
            }
            return count
        }
        val firstIndex = if (candidate(rule, start, unit, interval, 0).isBefore(start)) 1L else 0L
        var index = if (unit == "months") (ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(before)) / interval).coerceAtLeast(firstIndex)
            else (ChronoUnit.DAYS.between(start, before) / interval).coerceAtLeast(firstIndex)
        while (candidate(rule, start, unit, interval, index).isBefore(before)) index++
        while (index > firstIndex && !candidate(rule, start, unit, interval, index - 1).isBefore(before)) index--
        return (index - firstIndex).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    fun occurrence(rule: Item, date: LocalDate): Item {
        val fields = rule.fields - setOf("endDate", "occurrenceCount", "paused", "startDate") + mapOf(
            "source" to rule.id, "recurrenceRuleId" to rule.id, "occurrenceDate" to date.toString(),
            "status" to if (rule.value("transactionType") == "income") "expected" else "pending",
            "planned" to "Sim", "dueDate" to date.toString(), "virtual" to "yes", "recurrence" to "no")
        return Item(id = "recurring:${rule.id}:$date", type = rule.value("transactionType").ifBlank { "expense" },
            title = rule.title, notes = rule.notes, date = date.toString(), tags = rule.tags, parentId = rule.parentId,
            fields = fields, createdAt = rule.createdAt)
    }
    fun occurrences(items: List<Item>, from: LocalDate, through: LocalDate): List<Item> {
        val overrides = items.filter { !isRule(it) && (it.value("recurrenceRuleId").isNotBlank() || it.value("source").isNotBlank() || it.id.startsWith("recurring:")) }
            .associateBy { it.id }
        return items.filter { isRule(it) && it.deletedAt==0L }.flatMap { rule -> dates(rule, from, through).mapNotNull { date ->
            val virtual = occurrence(rule, date)
            val override = overrides[virtual.id]
            when {
                override == null -> virtual
                override.deletedAt != 0L || override.type == "recurrence_exception" || !FinancialDomain.active(override) -> null
                else -> override
            }
        } }.distinctBy { it.id }
    }
    fun pause(rule: Item, paused: Boolean, at: LocalDate = LocalDate.now()): Item {
        if (paused) return if (this.paused(rule) && rule.value("pausedAt").isNotBlank()) rule else rule.copy(done = true, fields = rule.fields + mapOf("paused" to "yes", "pausedAt" to at.toString()))
        val began = rule.value("pausedAt").takeIf(String::isNotBlank)?.let(LocalDate::parse)
        val window = if (began != null && began.isBefore(at)) "$began/${at.minusDays(1)}" else ""
        val windows = (rule.value("pauseWindows").split(';').filter(String::isNotBlank) + listOf(window).filter(String::isNotBlank)).distinct().joinToString(";")
        return rule.copy(done = false, fields = rule.fields - "pausedAt" + mapOf("paused" to "no", "pauseWindows" to windows))
    }
    fun edit(rule: Item, at: LocalDate, replacement: Item, scope: SeriesScope, items: List<Item> = emptyList()): List<Item> {
        require(isRule(rule))
        if (scope == SeriesScope.THIS) {
            val original = occurrence(rule, at)
            val changed = FinancialDomain.normalize(replacement.copy(id = original.id, type = replacement.type.takeIf { it in setOf("income", "expense") } ?: original.type, date = replacement.date.ifBlank { at.toString() },
                fields = mergeFields(original.fields, replacement.fields) + mapOf("source" to rule.id, "recurrenceRuleId" to rule.id, "occurrenceDate" to at.toString(), "virtual" to "no")))
            FinancialDomain.validate(changed, items)
            return listOf(changed)
        }
        val common = FinancialDomain.normalize(rule.copy(title = replacement.title, notes = replacement.notes, tags = replacement.tags,
            fields = mergeFields(rule.fields, replacement.fields) - setOf("virtual", "source", "recurrenceRuleId", "occurrenceDate") +
                (replacement.type.takeIf { it in setOf("income", "expense") }?.let { mapOf("transactionType" to it) } ?: emptyMap())))
        validate(common)
        fun linked(item:Item)=item.value("source")==rule.id || item.value("recurrenceRuleId")==rule.id
        fun anchor(item:Item)=LocalDate.parse(item.value("occurrenceDate").ifBlank{item.date})
        val members=items.distinctBy{it.id}.filter{linked(it) && it.deletedAt==0L}
        val settled=members.filter{FinancialDomain.settled(it)}
        val scheduleKeys=listOf("frequency","customUnit","interval","dayOfMonth","weekdays","lastBusinessDay","startDate")
        val rephaseChanged=scheduleKeys.any{rule.value(it)!=common.value(it)}
        val scheduleChanged=rephaseChanged || listOf("endDate","occurrenceCount").any{rule.value(it)!=common.value(it)}
        var splitAt:LocalDate?=if(scope==SeriesScope.THIS_AND_FUTURE)at else null
        if(rephaseChanged && settled.isNotEmpty()){
            val lastPaid=settled.maxOf(::anchor)
            if(splitAt==null || !lastPaid.isBefore(splitAt)){
                val next=dates(rule,lastPaid.plusDays(1),lastPaid.plusYears(10)).firstOrNull()
                    ?: error("A série já foi concluída. Crie uma nova recorrência para os próximos pagamentos.")
                splitAt=if(step(rule).first=="months")next.withDayOfMonth(1)else next
            }
        }
        val changed = if (splitAt==null) listOf(common) else {
            val boundary=splitAt
            val remaining = rule.value("occurrenceCount").toIntOrNull()?.let { count ->
                (count - countBefore(rule, boundary)).also { require(it > 0) { "Esta série já chegou ao fim da quantidade de ocorrências" } }
            }
            listOf(if (!boundary.isAfter(start(rule))) rule.copy(deletedAt = System.currentTimeMillis()) else rule.copy(fields = rule.fields + ("endDate" to boundary.minusDays(1).toString())), common.copy(
                id = "${rule.id}:from:$boundary", date = boundary.toString(), fields = common.fields + ("startDate" to boundary.toString()) +
                    (remaining?.let { mapOf("occurrenceCount" to it.toString()) } ?: emptyMap())))
        }
        val pending = members.filter { !FinancialDomain.settled(it) && (splitAt==null || !anchor(it).isBefore(splitAt)) }
        changed.filter { it.deletedAt == 0L }.forEach(::validate)
        val preservedPayments=if(splitAt==null)emptyList()else{
            val successor=changed.last()
            settled.filter{anchor(it) in dates(successor,anchor(it),anchor(it))}.map{paid->
                val date=anchor(paid)
                Item(id="recurring:${successor.id}:$date",type=paid.type,title="Registro excluído",date=date.toString(),deletedAt=System.currentTimeMillis(),
                    fields=mapOf("source" to successor.id,"recurrenceRuleId" to successor.id,"occurrenceDate" to date.toString(),"purged" to "yes"))
            }
        }
        return changed + pending.map { old ->
            if (splitAt==null && (!scheduleChanged || anchor(old) in dates(common,anchor(old),anchor(old)))) old.copy(title = common.title, notes = common.notes, tags=common.tags, fields = old.fields + common.fields +
                mapOf("source" to rule.id, "recurrenceRuleId" to rule.id, "occurrenceDate" to old.value("occurrenceDate").ifBlank { old.date }, "dueDate" to old.value("dueDate").ifBlank { old.date }, "status" to old.value("status"), "planned" to old.value("planned")))
            else old.copy(deletedAt = System.currentTimeMillis())
        } + preservedPayments
    }
    fun delete(rule: Item, at: LocalDate, scope: SeriesScope, items: List<Item> = emptyList()): List<Item> = when (scope) {
        SeriesScope.THIS -> listOf((items.firstOrNull { it.id == "recurring:${rule.id}:$at" } ?: occurrence(rule, at))
            .let { it.copy(deletedAt = System.currentTimeMillis(), fields = it.fields + mapOf("virtual" to "no", "trashGroup" to it.id)) })
        SeriesScope.THIS_AND_FUTURE -> if(!at.isAfter(start(rule)))FinanceActions.trash(rule,items)else listOf(rule.copy(fields = rule.fields + ("endDate" to at.minusDays(1).toString()))) +
            items.distinctBy{it.id}.filter { (it.value("source") == rule.id || it.value("recurrenceRuleId")==rule.id) && it.deletedAt==0L && !FinancialDomain.settled(it) && it.value("occurrenceDate").ifBlank{it.date} >= at.toString() }.map { it.copy(deletedAt = System.currentTimeMillis()) }
        SeriesScope.ALL -> FinanceActions.trash(rule,items)
    }
}
