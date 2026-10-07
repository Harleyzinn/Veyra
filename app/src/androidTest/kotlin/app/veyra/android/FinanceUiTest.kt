package app.veyra.android

import android.app.KeyguardManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.veyra.data.WorkspaceIdentity
import app.veyra.data.WorkspaceStore
import app.veyra.feature.finance.FinancialDomain
import app.veyra.model.Item
import org.junit.*
import java.util.UUID
import java.util.regex.Pattern

/** Critical flows use the actual activity, Room writes, calculator, and Android accessibility tree. */
class FinanceUiTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val token=UUID.randomUUID().toString().take(8)
    private var preserved=emptyList<Item>()
    private var prepared=false
    private var originalPreferences=emptyMap<String,String>()
    private val changedPreferences=mapOf("name" to "Teste", "lock" to "", "financeLock" to "Não", "home" to "tasks,balance,weather,habits,focus,favorites",
        "financeHidden" to "Não", "financeCurrency" to "BRL", "recentFinanceAccount" to "", "recentExpenseCategory" to "Alimentação", "recentIncomeCategory" to "Salário", "financeNotifications" to "Não", "financialMigrationVersion" to "3")

    @Before fun isolateLocalFinance(){
        Assume.assumeTrue("UI regression tests use the guest workspace only",WorkspaceIdentity.activeUid(context)==null)
        WorkspaceStore(context).use{store->
            originalPreferences=store.preferences().filterKeys{it in changedPreferences}
            preserved=store.all().filter{it.type in FinancialDomain.financialTypes || it.type=="financial_asset"}
            store.saveAll(preserved.filter{it.deletedAt==0L}.map{it.copy(deletedAt=System.currentTimeMillis())})
            changedPreferences.forEach{(key,value)->store.preference(key,value)}
            prepared=true
        }
    }
    @Test fun lockedHomeKeepsNoteFavoritesButNeverExposesFinancialFavoriteTitles(){
        val note=Item(id="privacy-note-$token",type="note",title="Ideia privada $token",favorite=true)
        val account=Item(id="privacy-account-$token",type="account",title="Banco sigiloso $token",favorite=true,fields=mapOf("openingMinor" to "12500","currency" to "BRL"))
        WorkspaceStore(context).use{store->store.saveAll(listOf(note,account));store.preference("home","favorites");store.preference("financeLock","Sim")}
        try{
            ActivityScenario.launch(MainActivity::class.java).use{
                Assert.assertTrue(device.wait(Until.hasObject(By.text(note.title)),30_000))
                Assert.assertFalse(device.hasObject(By.text(account.title)))
                val tabs=device.findObjects(By.text("Finanças"));Assert.assertTrue(tabs.isNotEmpty());tabs.last().click()
                Assert.assertTrue(device.wait(Until.hasObject(By.text("Seu financeiro está protegido")),10_000))
                Assert.assertFalse(device.hasObject(By.text(account.title)))
            }
        }finally{WorkspaceStore(context).use{it.trash(note.id)}}
    }
    @After fun restoreLocalFinance(){
        if(!prepared)return
        WorkspaceStore(context).use{store->
            val existing=preserved.map{it.id}.toSet()
            val generated=store.all().filter{it.id !in existing && (it.type in FinancialDomain.financialTypes || it.type=="financial_asset") && it.deletedAt==0L}
            store.saveAll(generated.map{it.copy(deletedAt=System.currentTimeMillis())})
            store.saveAll(preserved)
            changedPreferences.forEach{(key,fallback)->store.preference(key,originalPreferences[key] ?: fallback)}
        }
    }
    private fun goFinance(){
        val tabs=device.wait(Until.findObjects(By.text("Finanças")),30_000)
        Assert.assertFalse("The finance tab must be accessible",tabs.isNullOrEmpty())
        tabs.maxBy{it.visibleBounds.centerY()}.click()
    }
    private fun text(selector:BySelector,timeout:Long=15_000):UiObject2=
        device.wait(Until.findObject(selector),timeout) ?: throw AssertionError("Element not found: $selector")
    private fun hideKeyboard(){if(device.hasObject(By.pkg(Pattern.compile(".*inputmethod.*"))))device.pressBack();SystemClock.sleep(250)}
    private fun scrollTo(selector:BySelector):UiObject2 {
        repeat(12){device.findObject(selector)?.let{return it};device.swipe(device.displayWidth/2,device.displayHeight*3/4,device.displayWidth/2,device.displayHeight/3,20);SystemClock.sleep(300)}
        return text(selector)
    }
    private fun awaitRecord(predicate:(Item)->Boolean):Item {
        val deadline=SystemClock.elapsedRealtime()+20_000
        while(SystemClock.elapsedRealtime()<deadline){WorkspaceStore(context).use{store->store.all().firstOrNull(predicate)?.let{return it}};SystemClock.sleep(150)}
        throw AssertionError("The financial record was not committed to Room")
    }

    @Test fun quickExpenseStoresExactMinorUnitsAndPaidStatus(){
        ActivityScenario.launch(MainActivity::class.java).use{
            goFinance();text(By.text("− Gasto")).click()
            text(By.text("Novo gasto"))
            val fields=device.findObjects(By.clazz("android.widget.EditText"))
            Assert.assertTrue("Quick form contains amount and description",fields.size>=2)
            fields[0].text="1.234,56";fields[1].text="Finance UI café $token"
            hideKeyboard();text(By.text("Salvar")).click()
            val saved=awaitRecord{item->item.title=="Finance UI café $token" && item.deletedAt==0L}
            Assert.assertEquals("expense",saved.type)
            Assert.assertEquals("123456",saved.value("amountMinor"))
            Assert.assertEquals("paid",saved.value("status"))
            Assert.assertEquals("Alimentação",saved.value("category"))
            Assert.assertEquals(123456L,FinancialDomain.amount(saved))
        }
        WorkspaceStore(context).use{store->Assert.assertNotNull(store.all().firstOrNull{it.title=="Finance UI café $token" && it.value("amountMinor")=="123456"})}
    }

    @Test fun accountOpeningBalanceEditPersistsExactly(){
        val account=FinancialDomain.normalize(Item(id="finance-ui-account-$token",type="account",title="Conta UI $token",fields=mapOf("opening" to "10.00","currency" to "BRL")))
        WorkspaceStore(context).use{it.save(account)}
        ActivityScenario.launch(MainActivity::class.java).use{
            goFinance();scrollTo(By.text("Explorar: Resumo")).click();text(By.text("Contas")).click()
            scrollTo(By.text("Conta UI $token"));scrollTo(By.text("Editar")).click()
            text(By.text("Conta ou carteira"))
            val fields=device.findObjects(By.clazz("android.widget.EditText"))
            Assert.assertTrue("Account editor contains name and opening balance",fields.size>=2)
            fields[1].text="123,45";hideKeyboard();text(By.text("Salvar")).click()
            val updated=awaitRecord{item->item.id==account.id && item.value("openingMinor")=="12345"}
            Assert.assertEquals("123.45",updated.value("opening"))
            Assert.assertEquals(12345L,FinancialDomain.amount(updated,"opening"))
        }
    }

    @Test fun partialInvoicePaymentPersistsExactCashAndKeepsRemainder(){
        val today=java.time.LocalDate.now()
        val account=FinancialDomain.normalize(Item(id="partial-account-$token",type="account",title="Conta parcial $token",fields=mapOf("openingMinor" to "100000","currency" to "BRL")))
        val card=FinancialDomain.normalize(Item(id="partial-card-$token",type="card",title="Cartão parcial $token",fields=mapOf("closing" to "10","due" to "20","limitMinor" to "500000","account" to account.id,"currency" to "BRL")))
        val buy=FinancialDomain.normalize(Item(id="partial-buy-$token",type="expense",title="Compra parcial $token",date=today.toString(),fields=mapOf("amountMinor" to "10001","card" to card.id,"status" to "pending","currency" to "BRL","category" to "Compras")))
        WorkspaceStore(context).use{it.saveAll(listOf(account,card,buy))}
        ActivityScenario.launch(MainActivity::class.java).use{
            goFinance();scrollTo(By.text("Explorar: Resumo")).click();text(By.text("Cartões")).click()
            scrollTo(By.text("Registrar pagamento da fatura")).click()
            text(By.text("Registrar pagamento"))
            val fields=device.findObjects(By.clazz("android.widget.EditText"));Assert.assertEquals(2,fields.size)
            fields[0].text="33,33";hideKeyboard();device.takeScreenshot(java.io.File("/sdcard/Download/veyra-partial-2.1.png"));text(By.text("Confirmar registro")).click()
            val payment=awaitRecord{it.value("paymentType")=="card_payment" && it.value("card")==card.id && it.deletedAt==0L}
            Assert.assertEquals(3333L,FinancialDomain.amount(payment));Assert.assertEquals(account.id,payment.value("account"))
            Assert.assertEquals(today.toString(),payment.value("settledDate"))
            WorkspaceStore(context).use{store->
                val rows=store.all();val invoice=app.veyra.feature.finance.CardEngine.invoices(rows,card,today.minusMonths(1),today.plusMonths(2)).single()
                Assert.assertEquals(6668L,invoice.outstandingMinor)
                Assert.assertEquals(96667L,app.veyra.feature.finance.FinanceEngine.accountBalance(rows,account,today))
            }
        }
    }

    @Test fun simulatorDoesNotWriteTransactionsAndHonorsPrivacy(){
        val account=FinancialDomain.normalize(Item(id="scenario-account-$token",type="account",title="Conta cenário $token",fields=mapOf("openingMinor" to "100000","currency" to "BRL")))
        WorkspaceStore(context).use{it.save(account)}
        val before=WorkspaceStore(context).use{it.exportJson()}
        ActivityScenario.launch(MainActivity::class.java).use{
            goFinance();scrollTo(By.text("Explorar: Resumo")).click();text(By.text("Simulador")).click()
            scrollTo(By.text("Entrada extra"))
            val fields=device.findObjects(By.clazz("android.widget.EditText"));Assert.assertTrue(fields.isNotEmpty())
            fields[0].text="100,00";hideKeyboard()
            scrollTo(By.text("Seu cenário"));Assert.assertEquals(before,WorkspaceStore(context).use{it.exportJson()})
            scrollTo(By.textContains("Margem diária adicional:"));device.takeScreenshot(java.io.File("/sdcard/Download/veyra-scenario-2.1.png"))
            repeat(10){device.swipe(device.displayWidth/2,device.displayHeight/3,device.displayWidth/2,device.displayHeight*3/4,15)}
            text(By.desc("Ocultar valores")).click()
            scrollTo(By.text("Exiba os valores no topo para usar o simulador."))
            Assert.assertFalse(device.hasObject(By.text("Seu cenário")))
        }
    }

    @Test fun reviewFindsManualDuplicatesWithoutRemovingThem(){
        val original=FinancialDomain.normalize(Item(id="review-a-$token",type="expense",title="Café conferência $token",fields=mapOf("amountMinor" to "1250","currency" to "BRL","status" to "paid")))
        val copy=original.copy(id="review-b-$token")
        WorkspaceStore(context).use{it.saveAll(listOf(original,copy))}
        ActivityScenario.launch(MainActivity::class.java).use{
            goFinance();scrollTo(By.text("Explorar: Resumo")).click();text(By.text("Conferência")).click()
            scrollTo(By.text("Possível duplicidade · 2 registros"))
            device.takeScreenshot(java.io.File("/sdcard/Download/veyra-review-2.1.png"))
            WorkspaceStore(context).use{store->Assert.assertEquals(0L,store.find(original.id)!!.deletedAt);Assert.assertEquals(0L,store.find(copy.id)!!.deletedAt)}
        }
    }

    @Test fun emptyDashboardHasNoSampleTransactions(){
        ActivityScenario.launch(MainActivity::class.java).use{
            goFinance();scrollTo(By.textContains("Não há exemplos misturados"))
            Assert.assertTrue(device.hasObject(By.textContains("Não há exemplos misturados")))
            WorkspaceStore(context).use{store->Assert.assertTrue(store.all().none{it.type in FinancialDomain.transactionTypes && it.deletedAt==0L})}
        }
    }

    @Test fun missingDeviceCredentialCannotUnlockFinancialData(){
        Assume.assumeFalse(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        WorkspaceStore(context).use{it.preference("financeLock","Sim")}
        ActivityScenario.launch(MainActivity::class.java).use{
            goFinance()
            Assert.assertNotNull(text(By.text("Seu financeiro está protegido")))
            Assert.assertTrue(text(By.text("Configurar bloqueio do aparelho")).isEnabled)
            Assert.assertFalse(device.hasObject(By.text("SALDO ATUAL")))
            Assert.assertFalse(device.hasObject(By.text("− Gasto")))
        }
    }
}
