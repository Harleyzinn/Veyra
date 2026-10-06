package app.veyra.feature.finance
import app.veyra.model.Item
import kotlin.test.*
class LedgerReportTest {
    @Test fun ledgerIncludesTransferStatusAndSafeText(){
        val account=Item(id="a",type="account",title="Carteira")
        val tx=Item(type="transfer",title="=HYPERLINK(1)",fields=mapOf("amount" to "10.50","account" to "a","planned" to "Sim"))
        val report=Statement.report(listOf(tx,tx.copy(deletedAt=1)),listOf(account))
        assertTrue(report.contains("Transferência"));assertTrue(report.contains("10.50"));assertTrue(report.contains("Carteira"));assertTrue(report.contains("Previsto"));assertTrue(report.contains("'=HYPERLINK(1)"));assertEquals(2,report.lines().size)
    }
}
