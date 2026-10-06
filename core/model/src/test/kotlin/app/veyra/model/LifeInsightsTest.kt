package app.veyra.model

import kotlin.test.*

import java.time.LocalDate

class LifeInsightsTest {
    private val today=LocalDate.of(2026,10,6)
    @Test fun pendingIgnoresCompletedDeletedFutureAndNonActions(){
        val task=Item(type="chore",title="Limpar",date=today.toString())
        val items=listOf(task,task.copy(id="done",done=true),task.copy(id="deleted",deletedAt=1),task.copy(id="future",date=today.plusDays(1).toString()),task.copy(id="note",type="note"))
        assertEquals(listOf(task),LifeInsights.pending(items,today))
    }
    @Test fun billCreatesExactExpenseAndPreservesAccount(){
        val bill=Item(id="internet",type="bill",title="Internet",fields=mapOf("amount" to "99.90","account" to "wallet"))
        val changes=LifeInsights.settleBill(bill,today)
        assertTrue(changes.first().done);assertEquals("bill-payment:internet",changes.last().id)
        assertEquals("wallet",changes.last().value("account"));assertEquals(9990L,Workspace.expenses(changes))
        assertEquals(today.toString(),changes.last().date)
    }
    @Test fun settledBillCannotSettleAgain(){assertFailsWith<IllegalArgumentException>{LifeInsights.settleBill(Item(type="bill",title="Pago",done=true),today)}}
    @Test fun shoppingNormalizesAndHasStableIds(){
        val meal=Item(type="meal",title="Salada",fields=mapOf("ingredients" to " Tomate \n\nAlface\nTomate"))
        val first=LifeInsights.mealShopping(meal);assertEquals(listOf("Tomate","Alface"),first.map{it.title});assertEquals(first.map{it.id},LifeInsights.mealShopping(meal).map{it.id})
    }
    @Test fun countdownAndProgressAreBounded(){
        assertEquals(2L,LifeInsights.daysUntil(Item(type="countdown",title="Viagem",date="2026-10-08"),today))
        assertEquals(1f,LifeInsights.progress(110,100));assertEquals(0f,LifeInsights.progress(5,0))
        assertEquals(7500L,LifeInsights.remaining(Item(type="debt",title="Dívida",fields=mapOf("amount" to "100","paid" to "25")),"paid"))
    }
}
