package app.veyra.android

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.widget.RemoteViews
import app.veyra.data.WorkspaceIdentity
import app.veyra.data.WorkspaceStore
import app.veyra.feature.finance.*
import app.veyra.model.Item
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.Locale

internal data class TodayWidgetContent(val title:String,val body:String,val financial:Boolean=false)
internal object TodayWidgetSnapshot {
    fun render(items:List<Item>,preferences:Map<String,String>,today:LocalDate=LocalDate.now()):TodayWidgetContent {
        if(!preferences["lock"].isNullOrBlank())return TodayWidgetContent("VEYRA • protegido","Abra o aplicativo para consultar seu espaço.")
        if(preferences["widgetFinance"]=="Sim"){
            if(!FinancePrivacyPolicy.widgetDetails(preferences))return TodayWidgetContent("VEYRA • finanças protegidas","Abra o financeiro para consultar seu resumo.",true)
            val currency=preferences["financeCurrency"] ?: "BRL"
            val summary=FinanceEngine.summary(items,YearMonth.from(today),today,currency,(preferences["financialDay"]?.toIntOrNull() ?: 1).coerceIn(1,31))
            fun money(amount:Long)=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).apply{this.currency=Currency.getInstance(currency)}.format(BigDecimal.valueOf(amount,FinancialDomain.scale(currency)))
            val upcoming=FinanceEngine.transactions(items,today,today.plusDays(90),currency).filter{
                it.type=="expense" && !FinancialDomain.settled(it) && it.value("card").isBlank() && !FinancialDomain.dueDate(it).isBefore(today)
            }.minByOrNull(FinancialDomain::dueDate)
            val invoice=items.filter{it.type=="card" && it.deletedAt==0L && FinancialDomain.currency(it)==currency}.flatMap{CardEngine.invoices(items,it,today,today.plusDays(90))}.filter{it.outstandingMinor>0}.minByOrNull{it.dueDate}
            val nextDate=upcoming?.let(FinancialDomain::dueDate)
            val next=if(invoice!=null && (nextDate==null || !invoice.dueDate.isAfter(nextDate)))"${invoice.dueDate.dayOfMonth}/${invoice.dueDate.monthValue} • ${money(invoice.outstandingMinor)}"else if(upcoming!=null)"${nextDate!!.dayOfMonth}/${nextDate.monthValue} • ${money(FinancialDomain.amount(upcoming))}"else"Nenhuma em 90 dias"
            return TodayWidgetContent("VEYRA • finanças","Saldo ${money(summary.currentBalance)}\nGastos do mês ${money(summary.expenseMinor)}\nPróxima conta: $next",true)
        }
        val tasks=items.filter{it.type=="task" && it.deletedAt==0L && !it.done && it.date<=today.toString()}.sortedBy{it.date}
        return TodayWidgetContent("VEYRA • ${tasks.size} tarefas",tasks.firstOrNull()?.title ?: "Seu dia, com clareza.")
    }
}

class TodayWidget:AppWidgetProvider(){
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){
        val pending=goAsync()
        java.util.concurrent.Executors.newSingleThreadExecutor().also{executor->executor.execute{try{
            val uid=WorkspaceIdentity.activeUid(context)
            val content=WorkspaceStore(context,WorkspaceIdentity.databaseForUid(uid)).use{store->
                val preferences=store.preferences();val today=LocalDate.now()
                val items=if(FinancePrivacyPolicy.widgetDetails(preferences))store.financeWindow(today.minusMonths(3).withDayOfMonth(1),today.plusYears(1))else store.overview(today)
                TodayWidgetSnapshot.render(items,preferences,today)
            }
            AndroidJobs.withCurrentScope(context,uid){ids.forEach{id->
                val view=RemoteViews(context.packageName,R.layout.widget_today)
                view.setTextViewText(R.id.widget_title,content.title);view.setTextViewText(R.id.widget_body,content.body)
                val open=AndroidJobs.scopedIntent(Intent(context,MainActivity::class.java).putExtra("finance",content.financial).putExtra(AndroidJobs.SCOPE_UID,uid.orEmpty()).putExtra(AndroidJobs.SCOPE_BOUND,true),uid,"widget-open",id.toString())
                val add=AndroidJobs.scopedIntent(if(content.financial)Intent(open)else Intent(open).putExtra("capture",true),uid,"widget-add",id.toString())
                view.setTextViewText(R.id.widget_add,if(content.financial)"→ Abrir financeiro"else"+ Captura rápida")
                val request=(AndroidJobs.scopeKey(uid)+id).hashCode()
                view.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,request,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                view.setOnClickPendingIntent(R.id.widget_add,PendingIntent.getActivity(context,request xor 0x40000000,add,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(id,view)
            }}
        }catch(_:Exception){
            // Failed calculations never replace a safe widget with stale financial values.
            ids.forEach{id->val view=RemoteViews(context.packageName,R.layout.widget_today);view.setTextViewText(R.id.widget_title,"VEYRA");view.setTextViewText(R.id.widget_body,"Abra o aplicativo para atualizar seu resumo.");manager.updateAppWidget(id,view)}
        }finally{pending.finish();executor.shutdown()}}}
    }
}
