package app.veyra.android

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.veyra.data.WorkspaceStore
import app.veyra.model.Item
import org.junit.*
import java.io.File
import java.time.LocalDate

class StudioFlowTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun click(text:String){
        repeat(8){val node=device.wait(Until.findObject(By.text(text)),2000);if(node!=null){node.click();android.os.SystemClock.sleep(350);device.waitForIdle();return};device.swipe(device.displayWidth/2,device.displayHeight*3/4,device.displayWidth/2,device.displayHeight/3,25);device.waitForIdle()}
        Assert.fail("Elemento não encontrado: $text")
    }
    private fun screenshot(name:String){device.waitForIdle();device.executeShellCommand("mkdir -p /sdcard/Download/veyra-qa");device.executeShellCommand("screencap -p /sdcard/Download/veyra-qa/$name.png")}
    @Before fun setup(){WorkspaceStore(context).use{store->
        store.preference("name","Ana");store.preference("lock","");store.preference("theme","Escuro");store.preference("profile","Completo");store.preference("weatherMode","Manual");store.preference("weatherTemperature","24");store.preference("weatherCondition","Céu limpo")
        val today=LocalDate.now().toString()
        store.saveAll(listOf(
            Item(id="qa-task",type="task",title="Planejar a semana",date=today,fields=mapOf("priority" to "Alta","status" to "A fazer")),
            Item(id="qa-task2",type="task",title="Treino de hoje",date=today),
            Item(id="qa-account",type="account",title="Conta principal",fields=mapOf("opening" to "1200","bank" to "Minha carteira")),
            Item(id="qa-income",type="income",title="Salário",date=today,fields=mapOf("amount" to "4800","category" to "Trabalho","account" to "qa-account")),
            Item(id="qa-expense",type="expense",title="Mercado da semana",date=today,fields=mapOf("amount" to "286.50","category" to "Alimentação","account" to "qa-account")),
            Item(id="qa-expense2",type="expense",title="Aluguel",date=today,fields=mapOf("amount" to "1450","category" to "Casa")),
            Item(id="qa-expense3",type="expense",title="Internet",date=today,fields=mapOf("amount" to "99.90","category" to "Casa","planned" to "Sim")),
            Item(id="qa-budget",type="budget",title="Comer bem, gastar melhor",fields=mapOf("category" to "Alimentação","amount" to "800")),
            Item(id="qa-habit",type="habit",title="Ler 20 minutos",fields=mapOf("frequency" to "Diária")),
            Item(id="qa-note",type="note",title="Ideias para meu próximo projeto",notes="Criar algo que simplifique a rotina.\n\n• Organizar as prioridades\n• Reservar tempo para aprender\n• Dar o primeiro passo",fields=mapOf("folder" to "Pessoal"),tags="#ideias"),
            Item(id="qa-note2",type="note",title="Coisas boas para lembrar",notes="Uma caminhada ao ar livre, uma conversa boa e um dia com espaço para respirar.",fields=mapOf("folder" to "Pessoal"))
        ))
    }}
    @Test fun financePlanilhaAndBudgets(){ActivityScenario.launch(MainActivity::class.java).use{
        fun reveal(selector:BySelector):Boolean{
            repeat(10){if(device.wait(Until.hasObject(selector),1500))return true;device.swipe(device.displayWidth/2,device.displayHeight*3/4,device.displayWidth/2,device.displayHeight/3,25);device.waitForIdle()}
            return false
        }
        Assert.assertTrue(device.wait(Until.hasObject(By.desc("Capturar")),30000));click("Finanças")
        Assert.assertTrue(device.wait(Until.hasObject(By.textContains("4.800")),10000));screenshot("financas")
        click("Movimentações");Assert.assertTrue(reveal(By.text("Mercado da semana")));screenshot("movimentacoes")
        repeat(6){device.swipe(device.displayWidth/2,device.displayHeight/3,device.displayWidth/2,device.displayHeight*3/4,25);device.waitForIdle()}
        click("Explorar: Movimentações");click("Orçamentos")
        Assert.assertTrue(reveal(By.text("Comer bem, gastar melhor")))
        Assert.assertTrue(reveal(By.textContains("286,50")))
        Assert.assertTrue(reveal(By.textContains("513,50")))
    }}
    @Test fun calendarShowsTasksAndKanbanStages(){ActivityScenario.launch(MainActivity::class.java).use{
        Assert.assertTrue(device.wait(Until.hasObject(By.desc("Capturar")),30000));click("Agenda")
        Assert.assertTrue(device.wait(Until.hasObject(By.text("Dê espaço aos planos.")),10000));screenshot("agenda")
        click("Kanban");Assert.assertTrue(device.wait(Until.hasObject(By.text("A fazer · 2")),10000));click("Começar →")
        WorkspaceStore(context).use{store->Assert.assertTrue(store.all().any{i->i.type=="task" && i.value("status")=="Fazendo"})}
    }}
    @Test fun notesAndManualWeatherPersist(){ActivityScenario.launch(MainActivity::class.java).use{
        Assert.assertTrue(device.wait(Until.hasObject(By.desc("Capturar")),30000));screenshot("inicio")
        click("Notas");Assert.assertTrue(device.wait(Until.hasObject(By.text("Ideias para meu próximo projeto")),10000));screenshot("notas")
        click("Hoje");screenshot("apos-voltar");click("Clima");screenshot("clima-apos-toque");Assert.assertTrue(device.wait(Until.hasObject(By.text("Meu clima")),10000));screenshot("clima")
        click("Salvar meu clima")
    }
        WorkspaceStore(context).use{Assert.assertEquals("Manual",it.preferences()["weatherMode"]);Assert.assertEquals("24",it.preferences()["weatherTemperature"])}
        ActivityScenario.launch(MainActivity::class.java).use{Assert.assertTrue(device.wait(Until.hasObject(By.desc("Capturar")),30000));click("Hoje");click("Clima");Assert.assertTrue(device.wait(Until.hasObject(By.text("24°")),10000))}
    }
    @Test fun quickTaskCanBeCompleted(){ActivityScenario.launch(MainActivity::class.java).use{
        Assert.assertTrue(device.wait(Until.hasObject(By.desc("Capturar")),30000));click("Tarefas")
        device.findObject(By.clazz("android.widget.EditText")).text="Tarefa rápida de teste"
        click("Adicionar hoje")
        click("Tarefa rápida de teste");screenshot("tarefa-detalhe")
        val complete=device.wait(Until.findObject(By.desc("Concluir Tarefa rápida de teste")),10000)
        if(complete!=null){complete.click();Assert.assertTrue(device.wait(Until.gone(By.text("Tarefa rápida de teste")),10000))}
        else {click("Concluir");Assert.assertTrue(device.wait(Until.hasObject(By.text("Reabrir")),10000))}
    }
        WorkspaceStore(context).use{Assert.assertTrue(it.all().any{i->i.title=="Tarefa rápida de teste" && i.done})}
    }
    @After fun cleanup(){screenshot("ultimo-estado");WorkspaceStore(context).use{store->store.all().filter{it.id.startsWith("qa-") || it.title=="Tarefa rápida de teste"}.forEach{store.save(it.copy(deletedAt=System.currentTimeMillis()))}}}
}
