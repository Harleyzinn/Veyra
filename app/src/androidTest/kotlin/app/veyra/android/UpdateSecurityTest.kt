package app.veyra.android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import java.io.File

class UpdateSecurityTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun currentVersionCannotBeInstalledAsAnUpdate(){
        val current=File(context.applicationInfo.sourceDir)
        val error=runCatching{GitHubUpdater.verify(context,current,GitHubUpdater.currentVersion(context))}.exceptionOrNull()
        Assert.assertNotNull(error);Assert.assertTrue(error!!.message.orEmpty().contains("não é mais recente"))
    }
    @Test fun corruptApkIsRejected(){val file=File(context.cacheDir,"invalid-update.apk");try{file.writeText("APK inválido");Assert.assertTrue(runCatching{GitHubUpdater.verify(context,file,"99.0.0")}.isFailure)}finally{file.delete()}}
}
