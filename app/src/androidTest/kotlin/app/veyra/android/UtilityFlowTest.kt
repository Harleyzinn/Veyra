package app.veyra.android

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.veyra.data.WorkspaceStore
import org.junit.*

class UtilityFlowTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun hideKeyboard(){android.os.SystemClock.sleep(400);if(device.hasObject(By.pkg(java.util.regex.Pattern.compile(".*inputmethod.*")))){device.pressBack();android.os.SystemClock.sleep(400)}}
    private fun click(text:String){repeat(12){val node=device.wait(Until.findObject(By.text(text)),1200);if(node!=null){node.click();android.os.SystemClock.sleep(400);device.waitForIdle();return};device.swipe(500,1700,500,800,25);device.waitForIdle()};Assert.fail("Não encontrado: $text")}
    private fun module(label:String){click("Mais");device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000).text=label;android.os.SystemClock.sleep(400);device.wait(Until.findObject(By.text(label).clazz("android.widget.TextView")),10000)?.click() ?: Assert.fail(label);android.os.SystemClock.sleep(500);device.waitForIdle()}
    @Before fun setup(){WorkspaceStore(context).use{store->store.preference("name","Ana");store.preference("lock","");store.preference("profile","Completo");store.preference("hidden","");store.preference("autoUpdateCheck","Não");store.preference("clockZones","America/Sao_Paulo|Asia/Tokyo")}}
    @After fun cleanup(){WorkspaceStore(context).use{store->store.all().filter{it.title=="Desenho QA" || it.title=="Resultado • Sortear opções"}.forEach{store.save(it.copy(deletedAt=System.currentTimeMillis()))};store.preference("autoUpdateCheck","Sim")}}
    @Test fun drawingCanBeSavedAndReopened(){ActivityScenario.launch(MainActivity::class.java).use{
        Assert.assertTrue(device.wait(Until.hasObject(By.text("Mais")),30000));module("Desenho e esboços")
        device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000).text="Desenho QA"
        hideKeyboard()
        repeat(4){if(device.hasObject(By.desc("Área de desenho")))return@repeat;device.swipe(500,1700,500,800,25);device.waitForIdle()}
        val canvas=device.wait(Until.findObject(By.desc("Área de desenho")),10000).visibleBounds
        device.swipe(canvas.left+80,canvas.centerY(),canvas.right-80,canvas.centerY(),35)
        click("Guardar no Veyra")
        android.os.SystemClock.sleep(800)
        WorkspaceStore(context).use{store->val sketch=store.all().first{it.title=="Desenho QA" && it.deletedAt==0L};Assert.assertEquals("sketch",sketch.type);val bytes=android.util.Base64.decode(sketch.value("attachment"),android.util.Base64.NO_WRAP);Assert.assertNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size))}
        click("Mais");module("Desenho e esboços");click("Galeria");click("Desenho QA")
        Assert.assertTrue(device.wait(Until.hasObject(By.text("Editar")),10000))
    }}
    @Test fun decisionsAreSavedAsNotes(){ActivityScenario.launch(MainActivity::class.java).use{
        Assert.assertTrue(device.wait(Until.hasObject(By.text("Mais")),30000));module("Sorteios, equipes e dados")
        val fields=device.findObjects(By.clazz("android.widget.EditText"));fields[0].text="Ana\nBia";fields[1].text="2";hideKeyboard()
        click("Sortear");click("Guardar nas notas");android.os.SystemClock.sleep(700)
        WorkspaceStore(context).use{store->val note=store.all().first{it.title=="Resultado • Sortear opções" && it.deletedAt==0L};Assert.assertTrue(note.notes.contains("Ana") && note.notes.contains("Bia"))}
    }}
    @Test fun ticTacToeRespondsToPlayerMove(){ActivityScenario.launch(MainActivity::class.java).use{
        Assert.assertTrue(device.wait(Until.hasObject(By.text("Mais")),30000));module("Jogos rápidos")
        device.wait(Until.findObject(By.desc("Casa 1, vazia")),10000).click()
        Assert.assertTrue(device.wait(Until.hasObject(By.desc("Casa 1, X")),10000));Assert.assertTrue(device.wait(Until.hasObject(By.desc("Casa 5, O")),10000))
        click("Nova partida");Assert.assertTrue(device.wait(Until.hasObject(By.desc("Casa 1, vazia")),10000))
    }}
    @Test fun worldClockRemembersZones(){ActivityScenario.launch(MainActivity::class.java).use{
        Assert.assertTrue(device.wait(Until.hasObject(By.text("Mais")),30000));module("Relógio mundial")
        click("America/Sao Paulo");click("Asia/Tokyo")
        WorkspaceStore(context).use{Assert.assertEquals("America/Sao_Paulo|Asia/Tokyo",it.preferences()["clockZones"])}
    }}
}
