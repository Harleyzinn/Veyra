package app.veyra.android

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import android.os.SystemClock
import app.veyra.data.WorkspaceIdentity
import app.veyra.data.WorkspaceStore
import app.veyra.model.Item
import org.junit.*

class NavigationUiTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val prefs=mapOf("name" to "QA","lock" to "","financeLock" to "Não","theme" to "Escuro","profile" to "Essencial","hidden" to "worldclock","favoriteModules" to "tools","navHome" to "notes","navBottom" to "today,task,finance,notes","navOrder" to "tools,worldclock","navCollapsed" to "Vida pessoal","navRecent" to "","navUsage" to "")
    private var original=emptyMap<String,String>()
    private var prepared=false
    private val fixture=Item(id="navigation-preserved-ui",type="note",title="Navegação QA",notes="Conteúdo completo".repeat(100))
    @Before fun setup(){Assume.assumeTrue(WorkspaceIdentity.activeUid(context)==null);WorkspaceStore(context).use{s->original=s.preferences().filterKeys{it in prefs};prefs.forEach{(k,v)->s.preference(k,v)};s.save(fixture)};prepared=true;device.wakeUp();device.executeShellCommand("wm dismiss-keyguard")}
    @After fun restore(){if(prepared)WorkspaceStore(context).use{s->prefs.keys.forEach{s.preference(it,original[it].orEmpty())};s.find(fixture.id)?.let{s.save(it.copy(deletedAt=System.currentTimeMillis()))}}}
    private fun node(selector:BySelector):UiObject2=device.wait(Until.findObject(selector),20_000)?:throw AssertionError("Destino não encontrado: $selector")
    private fun settle(){device.waitForIdle();SystemClock.sleep(300)}
    private fun hideKeyboard(){if(device.hasObject(By.pkg(java.util.regex.Pattern.compile(".*inputmethod.*"))))device.pressBack();SystemClock.sleep(250)}
    @Test fun hiddenModuleIsSearchableAndPersonalizationPersistsWithoutChangingData(){
        Assert.assertTrue(Registry.specs.all{it.type in AppNavigation.ids})
        Assert.assertEquals(AppNavigation.destinations.size,AppNavigation.destinations.map{it.id}.toSet().size)
        Assert.assertEquals("notes",AppNavigation.resolve("note"))
        WorkspaceStore(context).use{s->Assert.assertTrue(s.syncablePreferences().containsKey("navHome"));Assert.assertFalse(s.syncablePreferences().containsKey("navUsage"));Assert.assertEquals("notes",org.json.JSONObject(s.exportJson()).getJSONObject("preferences").getString("navHome"))}
        ActivityScenario.launch(MainActivity::class.java).use{
            node(By.text("Ideias merecem espaço."))
            node(By.text("Mais")).click();settle()
            Assert.assertTrue(device.hasObject(By.text("Ferramentas")))
            val search=node(By.clazz("android.widget.EditText"));search.click();search.text="relogio";hideKeyboard();settle()
            node(By.text("Relógio mundial")).click();node(By.text("O mundo tem seus horários."))
            node(By.text("Mais")).click();settle();node(By.desc("Personalizar menus")).click();node(By.text("Personalizar menus"))
            val field=node(By.clazz("android.widget.EditText"));field.click();field.text="Clima";hideKeyboard();settle()
            node(By.desc("Favoritar Clima")).click();settle()
            WorkspaceStore(context).use{s->Assert.assertTrue(s.preferences()["favoriteModules"].orEmpty().split(',').contains("city"));Assert.assertEquals(fixture.notes,s.find(fixture.id)?.notes);Assert.assertTrue(s.preferences()["navRecent"].orEmpty().contains("worldclock"))}
            device.takeScreenshot(java.io.File("/sdcard/Download/veyra-navigation-2.7.png"))
        }
        ActivityScenario.launch(MainActivity::class.java).use{node(By.text("Ideias merecem espaço."));WorkspaceStore(context).use{s->Assert.assertTrue(s.preferences()["favoriteModules"].orEmpty().contains("city"));Assert.assertEquals("notes",s.preferences()["navHome"]);Assert.assertEquals(fixture.notes,s.find(fixture.id)?.notes)}}
    }
    @Test fun tabletUsesRailAndLightThemeWithoutLosingDestinations(){
        try{
            device.executeShellCommand("wm size 1280x800");device.executeShellCommand("wm density 160")
            WorkspaceStore(context).use{it.preference("theme","Claro")}
            ActivityScenario.launch(MainActivity::class.java).use{
                node(By.text("Ideias merecem espaço."));node(By.text("Mais")).click();node(By.text("Tudo no lugar."))
                node(By.text("Hoje"));node(By.text("Tarefas"));node(By.text("Finanças"));node(By.text("Notas"))
                device.takeScreenshot(java.io.File("/sdcard/Download/veyra-navigation-tablet-2.7.png"))
                WorkspaceStore(context).use{s->Assert.assertEquals(fixture.notes,s.find(fixture.id)?.notes)}
            }
        }finally{device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset")}
    }
}
