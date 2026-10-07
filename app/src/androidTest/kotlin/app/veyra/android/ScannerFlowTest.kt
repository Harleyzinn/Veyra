package app.veyra.android

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.veyra.data.WorkspaceStore
import com.google.zxing.*
import org.junit.*

class ScannerFlowTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun clickFresh(selector:BySelector){
        repeat(4){
            device.waitForIdle()
            try{(device.wait(Until.findObject(selector),5000) ?: error("Elemento não encontrado: $selector")).click();return}
            catch(_:StaleObjectException){android.os.SystemClock.sleep(200)}
        }
        error("O seletor continuou mudando durante o toque: $selector")
    }
    @Test fun imageCodeIsDecodedAndSavedAsNote(){
        WorkspaceStore(context).use{store->store.preference("name","Ana");store.preference("lock","");store.preference("profile","Completo");store.preference("hidden","");store.preference("autoUpdateCheck","Não")}
        val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"Veyra-QR-QA.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")}
        val uri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        val matrix=MultiFormatWriter().encode("veyra-qa-codigo-seguro",BarcodeFormat.QR_CODE,600,600)
        val pixels=IntArray(600*600){n->if(matrix[n%600,n/600])android.graphics.Color.BLACK else android.graphics.Color.WHITE}
        val bitmap=android.graphics.Bitmap.createBitmap(pixels,600,600,android.graphics.Bitmap.Config.ARGB_8888)
        context.contentResolver.openOutputStream(uri)!!.use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        try{ActivityScenario.launch(MainActivity::class.java).use{
            Assert.assertTrue(device.wait(Until.hasObject(By.text("Mais")),30000));device.findObject(By.text("Mais")).click();android.os.SystemClock.sleep(500)
            device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000).text="Leitor de QR e códigos";android.os.SystemClock.sleep(500)
            device.wait(Until.findObject(By.text("Leitor de QR e códigos").clazz("android.widget.TextView")),10000).click();android.os.SystemClock.sleep(500)
            device.waitForIdle()
            clickFresh(By.text("Escolher imagem"))
            if(!device.wait(Until.gone(By.pkg(context.packageName)),3000)){
                device.waitForIdle()
                clickFresh(By.text("Escolher imagem"))
            }
            Assert.assertTrue("O seletor de imagens não abriu",device.wait(Until.gone(By.pkg(context.packageName)),10000))
            android.os.SystemClock.sleep(1000);device.executeShellCommand("screencap -p /sdcard/Download/scanner-picker.png")
            var photo=device.wait(Until.findObject(By.textContains("Veyra-QR-QA")),3000) ?: device.wait(Until.findObject(By.descContains("Veyra-QR-QA")),1000)
            if(photo==null){
                clickFresh(By.desc("Show roots"))
                clickFresh(By.text("Recent"))
                photo=device.wait(Until.findObject(By.textContains("Veyra-QR-QA")),10000) ?: device.wait(Until.findObject(By.descContains("Veyra-QR-QA")),3000)
            }
            Assert.assertNotNull("Foto de teste não encontrada no seletor",photo)
            val tile=photo!!.parent.visibleBounds;device.click(tile.centerX(),tile.centerY())
            val decoded=device.wait(Until.hasObject(By.text("veyra-qa-codigo-seguro")),15000)
            device.executeShellCommand("screencap -p /sdcard/Download/scanner-result.png")
            Assert.assertTrue("Código não apareceu após escolher a imagem; pacote ${device.currentPackageName}",decoded)
            device.findObject(By.text("Guardar nas notas")).click();android.os.SystemClock.sleep(700)
            WorkspaceStore(context).use{store->Assert.assertTrue(store.all().any{it.notes=="veyra-qa-codigo-seguro" && it.type=="note" && it.deletedAt==0L})}
        }}finally{context.contentResolver.delete(uri,null,null);WorkspaceStore(context).use{store->store.all().filter{it.notes=="veyra-qa-codigo-seguro"}.forEach{store.save(it.copy(deletedAt=System.currentTimeMillis()))};store.preference("autoUpdateCheck","Sim")}}
    }
}
