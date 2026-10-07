package app.veyra.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.veyra.model.Item
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinancialPersistenceTest {
    @Test fun seriesMembersFindExactAliasesAcrossAllYears() = withStore { store ->
        store.save(transaction("old-series","2001-01-01",fields=mapOf("source" to "series")))
        store.save(transaction("future-series","2038-01-01",fields=mapOf("recurrenceRuleId" to "series")))
        store.save(transaction("other-series",fields=mapOf("source" to "series-other")))
        store.save(transaction("deleted-series",fields=mapOf("source" to "series")).copy(deletedAt=1))
        assertEquals(setOf("old-series","future-series"),store.financeSeriesMembers("series").map{it.id}.toSet())
        assertEquals(3,store.financeSeriesMembers("series",includeDeleted=true).size)
    }
    @Test fun movedDeletedAndPurgedOccurrencesKeepSuppressionInOriginalWindow() = withStore { store ->
        val deleted=transaction("recurring:series:2026-10-10","2027-01-10",fields=mapOf("source" to "series","occurrenceDate" to "2026-10-10")).copy(deletedAt=1)
        store.save(deleted)
        fun inWindow()=store.financeWindow("2026-10-01","2026-10-31").single{it.id==deleted.id}
        assertEquals(1L,inWindow().deletedAt)
        assertEquals(0L,store.cashNet(LocalDate.parse("2027-12-31")))
        store.purge(deleted.id)
        assertEquals("yes",inWindow().value("purged"))
        assertEquals("2026-10-10",inWindow().value("occurrenceDate"))
        assertTrue(store.financeTrash().items.isEmpty())
    }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun withStore(test: (WorkspaceStore) -> Unit) {
        val name = "finance-test-${UUID.randomUUID()}.db"
        try { WorkspaceStore(context, name).use(test) } finally { context.deleteDatabase(name) }
    }
    private fun transaction(id: String, date: String = "2026-10-06", amount: Long = 1090,
                            type: String = "expense", fields: Map<String, String> = emptyMap()) =
        Item(id = id, type = type, title = "Movimentação $id", date = date,
            fields = mapOf("amountMinor" to amount.toString(), "amount" to "10.90", "currency" to "BRL",
                "financialVersion" to "3", "status" to if(type=="income")"received"else"paid") + fields)

    @Test fun versionTwoMigrationKeepsRecordsAndBuildsCompactProjection() {
        val name = "v2-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                db.execSQL("CREATE TABLE entries (id TEXT NOT NULL PRIMARY KEY,kind TEXT NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL,amount INTEGER NOT NULL,date TEXT NOT NULL,done INTEGER NOT NULL,created INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX index_entries_kind_date ON entries(kind,date)")
                db.execSQL("CREATE TABLE items (id TEXT NOT NULL PRIMARY KEY,type TEXT NOT NULL,title TEXT NOT NULL,notes TEXT NOT NULL,date TEXT NOT NULL,done INTEGER NOT NULL,favorite INTEGER NOT NULL,tags TEXT NOT NULL,parentId TEXT NOT NULL,payload TEXT NOT NULL,deletedAt INTEGER NOT NULL,createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX index_items_type_date ON items(type,date)")
                db.execSQL("CREATE INDEX index_items_parentId ON items(parentId)")
                db.execSQL("CREATE TABLE preferences (`key` TEXT NOT NULL PRIMARY KEY,value TEXT NOT NULL)")
                db.execSQL("INSERT INTO items VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any>("old", "expense", "Café", "Original", "2026-10-05", 0, 0, "", "", "{\"amount\":\"10.90\",\"attachment\":\"aGVsbG8=\"}", 0, 123))
                db.version = 2
            }
            WorkspaceStore(context, name).use { store ->
                assertEquals("aGVsbG8=", store.find("old")!!.value("attachment"))
                assertEquals(1090L, store.cashNet(LocalDate.parse("2026-10-06")) * -1)
                val compact = store.queryFinance().items.single()
                assertEquals("", compact.value("attachment"))
                assertEquals("yes", compact.value("hasAttachment"))
                assertEquals(1L, store.stored("old")!!.localRevision)
                assertEquals(0, store.pendingSyncCount())
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun atomicSaveQueuesOneLatestRevisionAndReopensDurably() {
        val name = "outbox-${UUID.randomUUID()}.db"
        try {
            WorkspaceStore(context, name).use { store ->
                val original = transaction("stable")
                store.save(original); store.save(original.copy(title = "Editado"))
                assertEquals(1, store.pendingSyncCount())
                assertEquals(2L, store.pendingSync().single().localRevision)
                assertEquals(2, store.audit("stable").size)
            }
            WorkspaceStore(context, name).use { store ->
                assertEquals("Editado", store.find("stable")!!.title)
                assertEquals("Editado", store.pendingSync().single().item.title)
                assertEquals(-1090L, store.cashNet(LocalDate.parse("2026-10-06")))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun acknowledgementDoesNotDiscardEditMadeDuringUpload() = withStore { store ->
        val original = transaction("race")
        store.save(original)
        val uploading = store.pendingSync().single()
        store.save(original.copy(title = "Nova edição"))
        store.acknowledgeSync(uploading.operationId, 1, 200)
        val pending = store.pendingSync().single()
        assertEquals("Nova edição", pending.item.title)
        assertEquals(1L, pending.baseServerRevision)
        assertEquals(2L, pending.localRevision)
        store.acknowledgeSync(pending.operationId, 2, 300)
        assertEquals(0, store.pendingSyncCount())
        assertEquals(2L, store.stored("race")!!.serverRevision)
    }

    @Test fun remoteConflictPreservesLocalAndCanRebaseExplicitly() = withStore { store ->
        val local = transaction("conflict")
        store.save(local)
        val remote = RemoteItem(local.copy(title = "Outro aparelho"), 4, 500, "remote-operation")
        assertFalse(store.applyRemote(remote))
        assertEquals(local.title, store.find(local.id)!!.title)
        assertTrue(store.pendingSync().isEmpty())
        val conflict = store.conflicts().single()
        assertEquals("Outro aparelho", conflict.remoteItem.title)
        store.resolveConflict(conflict.id, useRemote = false)
        assertEquals(4L, store.pendingSync().single().baseServerRevision)
        assertEquals(local.title, store.pendingSync().single().item.title)
        assertTrue(store.conflicts().isEmpty())
    }

    @Test fun remoteConflictResolutionRetainsDisplacedVersionInAudit() = withStore { store ->
        val local = transaction("choose-cloud")
        store.save(local)
        val cloud = local.copy(title = "Versão da nuvem")
        store.applyRemote(RemoteItem(cloud, 2, 500, "cloud-op"))
        store.resolveConflict(store.conflicts().single().id, true)
        assertEquals(cloud, store.find(local.id))
        assertEquals(0, store.pendingSyncCount())
        assertTrue(store.audit(local.id).any { it.action == "conflict_remote" && it.before!!.title == local.title })
    }

    @Test fun softDeleteRestoreAndPurgedTombstoneRemainAuditable() = withStore { store ->
        val item = transaction("delete")
        store.save(item); store.trash(item.id)
        assertTrue(store.queryFinance().items.isEmpty())
        assertEquals(1, store.queryFinance(FinanceQuery(deletedOnly = true)).items.size)
        store.restore(item.id)
        assertEquals(1, store.queryFinance().items.size)
        store.trash(item.id); store.purge(item.id)
        assertTrue(store.find(item.id)!!.deletedAt > 0)
        assertEquals("Registro excluído", store.find(item.id)!!.title)
        assertEquals("yes", store.find(item.id)!!.value("purged"))
        assertTrue(store.financeTrash().items.isEmpty())
        try { store.restore(item.id); fail("A permanent tombstone cannot be restored") } catch (_: IllegalArgumentException) { }
        try { store.save(item); fail("Saving an old snapshot cannot undo permanent deletion") } catch (_: IllegalArgumentException) { }
        assertTrue(store.audit(item.id).any { it.action == "purge" })
        assertTrue(store.audit(item.id).any { it.action == "restore" })
        assertEquals(1, store.pendingSyncCount())
    }

    @Test fun stableCursorPagesDoNotDuplicateAndFiltersWork() = withStore { store ->
        store.saveAll((0..204).map { transaction("page-${it.toString().padStart(3,'0')}", fields = mapOf("category" to if(it%2==0)"Alimentação"else"Transporte")) })
        val first = store.queryFinance(limit = 100)
        val second = store.queryFinance(cursor = first.nextCursor, limit = 100)
        val third = store.queryFinance(cursor = second.nextCursor, limit = 100)
        assertEquals(205, (first.items+second.items+third.items).map { it.id }.distinct().size)
        assertNull(third.nextCursor)
        assertEquals(100, store.queryFinance(FinanceQuery(category = "Alimentação"), limit = 100).items.size)
        assertEquals(1, store.search("page-203").size)
        assertEquals(1, store.search("movimen page-203").size)
        assertEquals(100, store.search("alimentacao").size)
    }

    @Test fun exactPlanMembersAndReferencesIncludeHistoryOutsideTheLoadedWindow() = withStore { store ->
        val planId="plan/\"A\""
        val account=Item(id="historical-account",type="account",title="Conta")
        val card=Item(id="historical-card",type="card",title="Cartão")
        val old=transaction("old-reference","2001-01-01",fields=mapOf("account" to account.id))
        val rule=Item(id="reference-rule",type="recurring_rule",title="Regra",fields=mapOf("card" to card.id))
        val members=(0 until 120).map{n->transaction("exact-$n",LocalDate.parse("2027-01-01").plusMonths(n.toLong()).toString(),fields=mapOf("installmentPlanId" to planId)).copy(deletedAt=if(n==119)10L else 0L)}
        val similar=transaction("similar",fields=mapOf("installmentPlanId" to "$planId-other"))
        store.saveAll(listOf(account,card,old,rule,similar)+members)
        assertEquals(119,store.financePlanMembers(planId).size)
        assertEquals(120,store.financePlanMembers(planId,true).size)
        assertFalse(store.financePlanMembers(planId,true).any{it.id==similar.id})
        assertTrue(store.hasActiveFinanceReferences(account.id))
        assertTrue(store.hasActiveFinanceReferences(card.id))
        store.trash(old.id);store.trash(rule.id)
        assertFalse(store.hasActiveFinanceReferences(account.id))
        assertFalse(store.hasActiveFinanceReferences(card.id))
    }

    @Test fun financialTrashIncludesEntitiesPagesEmptyDatesAndExcludesPermanentTombstones() = withStore { store ->
        val entities=(0..204).map{n->Item(id="trash-${n.toString().padStart(3,'0')}",type=if(n%2==0)"financial_asset"else"account",title="Entidade $n",date="",deletedAt=10L)}
        val receipt=transaction("receipt-trash").copy(deletedAt=10L,fields=mapOf("amount" to "10.90","attachment" to "aGVsbG8="))
        store.saveAll(entities+receipt)
        store.purge(entities.first().id)
        val first=store.financeTrash(limit=100)
        val second=store.financeTrash(first.nextCursor,100)
        val third=store.financeTrash(second.nextCursor,100)
        val all=first.items+second.items+third.items
        assertEquals(205,all.size)
        assertEquals(205,all.map{it.id}.distinct().size)
        assertNull(third.nextCursor)
        assertTrue(all.any{it.type=="financial_asset"})
        assertTrue(all.any{it.type=="account"})
        assertFalse(all.any{it.id==entities.first().id})
        assertTrue(all.all{it.value("attachment").isBlank()})
        assertEquals("yes",all.single{it.id==receipt.id}.value("hasAttachment"))
    }

    @Test fun preferenceBackupKeepsDeviceExposureSettingsOutOfCloudSync() = withStore { store ->
        val settings=mapOf("financeHidden" to "Sim","financialDay" to "7","widgetFinance" to "Sim","financeNotificationValues" to "Sim","financeLock" to "Sim","lock" to "private-pin")
        settings.forEach{(key,value)->store.preference(key,value)}
        val preferences=JSONObject(store.exportJson()).getJSONObject("preferences")
        assertEquals("Sim",preferences.getString("financeHidden"))
        assertEquals("7",preferences.getString("financialDay"))
        assertEquals("Sim",preferences.getString("widgetFinance"))
        assertEquals("Sim",preferences.getString("financeNotificationValues"))
        assertFalse(preferences.has("financeLock"));assertFalse(preferences.has("lock"))
        assertTrue("financeHidden" in WorkspaceStore.SYNC_PREFERENCES)
        assertTrue("financialDay" in WorkspaceStore.SYNC_PREFERENCES)
        assertFalse("widgetFinance" in WorkspaceStore.SYNC_PREFERENCES)
        assertFalse("financeNotificationValues" in WorkspaceStore.SYNC_PREFERENCES)
        assertFalse("financeLock" in WorkspaceStore.SYNC_PREFERENCES)
    }

    @Test fun cashCarryIncludesHistoricalTransferOnceAndKeepsOpeningOriginal() = withStore { store ->
        val account = Item(id = "account-a", type = "account", title = "A", fields = mapOf("opening" to "10.00"))
        store.saveAll(listOf(account, transaction("old-in", "2026-09-01", 10000, "income", mapOf("account" to "account-a")),
            transaction("transfer", "2026-09-02", 2500, "transfer", mapOf("account" to "account-a", "destination" to "account-b")),
            transaction("new-expense", "2026-10-06", 1000, fields = mapOf("account" to "account-a"))))
        val window = store.financeWindow("2026-10-01", "2026-10-31")
        assertFalse(window.any { it.id == "old-in" || it.id == "transfer" })
        assertEquals(7500L, window.single { it.type=="cash_carry" && it.value("account")=="account-a" }.value("amountMinor").toLong())
        assertEquals(2500L, window.single { it.type=="cash_carry" && it.value("account")=="account-b" }.value("amountMinor").toLong())
        assertEquals("10.00", window.single { it.id==account.id }.value("opening"))
        assertEquals(9000L, store.cashNet(LocalDate.parse("2026-10-31")))
    }

    @Test fun cardCarryIncludesFutureInvoicesWithoutLoadingFuturePurchases() = withStore { store ->
        val card = Item(id = "card", type = "card", title = "Cartão", fields = mapOf("limit" to "2000", "closing" to "1", "due" to "10"))
        val future = transaction("future-purchase", "2027-01-05", 30000, fields = mapOf("card" to card.id, "status" to "pending", "dueDate" to "2027-01-10"))
        store.saveAll(listOf(card, future))
        val window = store.financeWindow("2026-10-01", "2026-10-31")
        assertFalse(window.any { it.id==future.id })
        val carry = window.single { it.type=="card_carry" }
        assertEquals(30000L, carry.value("amountMinor").toLong())
        assertEquals("2027-01-10", carry.value("dueDate"))
        assertEquals(0L, store.cashNet(LocalDate.parse("2027-01-31")))
    }

    @Test fun overviewNeverLoadsBinaryAndSavingSummaryDoesNotEraseReceipt() = withStore { store ->
        val item = transaction("receipt", fields = mapOf("attachment" to "aGVsbG8="))
        store.save(item)
        val summary = store.overview(LocalDate.parse("2026-10-06")).single { it.id==item.id }
        assertEquals("", summary.value("attachment"))
        store.save(summary.copy(title = "Renomeado"))
        assertEquals("aGVsbG8=", store.find(item.id)!!.value("attachment"))
        assertEquals("aGVsbG8=", store.pendingSync().single().item.value("attachment"))
    }

    @Test fun importFailureCannotPartiallyChangeIndexAuditOrOutbox() = withStore { store ->
        store.save(transaction("preserve"))
        val before = store.exportJson()
        val audit = store.audit("preserve")
        val operation = store.pendingSync().single().operationId
        val backup = JSONObject(before)
        backup.getJSONArray("items").put(WorkspaceStore.toJson(transaction("bad", amount = -1)))
        try { store.importJson(backup.toString()); fail("Negative amount accepted") } catch (_:IllegalArgumentException) { }
        assertEquals(before, store.exportJson())
        assertEquals(audit, store.audit("preserve"))
        assertEquals(operation, store.pendingSync().single().operationId)
        assertEquals(1, store.queryFinance().items.size)
        listOf(mapOf("amountMinor" to "invalid"),mapOf("amountMinor" to Long.MAX_VALUE.toString()),mapOf("dueDate" to "2026-99-40"),mapOf("currency" to "INVALID")).forEach{fields->
            val malformed=JSONObject(before)
            malformed.getJSONArray("items").put(WorkspaceStore.toJson(transaction("malformed",fields=fields)))
            try{store.importJson(malformed.toString());fail("Malformed finance accepted: $fields")}catch(_:IllegalArgumentException){}
            assertEquals(before,store.exportJson())
            assertEquals(operation,store.pendingSync().single().operationId)
        }
    }

    @Test fun physicalUidIsolationAllowsSameIdWithoutCrossAccountRead() {
        val previous = WorkspaceIdentity.activeUid(context)
        val firstUid = "test-a-${UUID.randomUUID()}"
        val secondUid = "test-b-${UUID.randomUUID()}"
        try {
            WorkspaceIdentity.setActiveUid(context, firstUid)
            WorkspaceStore(context).use { it.save(transaction("same-id").copy(title = "Conta A")) }
            WorkspaceIdentity.setActiveUid(context, secondUid)
            WorkspaceStore(context).use { store ->
                assertNull(store.find("same-id")); store.save(transaction("same-id").copy(title = "Conta B"))
                try { store.clearCacheWhenSynced(); fail("Pending upload cache was deleted") } catch (_:IllegalArgumentException) { }
            }
            WorkspaceIdentity.setActiveUid(context, firstUid)
            WorkspaceStore(context).use { assertEquals("Conta A", it.find("same-id")!!.title) }
            assertNotEquals(WorkspaceIdentity.databaseForUid(firstUid), WorkspaceIdentity.databaseForUid(secondUid))
        } finally {
            WorkspaceIdentity.setActiveUid(context, previous)
            context.deleteDatabase(WorkspaceIdentity.databaseForUid(firstUid))
            context.deleteDatabase(WorkspaceIdentity.databaseForUid(secondUid))
        }
    }
}
