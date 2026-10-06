package app.veyra.android

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.veyra.data.WorkspaceStore
import app.veyra.model.Item
import org.junit.*
import java.time.LocalDate

class ExtraFlowTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun click(text:String){
        repeat(10){val node=device.findObject(By.text(text));if(node!=null){node.click();android.os.SystemClock.sleep(500);device.waitForIdle();return};device.swipe(500,1700,500,800,25);device.waitForIdle()}
        Assert.fail("Não encontrado: $text")
    }
    private fun module(label:String){
        click("Mais");device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000).text=label;android.os.SystemClock.sleep(500)
        device.wait(Until.findObject(By.text(label).clazz("android.widget.TextView")),10000)?.click() ?: Assert.fail("Módulo não encontrado: $label")
        android.os.SystemClock.sleep(500);device.waitForIdle()
    }
    @Before fun setup(){WorkspaceStore(context).use{store->
        store.preference("name","Ana");store.preference("lock","");store.preference("profile","Completo");store.preference("hidden","")
        store.saveAll(listOf(
            Item(id="extra-bill",type="bill",title="Conta de energia QA",fields=mapOf("amount" to "150","category" to "Casa")),
            Item(id="extra-meal",type="meal",title="Salada QA",fields=mapOf("meal" to "Almoço","ingredients" to "Tomate QA\nAlface QA\nTomate QA")),
            Item(id="extra-goal",type="savings_goal",title="Viagem QA",fields=mapOf("amount" to "2000","saved" to "500")),
            Item(id="extra-countdown",type="countdown",title="Férias QA",date=LocalDate.now().plusDays(10).toString())
        ))
    }}
    @After fun cleanup(){WorkspaceStore(context).use{store->store.all().filter{it.id.startsWith("extra-") || it.id.contains("extra-")}.forEach{store.save(it.copy(deletedAt=System.currentTimeMillis()))}}}
    @Test fun billPaymentPersistsOneExpense(){ActivityScenario.launch(MainActivity::class.java).use{
        Assert.assertTrue(device.wait(Until.hasObject(By.text("Mais")),30000));module("Contas a pagar");click("Pagar e registrar despesa")
        device.wait(Until.gone(By.text("Pagar e registrar despesa")),10000)
        WorkspaceStore(context).use{store->val all=store.all();Assert.assertTrue(all.first{it.id=="extra-bill"}.done);Assert.assertEquals(1,all.count{it.id=="bill-payment:extra-bill" && it.deletedAt==0L});Assert.assertEquals(15000L,all.first{it.id=="bill-payment:extra-bill"}.cents())}
    }}
    @Test fun mealIngredientsDoNotDuplicate(){ActivityScenario.launch(MainActivity::class.java).use{
        Assert.assertTrue(device.wait(Until.hasObject(By.text("Mais")),30000));module("Cardápio semanal")
        repeat(6){if(device.hasObject(By.text("Adicionar ingredientes às compras")))return@repeat;device.swipe(500,1700,500,800,25);device.waitForIdle()}
        click("Adicionar ingredientes às compras");click("Adicionar ingredientes às compras")
        WorkspaceStore(context).use{store->Assert.assertEquals(2,store.all().count{it.id.startsWith("meal-shopping:extra-meal:") && it.deletedAt==0L})}
    }}
    @Test fun goalAndCountdownShowCalculatedProgress(){ActivityScenario.launch(MainActivity::class.java).use{
        Assert.assertTrue(device.wait(Until.hasObject(By.text("Mais")),30000));module("Metas financeiras")
        repeat(5){if(device.hasObject(By.textContains("Faltam R$")))return@repeat;device.swipe(500,1700,500,800,25);device.waitForIdle()}
        Assert.assertTrue(device.hasObject(By.textContains("1.500")))
        device.executeShellCommand("mkdir -p /sdcard/Download/veyra-qa");device.executeShellCommand("screencap -p /sdcard/Download/veyra-qa/metas-1.1.png")
        module("Contagem regressiva");Assert.assertTrue(device.wait(Until.hasObject(By.text("Faltam 10 dias")),10000))
    }}
}
