package app.veyra.model
import kotlin.test.*
import java.time.LocalDate
class WorkspaceTest {
    @Test fun transferNeverCreatesIncomeAndInstallmentsPreserveCents(){
        val a=Item(id="a",type="account",title="A",fields=mapOf("opening" to "100"));val b=Item(id="b",type="account",title="B")
        val transfer=Item(type="transfer",title="Mover",fields=mapOf("amount" to "20","account" to "a","destination" to "b"))
        assertEquals(8000L,Workspace.accountBalance(listOf(transfer),a));assertEquals(2000L,Workspace.accountBalance(listOf(transfer),b));assertEquals(0L,Workspace.income(listOf(transfer)))
        assertEquals(listOf(334L,333L,333L),Workspace.installments(1000,3))
    }
    @Test fun plannedExpenseIsExcludedUntilPaid(){val planned=Item(type="expense",title="Conta",fields=mapOf("amount" to "50","planned" to "Sim"));assertEquals(0L,Workspace.expenses(listOf(planned)));assertEquals(5000L,Workspace.expenses(listOf(planned.copy(done=true))))}
    @Test fun dailyCheckinStreakHandlesYesterdayAndBreaks(){val day=LocalDate.of(2026,10,5);val checks=listOf("2026-10-04","2026-10-03").map{Item(type="checkin",title="Hábito",parentId="h",date=it)};assertEquals(2,Workspace.streak(checks,"h",day));assertEquals(0,Workspace.streak(checks,"h",day.plusDays(1)))}
    @Test fun calculatorAndConversionsAreMathematical(){assertEquals(-4.0,Toolbox.calculate("-2^2"));assertEquals(14.0,Toolbox.calculate("2+3*4"));assertEquals(100.0,Toolbox.convert(1.0,"m","cm"));assertEquals(32.0,Toolbox.convert(0.0,"°C","°F"));assertFails{Toolbox.convert(1.0,"kg","m")}}
    @Test fun recurringIdsAndEndOfMonthAreStable(){val t=Item(id="s",type="subscription",title="Internet",date="2026-01-31",fields=mapOf("amount" to "100"));val records=Workspace.recurring(listOf(t),LocalDate.of(2026,3,31));assertEquals(3,records.size);assertEquals(records,Workspace.recurring(listOf(t),LocalDate.of(2026,3,31)).mapIndexed{n,i->i.copy(createdAt=records[n].createdAt)})}
}
