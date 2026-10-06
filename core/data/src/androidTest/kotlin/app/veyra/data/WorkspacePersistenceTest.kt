package app.veyra.data
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.database.sqlite.SQLiteDatabase
import app.veyra.model.*
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorkspacePersistenceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun migrationPreservesOldMoneyAndIdentifiers(){val name="migration-${UUID.randomUUID()}.db";val path=context.getDatabasePath(name);path.parentFile?.mkdirs()
        try{SQLiteDatabase.openOrCreateDatabase(path,null).use{db->
            db.execSQL("CREATE TABLE entries (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, amount INTEGER NOT NULL, date TEXT NOT NULL, done INTEGER NOT NULL, created INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX index_entries_kind_date ON entries(kind,date)")
            db.execSQL("INSERT INTO entries VALUES ('old','EXPENSE','Mercado','Preservado',12345,'2026-10-05',0,123)");db.version=1
        };WorkspaceStore(context,name).use{store->val item=store.all().single();Assert.assertEquals("old",item.id);Assert.assertEquals(12345L,item.cents());Assert.assertEquals("Preservado",item.notes)}}finally{context.deleteDatabase(name)}
    }
    @Test fun encryptedBackupRoundtripAndWrongPasswordPreserveData(){val name="backup-${UUID.randomUUID()}.db"
        try{WorkspaceStore(context,name).use{store->val item=Item(type="trip",title="Viagem",fields=mapOf("attachment" to "aGVsbG8="));store.save(item);store.preference("lock","must-not-export");val text=store.exportJson();Assert.assertFalse(text.contains("must-not-export"));val encrypted=BackupCrypto.encrypt(text,"password123");Assert.assertFalse(encrypted.contains("Viagem"));Assert.assertEquals(text,BackupCrypto.decrypt(encrypted,"password123"));try{BackupCrypto.decrypt(encrypted,"wrong-password");Assert.fail()}catch(_:Exception){};store.importJson(BackupCrypto.decrypt(encrypted,"password123"));Assert.assertEquals(item,store.all().single())}}finally{context.deleteDatabase(name)}
    }
    @Test fun malformedWorkspaceBackupCannotPartiallyWrite(){val name="atomic-${UUID.randomUUID()}.db"
        try{WorkspaceStore(context,name).use{store->store.save(Item(type="note",title="Preservar"));val before=store.exportJson();val json=org.json.JSONObject(before);val bad=WorkspaceStore.toJson(Item(type="expense",title="Inválida",fields=mapOf("amount" to "-5")));json.getJSONArray("items").put(bad);try{store.importJson(json.toString());Assert.fail()}catch(_:IllegalArgumentException){};Assert.assertEquals(before,store.exportJson())}}finally{context.deleteDatabase(name)}
    }
}
