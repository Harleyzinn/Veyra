package app.veyra.android

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.veyra.data.WorkspaceIdentity
import app.veyra.data.WorkspaceStore
import app.veyra.model.Item
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FinancePortalTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture=Item(id="portal-native-qa",type="account",title="Banco QA Portal",fields=mapOf("openingMinor" to "123456","currency" to "BRL"))
    private var original=emptyMap<String,String>()
    @Before fun prepare(){
        Assume.assumeTrue(WorkspaceIdentity.activeUid(context)==null)
        WorkspaceStore(context).use{s->original=s.preferences();s.preference("lock","");s.preference("financeLock","Não");s.preference("financePortal","{\"onboarded\":\"yes\",\"theme\":\"emerald\"}")}
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply{wakeUp();executeShellCommand("wm dismiss-keyguard")}
    }
    @After fun cleanup(){WorkspaceStore(context).use{s->for(k in listOf("lock","financeLock","financePortal"))s.preference(k,original[k].orEmpty());s.find(fixture.id)?.let{s.save(it.copy(deletedAt=System.currentTimeMillis()))}}}
    private fun web(view:View):WebView?{if(view is WebView)return view;if(view is ViewGroup)for(n in 0 until view.childCount)web(view.getChildAt(n))?.let{return it};return null}
    private fun eval(scenario:ActivityScenario<MainActivity>,script:String):String{
        var value="null";val latch=CountDownLatch(1)
        scenario.onActivity{a->val browser=web(a.window.decorView);if(browser==null)latch.countDown()else browser.evaluateJavascript(script){value=it;latch.countDown()}}
        Assert.assertTrue(latch.await(10,TimeUnit.SECONDS));return value
    }
    private fun waitFor(scenario:ActivityScenario<MainActivity>,script:String):String{
        val end=SystemClock.elapsedRealtime()+40_000
        while(SystemClock.elapsedRealtime()<end){val result=eval(scenario,script);if(result!="null" && result!="false")return result;SystemClock.sleep(150)}
        throw AssertionError("O portal não respondeu ao teste.")
    }
    private fun call(scenario:ActivityScenario<MainActivity>,method:String,payload:JSONObject=JSONObject()):JSONObject{
        eval(scenario,"window.__qa=null;window.omegaFinanceReply=(id,result)=>{window.__qa=JSON.parse(result)};window.VeyraFinance.request('qa',${JSONObject.quote(method)},${JSONObject.quote(payload.toString())});")
        val result=waitFor(scenario,"window.__qa ? JSON.stringify(window.__qa) : null")
        return JSONObject(JSONTokener(result).nextValue() as String)
    }
    @Test fun bundledUiReadsWritesOriginalBankAndRejectsStaleUpdates(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            waitFor(scenario,"!!window.VeyraFinance && !!document.querySelector('.hero')")
            val initial=call(scenario,"snapshot");Assert.assertFalse(initial.has("error"))
            val request=JSONObject().put("expectedRevision",initial.getLong("revision")).put("items",org.json.JSONArray().put(WorkspaceStore.toJson(fixture))).put("preferences",initial.getJSONObject("preferences"))
            val saved=call(scenario,"save",request);Assert.assertFalse(saved.has("error"))
            WorkspaceStore(context).use{Assert.assertEquals(123456L,it.find(fixture.id)!!.cents("opening"))}
            Assert.assertTrue(call(scenario,"save",request).has("error"))
            scenario.recreate();waitFor(scenario,"!!document.querySelector('.hero')")
            val reopened=call(scenario,"snapshot");Assert.assertTrue(reopened.getJSONArray("items").toString().contains(fixture.id))
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(java.io.File("/sdcard/Download/veyra-finance30-native.png"))
        }
    }
    @Test fun salarySchedulesAreClassifiedAsFinancialData(){Assert.assertTrue(financeSensitive(Item(type="salary_schedule",title="Salário privado"),emptyList()))}
    @Test fun newPreferencesDoNotBreakExistingCloudSettingsRules(){Assert.assertFalse(WorkspaceStore.SYNC_PREFERENCES.contains("financePortal"))}
}
