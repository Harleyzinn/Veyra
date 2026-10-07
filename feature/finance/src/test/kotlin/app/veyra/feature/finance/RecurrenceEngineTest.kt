package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate
import kotlin.test.*

class RecurrenceEngineTest {
    @Test fun permanentlyPurgedRuleIsIgnoredBeforeParsingItsRemovedDetails(){
        val purged=rule("2026-10-10").copy(deletedAt=1,fields=mapOf("purged" to "yes"))
        assertTrue(RecurrenceEngine.occurrences(listOf(purged),date("2026-10-01"),date("2026-11-30")).isEmpty())
        assertTrue(FinanceEngine.transactions(listOf(purged),date("2026-10-01"),date("2026-11-30")).isEmpty())
    }
    private fun applyChanges(original:List<Item>,changes:List<Item>)=original.filter{old->changes.none{it.id==old.id}}+changes
    @Test fun changingWholeSeriesCalendarReplacesMaterializedPendingWithoutDuplicating() {
        val original=rule("2026-10-10",extra=mapOf("dayOfMonth" to "10"))
        val pending=RecurrenceEngine.occurrence(original,date("2026-10-10")).copy(fields=RecurrenceEngine.occurrence(original,date("2026-10-10")).fields-"virtual")
        val changes=RecurrenceEngine.edit(original,date("2026-10-10"),original.copy(fields=original.fields+("dayOfMonth" to "15")),SeriesScope.ALL,listOf(pending))
        val rows=RecurrenceEngine.occurrences(applyChanges(listOf(original,pending),changes),date("2026-10-01"),date("2026-11-30"))
        assertEquals(listOf("2026-10-15","2026-11-15"),rows.map{it.date})
        assertTrue(changes.single{it.id==pending.id}.deletedAt>0)
    }
    @Test fun calendarChangePreservesPaidCycleAndStartsNewCalendarInNextCycle() {
        val original=rule("2026-10-10",extra=mapOf("dayOfMonth" to "10"))
        val paid=FinanceActions.settle(RecurrenceEngine.occurrence(original,date("2026-10-10")),date("2026-10-10"))
        val pending=RecurrenceEngine.occurrence(original,date("2026-11-10"))
        val changes=RecurrenceEngine.edit(original,date("2026-10-10"),original.copy(fields=original.fields+("dayOfMonth" to "5")),SeriesScope.ALL,listOf(paid,pending))
        val context=applyChanges(listOf(original,paid,pending),changes)
        val rows=FinanceEngine.transactions(context,date("2026-10-01"),date("2026-12-31"))
        assertEquals(listOf("2026-10-10","2026-11-05","2026-12-05"),rows.sortedBy{it.date}.map{it.date})
        assertEquals(paid,rows.single{it.id==paid.id})
    }
    @Test fun futureEditSuppressesSuccessorForAlreadyPaidFutureOccurrence() {
        val original=rule("2026-10-05")
        val paid=FinanceActions.settle(RecurrenceEngine.occurrence(original,date("2026-12-05")),date("2026-10-01"))
        val pending=RecurrenceEngine.occurrence(original,date("2026-11-05")).let{it.copy(fields=it.fields-"source")}
        val changes=RecurrenceEngine.edit(original,date("2026-11-05"),original.copy(fields=original.fields+("amount" to "4000")),SeriesScope.THIS_AND_FUTURE,listOf(paid,pending))
        val rows=FinanceEngine.transactions(applyChanges(listOf(original,paid,pending),changes),date("2026-10-01"),date("2026-12-31"))
        assertEquals(3,rows.size)
        assertEquals(paid,rows.single{it.date=="2026-12-05"})
        assertTrue(changes.single{it.id==pending.id}.deletedAt>0)
    }
    private fun date(text: String) = LocalDate.parse(text)
    private fun rule(start: String = "2024-01-31", frequency: String = "MONTHLY", extra: Map<String, String> = emptyMap()) =
        Item(id = "salary", type = "recurring_rule", title = "Salário", date = start, fields = mapOf("amount" to "3500", "frequency" to frequency, "transactionType" to "income") + extra)
    @Test fun monthEndKeepsAnchorAcrossLeapFebruaryAndShortMonths() {
        assertEquals(listOf("2024-01-31", "2024-02-29", "2024-03-31", "2024-04-30"),
            RecurrenceEngine.dates(rule(), date("2024-01-01"), date("2024-04-30")).map { it.toString() })
        assertEquals(listOf("2025-01-31", "2025-02-28", "2025-03-31"), RecurrenceEngine.dates(rule("2025-01-31"), date("2025-01-01"), date("2025-03-31")).map { it.toString() })
    }
    @Test fun yearBoundaryAndEverySupportedFrequency() {
        val start = "2026-12-31"
        val cases = mapOf("DAILY" to "2027-01-01", "WEEKLY" to "2027-01-07", "FORTNIGHTLY" to "2027-01-14",
            "MONTHLY" to "2027-01-31", "BIMONTHLY" to "2027-02-28", "QUARTERLY" to "2027-03-31", "SEMIANNUAL" to "2027-06-30", "YEARLY" to "2027-12-31")
        cases.forEach { (frequency, next) -> assertEquals(date(next), RecurrenceEngine.dates(rule(start, frequency, mapOf("occurrenceCount" to "2")), date(start), date("2027-12-31"))[1]) }
    }
    @Test fun infiniteRuleIsWindowBoundedEvenWhenDecadesOld() {
        val original = rule("1980-01-05").copy(createdAt = 123L)
        val records = RecurrenceEngine.occurrences(listOf(original), date("2026-10-01"), date("2026-12-31"))
        assertEquals(3, records.size)
        assertEquals("2026-10-05", records.first().date)
        assertEquals(records, RecurrenceEngine.occurrences(listOf(original), date("2026-10-01"), date("2026-12-31")))
        assertFails { RecurrenceEngine.dates(rule(), date("2024-01-01"), date("2040-01-01")) }
    }
    @Test fun pausedEndedAndCountLimitedRules() {
        val rule = rule("2026-01-05", extra = mapOf("occurrenceCount" to "3"))
        assertEquals(3, RecurrenceEngine.dates(rule, date("2026-01-01"), date("2026-12-31")).size)
        assertTrue(RecurrenceEngine.dates(RecurrenceEngine.pause(rule, true, date("2026-01-01")), date("2026-01-01"), date("2026-12-31")).isEmpty())
        assertEquals(2, RecurrenceEngine.dates(rule.copy(fields = rule.fields + ("endDate" to "2026-02-05")), date("2026-01-01"), date("2026-12-31")).size)
        assertEquals(3, RecurrenceEngine.dates(RecurrenceEngine.pause(RecurrenceEngine.pause(rule, true, date("2026-01-01")), false, date("2026-01-01")), date("2026-01-01"), date("2026-12-31")).size)
    }
    @Test fun weekdaysAndLastBusinessDayAreCalculatedFromCalendar() {
        val weekly = rule("2026-10-05", "WEEKLY", mapOf("weekdays" to "1,3,5"))
        assertEquals(listOf("2026-10-05", "2026-10-07", "2026-10-09"), RecurrenceEngine.dates(weekly, date("2026-10-01"), date("2026-10-11")).map { it.toString() })
        val business = rule("2026-10-01", extra = mapOf("lastBusinessDay" to "yes", "dayOfMonth" to "31"))
        assertEquals(listOf("2026-10-30", "2026-11-30"), RecurrenceEngine.dates(business, date("2026-10-01"), date("2026-11-30")).map { it.toString() })
    }
    @Test fun customIntervalsAndCountsDoNotResetAtWindowStart() {
        val custom = rule("2026-01-01", "CUSTOM", mapOf("customUnit" to "weeks", "interval" to "2", "occurrenceCount" to "4"))
        assertEquals(listOf("2026-01-29", "2026-02-12"), RecurrenceEngine.dates(custom, date("2026-01-20"), date("2026-03-31")).map { it.toString() })
    }
    @Test fun occurrenceEditsOverrideExactlyOneAndSettlingNeverDuplicates() {
        val rule = rule("2026-10-05")
        val original = RecurrenceEngine.occurrence(rule, date("2026-11-05"))
        val edit = RecurrenceEngine.edit(rule, date("2026-11-05"), original.copy(fields = original.fields - "amountMinor" + ("amount" to "4000")), SeriesScope.THIS)
        val rows = RecurrenceEngine.occurrences(listOf(rule) + edit, date("2026-10-01"), date("2026-12-31"))
        assertEquals(listOf(350000L, 400000L, 350000L), rows.map(FinancialDomain::amount))
        assertEquals(3, RecurrenceEngine.occurrences(listOf(rule) + edit.map { FinanceActions.settle(it, date("2026-11-05")) }, date("2026-10-01"), date("2026-12-31")).size)
    }
    @Test fun oneDeletionSuppressesOnlyItsDeterministicOccurrence() {
        val rule = rule("2026-10-05")
        val items = listOf(rule) + RecurrenceEngine.delete(rule, date("2026-11-05"), SeriesScope.THIS)
        assertEquals(listOf("2026-10-05", "2026-12-05"), RecurrenceEngine.occurrences(items, date("2026-10-01"), date("2026-12-31")).map { it.date })
    }
    @Test fun editingThisAndFutureSplitsRuleWithoutChangingEarlierPayment() {
        val rule = rule("2026-10-05")
        val paid = FinanceActions.settle(RecurrenceEngine.occurrence(rule, date("2026-10-05")), date("2026-10-05"))
        val changes = RecurrenceEngine.edit(rule, date("2026-11-05"), rule.copy(fields = rule.fields + ("amount" to "4000")), SeriesScope.THIS_AND_FUTURE, listOf(paid))
        val items = listOf(paid) + changes
        val rows = RecurrenceEngine.occurrences(items, date("2026-10-01"), date("2026-12-31"))
        assertEquals(listOf(350000L, 400000L, 400000L), rows.map(FinancialDomain::amount))
        assertEquals(paid, rows.first())
    }
    @Test fun futureAndWholeSeriesDeletionRetainSettledRecords() {
        val rule = rule("2026-10-05")
        assertEquals(1, RecurrenceEngine.occurrences(RecurrenceEngine.delete(rule, date("2026-11-05"), SeriesScope.THIS_AND_FUTURE), date("2026-10-01"), date("2026-12-31")).size)
        val paid = FinanceActions.settle(RecurrenceEngine.occurrence(rule, date("2026-10-05")), date("2026-10-05"))
        val changes = RecurrenceEngine.delete(rule, date("2026-11-05"), SeriesScope.ALL, listOf(paid))
        assertTrue(changes.none { it.id == paid.id })
        assertTrue(RecurrenceEngine.occurrences(changes, date("2026-10-01"), date("2026-12-31")).isEmpty())
    }
    @Test fun legacySubscriptionKeepsExistingDeterministicIds() {
        val old = Item(id = "internet", type = "subscription", title = "Internet", date = "2026-10-10", fields = mapOf("amount" to "119.90"))
        val paid = Item(id = "recurring:internet:2026-10-10", type = "expense", title = "Internet", date = "2026-10-10", done = true, fields = old.fields + mapOf("source" to old.id, "planned" to "Sim"))
        val rows = RecurrenceEngine.occurrences(listOf(old, paid), date("2026-10-01"), date("2026-11-30"))
        assertEquals(2, rows.size)
        assertEquals(paid, rows.first())
    }
    @Test fun countStartsAtFirstValidDayAfterStartNotAtAnImpossibleEarlierAnchor() {
        val rule = rule("2026-10-06", extra = mapOf("dayOfMonth" to "5", "occurrenceCount" to "1"))
        assertEquals(listOf("2026-11-05"), RecurrenceEngine.dates(rule, date("2026-10-01"), date("2026-12-31")).map { it.toString() })
    }
    @Test fun editingWholeSeriesLeavesSettledOriginalAndPendingStateIntact() {
        val rule = FinancialDomain.normalize(rule("2026-10-05"))
        val paid = FinanceActions.settle(RecurrenceEngine.occurrence(rule, date("2026-10-05")), date("2026-10-05"))
        val pending = RecurrenceEngine.occurrence(rule, date("2026-11-05")).copy(fields = RecurrenceEngine.occurrence(rule, date("2026-11-05")).fields - "virtual")
        val changes = RecurrenceEngine.edit(rule, date("2026-11-05"), rule.copy(fields = rule.fields - "amountMinor" + ("amount" to "4000")), SeriesScope.ALL, listOf(paid, pending))
        assertTrue(changes.none { it.id == paid.id })
        val rows = RecurrenceEngine.occurrences(listOf(paid) + changes, date("2026-10-01"), date("2026-12-31"))
        assertEquals(listOf(350000L, 400000L, 400000L), rows.map(FinancialDomain::amount))
        assertFalse(FinancialDomain.settled(rows[1]))
        assertEquals(paid, rows.first())
    }
    @Test fun futureSplitRetainsRemainingCountEvenForVeryOldSeries() {
        val rule = rule("2000-01-05", extra = mapOf("occurrenceCount" to "400"))
        val changes = RecurrenceEngine.edit(rule, date("2026-01-05"), rule, SeriesScope.THIS_AND_FUTURE)
        assertEquals("88", changes.last().value("occurrenceCount"))
        assertEquals(3, RecurrenceEngine.occurrences(changes, date("2026-01-01"), date("2026-03-31")).size)
    }
    @Test fun pauseKeepsPastDueAndResumeDoesNotRegenerateChargesDuringThePause() {
        val rule = rule("2026-10-05")
        val paused = RecurrenceEngine.pause(rule, true, date("2026-10-06"))
        assertEquals(listOf("2026-10-05"), RecurrenceEngine.dates(paused, date("2026-10-01"), date("2026-12-31")).map { it.toString() })
        val resumed = RecurrenceEngine.pause(paused, false, date("2026-11-06"))
        assertEquals(listOf("2026-10-05", "2026-12-05"), RecurrenceEngine.dates(resumed, date("2026-10-01"), date("2026-12-31")).map { it.toString() })
    }
    @Test fun pausedSlotsDoNotConsumeAnExplicitOccurrenceCount() {
        val rule = rule("2026-01-31", extra = mapOf("occurrenceCount" to "3"))
        val paused = RecurrenceEngine.pause(rule, true, date("2026-02-01"))
        val resumed = RecurrenceEngine.pause(paused, false, date("2026-04-01"))
        assertEquals(listOf("2026-01-31", "2026-04-30", "2026-05-31"), RecurrenceEngine.dates(resumed, date("2026-01-01"), date("2026-12-31")).map { it.toString() })
    }
}
