package app.veyra.feature.finance
import app.veyra.model.*
import java.time.LocalDate
import java.security.MessageDigest
import java.math.BigDecimal

object Statement {
    /** CSV header: date,title,amount,category. Negative amount = expense. Quotes/newlines accepted. */
    fun csv(text:String):List<Item> {
        val rows=parseRows(text)
        require(rows.isNotEmpty()){"Arquivo vazio"}
        val header=rows.first().map{it.trim().lowercase().removePrefix("\uFEFF")}
        val date=header.indexOf("date"); val title=header.indexOf("title");val amount=header.indexOf("amount");val category=header.indexOf("category")
        require(date>=0 && title>=0 && amount>=0){"Use cabeçalho date,title,amount,category"}
        return rows.drop(1).filter{it.any(String::isNotBlank)}.mapIndexed { index,row ->
            require(row.size==header.size){"Linha ${index+2}: colunas incompatíveis"}
            val d=LocalDate.parse(row[date]).toString(); val cents=Money.parseCents(row[amount])
            require(cents!=Long.MIN_VALUE && row[title].isNotBlank())
            imported(d,row[title],cents,if(category>=0) row[category] else "", "csv:${row.joinToString("|")}")
        }
    }
    fun ofx(text:String):List<Item> {
        val transactions=Regex("<STMTTRN>([\\s\\S]*?)</STMTTRN>",RegexOption.IGNORE_CASE).findAll(text).toList()
        require(transactions.isNotEmpty()){"Nenhuma transação OFX encontrada"}
        fun tag(body:String,key:String)=Regex("<$key>([^<\\r\\n]+)",RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.trim().orEmpty()
        return transactions.map { match ->
            val body=match.groupValues[1]; val d=tag(body,"DTPOSTED").take(8)
            require(d.length==8);val date=LocalDate.of(d.take(4).toInt(),d.substring(4,6).toInt(),d.substring(6,8).toInt()).toString()
            val amount=Money.parseCents(tag(body,"TRNAMT"));require(amount!=Long.MIN_VALUE)
            val title=tag(body,"NAME").ifBlank{tag(body,"MEMO")}.ifBlank{"Lançamento importado"}
            val source=tag(body,"FITID").ifBlank{body}
            imported(date,title,amount,"", "ofx:$source")
        }
    }
    private fun imported(date:String,title:String,cents:Long,category:String,source:String)=Item(
        id="import:"+MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString(""){"%02x".format(it)},
        type=if(cents<0) "expense" else "income",title=title.take(200),date=date,
        fields=mapOf("amount" to BigDecimal.valueOf(kotlin.math.abs(cents),2).toPlainString(),"category" to category,"imported" to "Sim"))
    fun export(items:List<Item>):String {
        fun escape(value:String):String { val safe=if(value.firstOrNull() in listOf('=','+','-','@')) "'$value" else value;return "\"${safe.replace("\"","\"\"")}\"" }
        return "date,title,amount,category\r\n"+items.filter{it.type in listOf("income","expense")}.joinToString("\r\n"){
            listOf(it.date,escape(it.title),(if(it.type=="expense") "-" else "")+it.value("amount"),escape(it.value("category"))).joinToString(",")
        }
    }
    /** Complete ledger for spreadsheet analysis. Restore full records with JSON backup. */
    fun report(items:List<Item>,workspace:List<Item>):String {
        fun escape(value:String):String{val safe=if(value.firstOrNull() in listOf('=','+','-','@'))"'$value"else value;return "\"${safe.replace("\"","\"\"")}\""}
        fun name(id:String)=workspace.firstOrNull{it.id==id && it.deletedAt==0L}?.title.orEmpty()
        return "data,descricao,tipo,valor,categoria,conta,destino,cartao,status,tags,notas\r\n"+items.filter{it.deletedAt==0L && it.type in setOf("income","expense","transfer")}.joinToString("\r\n"){i->
            val type=when(i.type){"income"->"Receita";"expense"->"Despesa";else->"Transferência"}
            val amount=BigDecimal.valueOf(if(i.type=="expense")-i.cents()else i.cents(),2).toPlainString()
            listOf(escape(i.date),escape(i.title),escape(type),amount,escape(i.value("category")),escape(name(i.value("account"))),escape(name(i.value("destination"))),escape(name(i.value("card"))),escape(if(i.value("planned")=="Sim" && !i.done)"Previsto"else "Realizado"),escape(i.tags),escape(i.notes)).joinToString(",")
        }
    }
    private fun parseRows(text:String):List<List<String>> {
        val rows=mutableListOf<List<String>>();var row=mutableListOf<String>();val field=StringBuilder();var quoted=false;var i=0
        while(i<text.length) { val ch=text[i]
            when { ch=='"' -> if(quoted && i+1<text.length && text[i+1]=='"'){field.append('"');i++}else quoted=!quoted
                ch==',' && !quoted -> {row.add(field.toString());field.clear()}
                (ch=='\n' || ch=='\r') && !quoted -> {if(ch=='\r' && i+1<text.length && text[i+1]=='\n') i++;row.add(field.toString());field.clear();rows.add(row);row=mutableListOf()}
                else -> field.append(ch)
            };i++
        }
        require(!quoted){"CSV com aspas abertas"}
        if(field.isNotEmpty() || row.isNotEmpty()){row.add(field.toString());rows.add(row)}
        require(rows.size<=20001){"Extrato excede 20 mil linhas"};return rows
    }
}
