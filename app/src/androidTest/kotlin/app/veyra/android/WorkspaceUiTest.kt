package app.veyra.android
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.veyra.data.WorkspaceStore
import org.junit.*
class WorkspaceUiTest {
    @Test fun captureTaskAndGlobalSearchWorks(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        WorkspaceStore(instrumentation.targetContext).use{it.preference("name","Teste");it.preference("lock","")}
        val device=UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use{
            val capture=device.wait(Until.findObject(By.desc("Capturar")),30000)
            Assert.assertNotNull("Captura deve aparecer após carregar dados",capture)
            capture.click()
            val title=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000)
            Assert.assertNotNull("Editor deve ter título editável",title)
            title.text="Tarefa instrumentada"
            device.findObject(By.text("Salvar")).click()
            device.wait(Until.findObject(By.desc("Buscar")),10000).click()
            Assert.assertTrue(device.wait(Until.hasObject(By.text("Buscar em toda sua vida")),10000))
            device.findObject(By.clazz("android.widget.EditText")).text="instrumentada"
            Assert.assertTrue(device.wait(Until.hasObject(By.text("Tarefa instrumentada")),10000))
        }
    }
    @After fun cleanup(){WorkspaceStore(InstrumentationRegistry.getInstrumentation().targetContext).use{store->store.all().filter{it.title=="Tarefa instrumentada"}.forEach{store.save(it.copy(deletedAt=1))}}}
}
