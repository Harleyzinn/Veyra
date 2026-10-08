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
        "financeHidden" to "Não", "financeCurrency" to "BRL", "financialDay" to "1", "recentFinanceAccount" to "", "recentExpenseCategory" to "Alimentação", "recentIncomeCategory" to "Salário", "financeNotifications" to "Não", "financialMigrationVersion" to "3")

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
        // A short, slow drag avoids flinging past rows inside tall report cards.
        for(down in listOf(true,false))repeat(30){
            device.waitForIdle(1500)
            var dragDown=down
            try{device.findObject(selector)?.let{node->
                val bounds=node.visibleBounds
                val center=bounds.centerY()
                if(!bounds.isEmpty && center in device.displayHeight/10..device.displayHeight*4/5)return node
                if(!bounds.isEmpty)dragDown=center>device.displayHeight*4/5
            }}catch(_:StaleObjectException){ }
            val from=if(dragDown)device.displayHeight*2/3 else device.displayHeight/2
            val to=if(dragDown)device.displayHeight/2 else device.displayHeight*2/3
            device.swipe(device.displayWidth/2,from,device.displayWidth/2,to,60)
        }
        device.dumpWindowHierarchy(java.io.File("/sdcard/Download/android23-failure.xml"))
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
            repeat(3){
                if(!device.hasObject(By.text("Registrar pagamento"))){
                    val button=scrollTo(By.text("Registrar pagamento da fatura"))
                    SystemClock.sleep(500)
                    val bounds=button.visibleBounds
                    device.click(bounds.centerX(),bounds.centerY())
                    device.wait(Until.hasObject(By.text("Registrar pagamento")),4_000)
                }
            }
            if(!device.hasObject(By.text("Registrar pagamento"))){
                device.takeScreenshot(java.io.File("/sdcard/Download/partial23-failure.png"))
                device.dumpWindowHierarchy(java.io.File("/sdcard/Download/partial23-failure.xml"))
            }
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

    @Test fun reportsUseActualPaymentDateAndOfferOnlySelectedTotals(){
        val today=java.time.LocalDate.now()
        val expense=FinancialDomain.normalize(Item(id="report23-$token",type="expense",title="Pagamento de outro mês $token",date=today.minusMonths(1).withDayOfMonth(1).toString(),fields=mapOf("amountMinor" to "4200","currency" to "BRL","status" to "paid","settledDate" to today.toString())))
        WorkspaceStore(context).use{store->
            store.save(expense)
            val rows=store.financeWindow(today.withDayOfMonth(1),today.withDayOfMonth(today.lengthOfMonth()))
            Assert.assertEquals(1,app.veyra.feature.finance.FinanceReports.select(rows,today.withDayOfMonth(1),today.withDayOfMonth(today.lengthOfMonth()),"BRL",app.veyra.feature.finance.ReportBasis.CASH).entries.size)
        }
        ActivityScenario.launch(MainActivity::class.java).use{
            goFinance();scrollTo(By.text("Explorar: Resumo")).click();text(By.text("Relatórios")).click()
            scrollTo(By.text("Analisar: Gastos registrados")).click();text(By.text("Fluxo de caixa")).click()
            text(By.text("Analisar: Fluxo de caixa"))
            scrollTo(By.text("1 registro selecionado"))
            SystemClock.sleep(500)
            device.takeScreenshot(java.io.File("/sdcard/Download/android23-report-count.png"))
            device.dumpWindowHierarchy(java.io.File("/sdcard/Download/android23-report-count.xml"))
            scrollTo(By.textContains("Saídas pagas:"))
            Assert.assertTrue(device.findObject(By.textContains("Saídas pagas:")).text.contains("42,00"))
            SystemClock.sleep(500)
            device.takeScreenshot(java.io.File("/sdcard/Download/veyra-report-2.3.png"))
            device.dumpWindowHierarchy(java.io.File("/sdcard/Download/android23-report-values.xml"))
            val output=FinanceReportWriter.csv(listOf(expense),listOf(expense))
            Assert.assertTrue(output.contains("Data de pagamento"));Assert.assertTrue(output.contains(today.toString()))
            val pdf=java.io.File(context.cacheDir,"report23-$token.pdf")
            try{
                FinanceReportWriter.pdf(context,android.net.Uri.fromFile(pdf),"Relatório selecionado",listOf("Entradas: R$ 0,00","Saídas: R$ 42,00"),listOf(expense),true)
                Assert.assertTrue(pdf.length()>100)
                Assert.assertEquals("%PDF",pdf.inputStream().use{stream->String(ByteArray(4).also{stream.read(it)},Charsets.US_ASCII)})
            }finally{pdf.delete()}
            scrollTo(By.textContains("PDF"))
            Assert.assertTrue(device.hasObject(By.textContains("CSV")))
            device.takeScreenshot(java.io.File("/sdcard/Download/veyra-report-2.3.png"))
        }
    }

    @Test fun upcomingSummaryShowsRemainingInvoiceAndKeepsOtherCurrenciesOut(){
        val today=java.time.LocalDate.now()
        val card=FinancialDomain.normalize(Item(id="agenda-card23-$token",type="card",title="Cartão agenda $token",fields=mapOf("closing" to "10","due" to "20","limitMinor" to "500000","currency" to "BRL")))
        val due=today.plusDays(2)
        val purchase=FinancialDomain.normalize(Item(id="agenda-buy23-$token",type="expense",title="Compra da fatura",date=today.toString(),fields=mapOf("amountMinor" to "30000","card" to card.id,"currency" to "BRL","dueDate" to due.toString(),"status" to "pending")))
        val payment=FinancialDomain.normalize(Item(id="agenda-pay23-$token",type="expense",title="Parcela paga",date=today.toString(),fields=mapOf("amountMinor" to "10000","card" to card.id,"currency" to "BRL","invoiceId" to "invoice:${card.id}:$due","paymentType" to "card_payment","status" to "paid")))
        val foreign=FinancialDomain.normalize(Item(id="agenda-usd23-$token",type="expense",title="Dólar não misturado $token",date=today.minusDays(1).toString(),fields=mapOf("amountMinor" to "9900","currency" to "USD","status" to "pending")))
        WorkspaceStore(context).use{it.saveAll(listOf(card,purchase,payment,foreign))}
        ActivityScenario.launch(MainActivity::class.java).use{
            goFinance();scrollTo(By.text("Vencimentos em foco"))
            scrollTo(By.textContains("1 pendência"));Assert.assertFalse(device.hasObject(By.text(foreign.title)))
            Assert.assertTrue(device.hasObject(By.textContains("200,00")))
            SystemClock.sleep(500)
            device.takeScreenshot(java.io.File("/sdcard/Download/veyra-agenda-2.3.png"))
        }
    }

    @Test fun notesCanSortByTitleWhileSearchRemainsApplied(){
        val a=Item(id="notes23-a-$token",type="note",title="Alfa $token",createdAt=1)
        val z=a.copy(id="notes23-z-$token",title="Zeta $token",createdAt=2)
        WorkspaceStore(context).use{it.saveAll(listOf(a,z))}
        try{ActivityScenario.launch(MainActivity::class.java).use{
            val tabs=device.wait(Until.findObjects(By.text("Notas")),30_000)
            tabs.maxBy{it.visibleBounds.centerY()}.click()
            text(By.clazz("android.widget.EditText")).text=token;hideKeyboard()
            scrollTo(By.text("Ordenar notas: Mais recentes")).click();text(By.text("Título")).click()
            scrollTo(By.text(z.title));Assert.assertTrue(device.hasObject(By.text(a.title)))
            Assert.assertTrue(device.findObject(By.text(a.title)).visibleBounds.top<device.findObject(By.text(z.title)).visibleBounds.top)
        }}finally{WorkspaceStore(context).use{it.saveAll(listOf(a,z).map{item->item.copy(deletedAt=System.currentTimeMillis())})}}
    }

    @Test fun reportChartsSwitchEveryViewAndRespectPrivacy(){
        val today=java.time.LocalDate.now().toString()
        val income=FinancialDomain.normalize(Item(id="chart-income-$token",type="income",title="Receita gráfico",date=today,fields=mapOf("amountMinor" to "5000","currency" to "BRL","status" to "received")))
        val expense=FinancialDomain.normalize(Item(id="chart-expense-$token",type="expense",title="Gasto gráfico",date=today,fields=mapOf("amountMinor" to "10001","currency" to "BRL","status" to "paid","category" to "Casa")))
        val other=expense.copy(id="chart-other-$token",fields=expense.fields+mapOf("amountMinor" to "20000","amount" to "200.00","category" to "Transporte"))
        WorkspaceStore(context).use{it.saveAll(listOf(income,expense,other))}
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            goFinance();scrollTo(By.text("Explorar: Resumo")).click();text(By.text("Relatórios")).click()
            scrollTo(By.text("Visualização: Barras"))
            scrollTo(By.descContains("Gráfico de barras"))
            var current="Barras"
            for(view in listOf("Linhas","Área","Rosca","Pizza","Tabela","Barras")){
                scrollTo(By.text("Visualização: $current")).click();text(By.text(view)).click()
                when(view){
                    "Rosca","Pizza"->{
                        scrollTo(By.descContains("Distribuição dos gastos"))
                        scrollTo(By.textContains("Total de gastos:"))
                        Assert.assertTrue(device.findObject(By.textContains("Total de gastos:")).text.contains("300,01"))
                    }
                    "Tabela"->scrollTo(By.text("Entradas, gastos e resultado por período"))
                    else->scrollTo(By.descContains("Gráfico de ${view.lowercase()}"))
                }
                device.takeScreenshot(java.io.File("/sdcard/Download/veyra-chart-${view.lowercase().replace('á','a')}-2.4.png"))
                current=view
            }
            scrollTo(By.text("Agrupar por: Semana")).click();text(By.text("Mês")).click()
            scrollTo(By.text("Comparar: Entradas e gastos")).click();text(By.text("Resultado do período")).click()
            scrollTo(By.descContains("Resultado. Selecione"))
            scrollTo(By.textContains("Resultado -"))
            Assert.assertTrue(device.findObject(By.textContains("Resultado -")).text.contains("250,01"))
            device.takeScreenshot(java.io.File("/sdcard/Download/veyra-chart-result-2.4.png"))
            scenario.onActivity{androidx.lifecycle.ViewModelProvider(it)[VeyraViewModel::class.java].pref("financeHidden","Sim")}
            scrollTo(By.text("Gráficos ocultos pelo modo privacidade."))
            Assert.assertFalse(device.hasObject(By.descContains("Gráfico de barras")))
            Assert.assertFalse(device.hasObject(By.textContains("300,01")))
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
