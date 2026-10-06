package app.veyra.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object LifeInsights {
    val actionable=setOf("task","checklist","chore","pet","medicine","appointment","exercise","meal","bill","packing","maintenance","exam")
    fun pending(items:List<Item>,today:LocalDate)=items.filter { it.deletedAt==0L && !it.done && it.type in actionable && it.date.isNotBlank() && it.date<=today.toString() }.sortedBy { it.date }
    fun daysUntil(item:Item,today:LocalDate)=ChronoUnit.DAYS.between(today,LocalDate.parse(item.date))
    fun progress(current:Long,total:Long):Float=if(total<=0)0f else (current.toDouble()/total).coerceIn(0.0,1.0).toFloat()
    fun remaining(item:Item,key:String)=Math.subtractExact(item.cents(),item.cents(key)).coerceAtLeast(0)
    /** Settling a bill creates one expense. The bill itself never enters cash flow. */
    fun settleBill(bill:Item,today:LocalDate):List<Item> {
        require(bill.type=="bill" && bill.deletedAt==0L && !bill.done)
        return listOf(bill.copy(done=true),Item(id="bill-payment:${bill.id}",type="expense",title=bill.title,notes=bill.notes,date=today.toString(),tags=bill.tags,parentId=bill.parentId,fields=bill.fields+mapOf("planned" to "Não","source" to bill.id)))
    }
    fun mealShopping(meal:Item)=meal.value("ingredients").lines().map(String::trim).filter(String::isNotBlank).distinct().mapIndexed { index,line ->
        Item(id="meal-shopping:${meal.id}:$index",type="shopping",title=line,date=meal.date,parentId=meal.id,fields=mapOf("quantity" to "1","category" to "Cardápio"))
    }
}
