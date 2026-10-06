package app.veyra.feature.finance
import kotlin.test.*
class StatementTest {
    @Test fun quotedCsvAndDuplicatesAreStable(){val csv="date,title,amount,category\r\n2026-10-05,\"Mercado, bairro\",-12.34,Alimentação\r\n";val a=Statement.csv(csv).single();assertEquals("expense",a.type);assertEquals(1234L,a.cents());assertEquals("Mercado, bairro",a.title);assertEquals(a.id,Statement.csv(csv).single().id)}
    @Test fun ofxHandlesSgmlAndMoneySign(){val text="<STMTTRN><DTPOSTED>20261005120000\n<TRNAMT>-20.99\n<FITID>stable-id\n<NAME>Posto\n</STMTTRN>";val item=Statement.ofx(text).single();assertEquals("2026-10-05",item.date);assertEquals(2099L,item.cents());assertEquals("expense",item.type)}
    @Test fun invalidCsvHasNoPartialOutput(){assertFails{Statement.csv("date,title,amount\n2026-10-05,Ok,10\ninvalid,Invalid,20")}}
}
