package app.veyra.model

import java.time.LocalDate
import java.util.UUID
import java.math.BigDecimal

data class Item(
    val id: String = UUID.randomUUID().toString(), val type: String, val title: String,
    val notes: String = "", val date: String = LocalDate.now().toString(),
    val done: Boolean = false, val favorite: Boolean = false, val tags: String = "",
    val parentId: String = "", val fields: Map<String,String> = emptyMap(),
    val deletedAt: Long = 0, val createdAt: Long = System.currentTimeMillis()
) {
    fun value(key: String) = fields[key].orEmpty()
    fun number(key: String) = value(key).toBigDecimalOrNull() ?: BigDecimal.ZERO
    fun cents(key: String = "amount") = if(value(key).isBlank()) 0 else Money.parseCents(value(key))
}
enum class FieldKind { TEXT, DECIMAL, MONEY, DATE, CHOICE, REFERENCE }
data class Field(val key: String, val label: String, val kind: FieldKind = FieldKind.TEXT,
    val choices: List<String> = emptyList(), val required: Boolean = false)
data class ItemSpec(val type: String, val label: String, val group: String,
    val fields: List<Field> = emptyList(), val checkable: Boolean = false)

object Workspace {
    fun validate(item: Item, spec: ItemSpec) {
        require(item.id.isNotBlank() && item.title.isNotBlank()) { "Preencha o título" }
        require(item.title.length<=200 && item.notes.length<=100_000) { "Texto muito longo" }
        if(item.date.isNotBlank()) LocalDate.parse(item.date)
        spec.fields.forEach { f ->
            val value=item.value(f.key)
            require(!f.required || value.isNotBlank()) { "Preencha ${f.label}" }
            if(value.isNotBlank()) when(f.kind) {
                FieldKind.MONEY -> require(f.key=="opening" || Money.parseCents(value)>=0) { "${f.label}: use valor positivo" }
                FieldKind.DECIMAL -> require(value.replace(',','.').toBigDecimalOrNull()!=null) { "${f.label}: número inválido" }
                FieldKind.DATE -> LocalDate.parse(value)
                FieldKind.CHOICE -> require(value in f.choices) { "${f.label}: opção inválida" }
                else -> Unit
            }
        }
        if(item.value("reminder").isNotBlank()){require(item.date.isNotBlank()){ "Defina uma data para o lembrete" };java.time.LocalTime.parse(item.value("reminder"))}
        for(key in listOf("liters","quantity","target","pages","progress","minutes","hours","odometer","weight"))if(item.value(key).isNotBlank())require(item.number(key).signum()>=0){"$key deve ser positivo"}
        if(item.type=="fuel")require(item.number("liters").signum()>0){"Litros devem ser maiores que zero"}
        if(item.type=="grade")require(item.number("grade") in BigDecimal.ZERO..BigDecimal.TEN){"Nota deve ficar entre 0 e 10"}
        if(item.type=="card")for(key in listOf("closing","due"))if(item.value(key).isNotBlank())require(item.number(key).toInt() in 1..31){"Dia deve ficar entre 1 e 31"}
        for(key in listOf("waist","height","sets","reps","load","stock","lessons","completed"))if(item.value(key).isNotBlank())require(item.number(key).signum()>=0){"$key deve ser positivo"}
        if(item.type=="savings_goal")require(item.cents("saved")<=item.cents()){ "O valor reservado não pode superar a meta" }
        if(item.type=="debt")require(item.cents("paid")<=item.cents()){ "O valor quitado não pode superar a dívida" }
        if(item.type=="course")require(item.number("completed")<=item.number("lessons")){"Aulas concluídas não podem superar o total"}
    }
    fun matches(item: Item, query: String): Boolean = listOf(item.title,item.notes,item.tags,item.type)
        .any { it.contains(query,true) }
    fun streak(checkins: List<Item>, habitId: String, today: LocalDate): Int {
        val dates=checkins.filter{it.type=="checkin" && it.parentId==habitId && it.deletedAt==0L}.map{it.date}.toSet()
        var date=if(today.toString() in dates) today else today.minusDays(1)
        var count=0; while(date.toString() in dates) { count++; date=date.minusDays(1) }; return count
    }
    fun monthly(items: List<Item>, month: String) = items.filter { it.deletedAt==0L && it.date.startsWith(month) }
    fun income(items: List<Item>) = items.filter{it.type=="income" && (it.value("planned")!="Sim" || it.done)}.fold(0L){a,i->Math.addExact(a,i.cents())}
    fun expenses(items: List<Item>) = items.filter{it.type=="expense" && (it.value("planned")!="Sim" || it.done)}.fold(0L){a,i->Math.addExact(a,i.cents())}
    fun balance(items: List<Item>) = Math.subtractExact(income(items),expenses(items))
    fun accountBalance(items: List<Item>, account: Item): Long {
        var balance=account.cents("opening")
        items.filter{it.deletedAt==0L && (it.value("planned")!="Sim" || it.done)}.forEach { i ->
            if(i.value("account")==account.id) when(i.type) {
                "income" -> balance=Math.addExact(balance,i.cents())
                "expense","transfer" -> balance=Math.subtractExact(balance,i.cents())
            }
            if(i.type=="transfer" && i.value("destination")==account.id) balance=Math.addExact(balance,i.cents())
        }; return balance
    }
    /** Scheduled templates create deterministic occurrences, never duplicate a payment. */
    fun recurring(templates: List<Item>, through: LocalDate): List<Item> = buildList {
        templates.filter{it.type=="subscription" && !it.done && it.deletedAt==0L}.forEach { template ->
            var date=LocalDate.parse(template.date)
            var count=0
            while(!date.isAfter(through) && count++ < 120) {
                add(Item(id="recurring:${template.id}:$date",type="expense",title=template.title,date=date.toString(),
                    parentId=template.parentId,fields=template.fields+mapOf("source" to template.id,"planned" to "Sim")))
                val next=java.time.YearMonth.from(LocalDate.parse(template.date)).plusMonths(count.toLong())
                date=next.atDay(LocalDate.parse(template.date).dayOfMonth.coerceAtMost(next.lengthOfMonth()))
            }
        }
    }
    fun installments(total: Long, count: Int): List<Long> {
        require(total>0 && count in 1..120)
        return List(count) { total/count + if(it<total%count) 1 else 0 }
    }
    fun shoppingTotal(items:List<Item>):Long = items.fold(0L){sum,item->Math.addExact(sum,BigDecimal.valueOf(item.cents()).multiply(item.number("quantity").let{if(it.signum()==0)BigDecimal.ONE else it}).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact())}
    fun weightedGrade(grades: List<Item>): BigDecimal {
        val weights=grades.fold(BigDecimal.ZERO){a,g->a+g.number("weight").let{if(it.signum()==0) BigDecimal.ONE else it}}
        if(weights.signum()==0) return BigDecimal.ZERO
        return grades.fold(BigDecimal.ZERO){a,g->a+g.number("grade")*g.number("weight").let{if(it.signum()==0) BigDecimal.ONE else it}}
            .divide(weights,2,java.math.RoundingMode.HALF_UP)
    }
}
