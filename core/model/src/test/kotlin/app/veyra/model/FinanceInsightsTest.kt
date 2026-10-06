package app.veyra.model
import kotlin.test.*
import java.time.YearMonth
class FinanceInsightsTest {
    private fun transaction(type:String,amount:String,day:Int=1,planned:Boolean=false,done:Boolean=false,deleted:Boolean=false)=Item(type=type,title="Teste",date="2026-10-%02d".format(day),done=done,deletedAt=if(deleted)1 else 0,fields=mapOf("amount" to amount,"planned" to if(planned)"Sim"else "Não","category" to "Casa"))
    @Test fun separatesPaidAndPlannedWithoutCountingTransfers(){
        val items=listOf(transaction("income","1000"),transaction("expense","120",2),transaction("expense","80",3,planned=true),transaction("income","50",4,planned=true),transaction("expense","20",5,planned=true,done=true),transaction("transfer","999"),transaction("expense","999",deleted=true))
        val active=FinanceInsights.transactions(items,YearMonth.of(2026,10))
        assertEquals(86000L,Workspace.balance(active));assertEquals(83000L,FinanceInsights.projection(active));assertEquals(8000L,FinanceInsights.planned(active,"expense"));assertEquals(14000L,FinanceInsights.categories(active).single().value)
        val curve=FinanceInsights.dailyFlow(items,YearMonth.of(2026,10));assertEquals(31,curve.size);assertEquals(100000L,curve[0]);assertEquals(86000L,curve.last())
    }
    @Test fun zeroIncomeHasNoSavingsRate(){assertNull(FinanceInsights.savingsRate(emptyList()))}
}
