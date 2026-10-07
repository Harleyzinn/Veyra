package app.veyra.android

import android.app.Notification
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import app.veyra.data.WorkspaceIdentity
import app.veyra.data.WorkspaceStore
import app.veyra.feature.finance.AlertPriority
import app.veyra.feature.finance.FinancialAlert
import app.veyra.model.Item
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinancePlatformSecurityTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun legacyJobsAreNeverReinterpretedInTheCurrentAccount(){
        assertFalse(AndroidJobs.isBoundCurrent(context,Data.EMPTY))
        assertNotEquals(AndroidJobs.reminderName(null,"same-id"),AndroidJobs.reminderName("another-user","same-id"))
        assertNotEquals(AndroidJobs.workspaceTag(null),AndroidJobs.workspaceTag("another-user"))
    }
    @Test fun pendingIntentsAndNotificationTagsSeparateHashCollidingImportedIds(){
        assertEquals("Aa".hashCode(),"BB".hashCode())
        val first=AndroidJobs.scopedIntent(Intent(context,ReminderAction::class.java).setAction("done"),null,"done","Aa")
        val second=AndroidJobs.scopedIntent(Intent(context,ReminderAction::class.java).setAction("done"),null,"done","BB")
        assertFalse(first.filterEquals(second))
        assertNotEquals(AndroidJobs.notificationTag(null,"Aa"),AndroidJobs.notificationTag(null,"BB"))
        val otherUser=AndroidJobs.scopedIntent(Intent(first),"other-user","done","Aa")
        assertFalse(first.filterEquals(otherUser))
        assertNotEquals(AndroidJobs.notificationTag(null,"Aa"),AndroidJobs.notificationTag("other-user","Aa"))
    }
    @Test fun oldAccountActionCannotCompleteNewAccountRecordWithSameId(){
        val previous=WorkspaceIdentity.activeUid(context)
        val first="jobs-a-${UUID.randomUUID()}";val second="jobs-b-${UUID.randomUUID()}"
        try{
            WorkspaceIdentity.setActiveUid(context,first)
            WorkspaceStore(context).use{it.save(Item(id="same-id",type="task",title="Conta A"))}
            val intent=Intent(context,ReminderAction::class.java).setAction("done").putExtra("id","same-id")
                .putExtra(AndroidJobs.SCOPE_BOUND,true).putExtra(AndroidJobs.SCOPE_UID,first)
            WorkspaceIdentity.setActiveUid(context,second)
            WorkspaceStore(context).use{it.save(Item(id="same-id",type="task",title="Conta B"))}
            assertFalse(AndroidJobs.scopedAction(context,intent))
            WorkspaceStore(context).use{assertFalse(it.find("same-id")!!.done)}
            WorkspaceStore(context,WorkspaceIdentity.databaseForUid(first)).use{assertFalse(it.find("same-id")!!.done)}
        }finally{
            WorkspaceIdentity.setActiveUid(context,previous)
            context.deleteDatabase(WorkspaceIdentity.databaseForUid(first));context.deleteDatabase(WorkspaceIdentity.databaseForUid(second))
        }
    }
    @Test fun financialSettlementCannotBePerformedByNotificationAction(){
        val uid=WorkspaceIdentity.activeUid(context);val id="notification-finance-${UUID.randomUUID()}"
        try{
            WorkspaceStore(context).use{it.save(Item(id=id,type="expense",title="Conta",fields=mapOf("amount" to "12.34","status" to "pending")))}
            val intent=Intent(context,ReminderAction::class.java).setAction("done").putExtra("id",id)
                .putExtra(AndroidJobs.SCOPE_BOUND,true).putExtra(AndroidJobs.SCOPE_UID,uid.orEmpty())
            assertFalse(AndroidJobs.scopedAction(context,intent))
            WorkspaceStore(context).use{assertFalse(it.find(id)!!.done);assertEquals("pending",it.find(id)!!.value("status"))}
        }finally{WorkspaceStore(context).use{it.trash(id)}}
    }
    @Test fun notificationsRequireOptInAndNeverRepeatTheSameThreshold(){
        val today=LocalDate.parse("2026-10-06")
        val alerts=(1..6).map{FinancialAlert("budget:b:$it",AlertPriority.ATTENTION,"Orçamento","90%")}
        assertTrue(FinanceNotifications.pending(alerts,emptyMap(),today).isEmpty())
        val enabled=mapOf("financeNotifications" to "Sim")
        assertEquals(3,FinanceNotifications.pending(alerts,enabled,today).size)
        val oneSeen=enabled+mapOf(FinanceNotifications.seenKey(alerts.first().id) to today.toString(),"financeAlertDate" to today.toString(),"financeAlertCount" to "1")
        assertEquals(listOf(alerts[1],alerts[2]),FinanceNotifications.pending(alerts,oneSeen,today))
        assertTrue(FinanceNotifications.pending(alerts,oneSeen+("financeAlertCount" to "3"),today).isEmpty())
        assertEquals(3,FinanceNotifications.pending(alerts,oneSeen,today.plusDays(1)).size)
    }
    @Test fun notificationPublicVersionNeverContainsAmountsOrAccountNames(){
        val alert=FinancialAlert("private",AlertPriority.URGENT,"Fatura particular","Banco pessoal • BRL 98765.43")
        val defaults=FinanceNotifications.buildNotification(context,null,alert,emptyMap())
        assertEquals(Notification.VISIBILITY_PRIVATE,defaults.visibility)
        assertEquals("Alerta financeiro",defaults.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertFalse(defaults.extras.toString().contains("98765"))
        val optedIn=FinanceNotifications.buildNotification(context,null,alert,mapOf("financeNotificationValues" to "Sim"))
        assertEquals(alert.title,optedIn.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNotNull(optedIn.publicVersion)
        assertFalse(optedIn.publicVersion.extras.toString().contains("98765"))
        assertFalse(optedIn.publicVersion.extras.toString().contains("Banco pessoal"))
        val protected=FinanceNotifications.buildNotification(context,null,alert,mapOf("financeNotificationValues" to "Sim","financeLock" to "Sim"))
        assertEquals("Alerta financeiro",protected.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }
    @Test fun widgetIsExplicitlyOptedInAndHonorsEveryPrivacyGate(){
        val account=Item(id="widget-account",type="account",title="Conta pessoal",fields=mapOf("opening" to "98765.43"))
        val task=Item(type="task",title="Minha tarefa")
        val default=TodayWidgetSnapshot.render(listOf(account,task),emptyMap(),LocalDate.parse(task.date))
        assertFalse(default.financial);assertEquals(task.title,default.body)
        listOf("financeHidden","financeLock","financeHideValues").forEach{setting->
            val safe=TodayWidgetSnapshot.render(listOf(account),mapOf("widgetFinance" to "Sim",setting to "Sim"))
            assertFalse(safe.body.contains("98765"));assertFalse(FinancePrivacyPolicy.widgetDetails(mapOf("widgetFinance" to "Sim",setting to "Sim")))
        }
        val locked=TodayWidgetSnapshot.render(listOf(account,task),mapOf("lock" to "pin-hash","widgetFinance" to "Sim"))
        assertFalse(locked.body.contains("98765"));assertFalse(locked.body.contains(task.title))
        assertTrue(FinancePrivacyPolicy.widgetDetails(mapOf("widgetFinance" to "Sim")))
        assertFalse(FinancePrivacyPolicy.notificationDetails(mapOf("financeNotificationValues" to "Sim","financeHidden" to "Sim")))
    }
}
