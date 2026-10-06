package app.veyra.model

import java.time.YearMonth

/** All financial summaries use integer cents and exclude soft-deleted entries. */
object FinanceInsights {
    fun transactions(items:List<Item>,month:YearMonth)=items.filter{it.deletedAt==0L && it.type in setOf("income","expense","transfer") && it.date.startsWith(month.toString())}
    fun planned(items:List<Item>,type:String)=items.filter{it.deletedAt==0L && it.type==type && it.value("planned")=="Sim" && !it.done}.fold(0L){sum,i->Math.addExact(sum,i.cents())}
    fun categories(items:List<Item>)=items.filter{it.deletedAt==0L && it.type=="expense" && (it.value("planned")!="Sim" || it.done)}.groupBy{it.value("category").ifBlank{"Sem categoria"}}.mapValues{Workspace.expenses(it.value)}.entries.filter{it.value>0}.sortedByDescending{it.value}
    fun dailyFlow(items:List<Item>,month:YearMonth):List<Long>{
        val monthItems=transactions(items,month)
        var total=0L
        return (1..month.lengthOfMonth()).map{day->total=Math.addExact(total,Workspace.balance(monthItems.filter{it.date==month.atDay(day).toString()}));total}
    }
    fun projection(items:List<Item>):Long{val active=items.filter{it.deletedAt==0L};return Math.subtractExact(Math.addExact(Workspace.balance(active),planned(active,"income")),planned(active,"expense"))}
    fun savingsRate(items:List<Item>):Double?{val active=items.filter{it.deletedAt==0L};val income=Workspace.income(active);return if(income==0L)null else Workspace.balance(active).toDouble()/income*100}
}
