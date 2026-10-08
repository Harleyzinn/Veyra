package app.veyra.android

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import app.veyra.model.Item
import app.veyra.feature.finance.FinancialDomain
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency

internal fun financeMoney(minor:Long,currency:String="BRL",hidden:Boolean=false):String{
    if(hidden)return "${Currency.getInstance(currency).symbol} ••••••"
    return NumberFormat.getCurrencyInstance(Brazilian).apply{this.currency=Currency.getInstance(currency)}.format(BigDecimal.valueOf(minor,FinancialDomain.scale(currency)))
}

object FinanceReportWriter {
    private fun cell(value:String):String {val guarded=if(value.trimStart(' ','\t','\r','\n').firstOrNull() in listOf('=','+','-','@'))"'"+value else value;return "\""+guarded.replace("\"","\"\"")+"\""}
    fun csv(entries:List<Item>,context:List<Item>):String=buildString{
        val names=context.associate{it.id to it.title}
        append("\uFEFFData;Descrição;Tipo;Valor;Moeda;Categoria;Subcategoria;Conta;Cartão;Status;Vencimento;Competência;Pessoa;Tags;Observações;Data de pagamento;Tipo de pagamento;Fatura;Versão financeira;Caixa legado\r\n")
        entries.forEach{e->append(listOf(e.date,e.title,e.type,FinancialDomain.decimal(FinancialDomain.amount(e),FinancialDomain.currency(e)),FinancialDomain.currency(e),e.value("category"),e.value("subcategory"),names[e.value("account")].orEmpty(),names[e.value("card")].orEmpty(),FinancialDomain.status(e).name,e.value("dueDate"),e.value("competence"),e.value("person"),e.tags,e.notes,e.value("settledDate"),e.value("paymentType"),e.value("invoiceId"),e.value("financialVersion"),e.value("legacyCardCash")).joinToString(";",transform=::cell));append("\r\n")}
    }
    fun pdf(context:Context,uri:Uri,title:String,summary:List<String>,entries:List<Item>,cashBasis:Boolean=false){
        val doc=PdfDocument()
        try{
            val lines=summary+listOf("")+entries.map{"${if(cashBasis)FinancialDomain.bookedDate(it).toString()else it.date}  ${it.title.take(38)}  ${financeMoney(FinancialDomain.amount(it),FinancialDomain.currency(it))}  ${financeStatusLabel(FinancialDomain.status(it).name)}"}
            val chunks=lines.chunked(39).ifEmpty{listOf(emptyList())}
            chunks.forEachIndexed{index,chunk->
                val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create());val canvas=page.canvas
                val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.rgb(30,30,40);textSize=11f}
                val heading=Paint(text).apply{typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD);textSize=20f}
                canvas.drawText("VEYRA · FINANÇAS",36f,48f,heading);canvas.drawText(title.take(75),36f,76f,text)
                chunk.forEachIndexed{n,line->canvas.drawText(line.take(105),36f,110f+n*17f,text)}
                canvas.drawText("${index+1}/${chunks.size} · Dados registrados pelo usuário",36f,811f,text);doc.finishPage(page)
            }
            context.contentResolver.openOutputStream(uri)?.use{doc.writeTo(it)} ?: error("Não foi possível abrir o arquivo de destino.")
        }finally{doc.close()}
    }
}
