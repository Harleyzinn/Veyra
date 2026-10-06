package app.veyra.feature.finance
import app.veyra.model.*
object Catalog {
    private val transaction=listOf(Field("amount","Valor em R$",FieldKind.MONEY,required=true),Field("category","Categoria"),Field("account","Conta",FieldKind.REFERENCE),Field("card","Cartão",FieldKind.REFERENCE),Field("planned","Previsto",FieldKind.CHOICE,listOf("Não","Sim")))
    val specs=listOf(
        ItemSpec("expense","Despesas","Finanças",transaction), ItemSpec("income","Receitas","Finanças",transaction),
        ItemSpec("account","Contas e carteiras","Finanças",listOf(Field("opening","Saldo inicial",FieldKind.MONEY),Field("bank","Banco / instituição"))),
        ItemSpec("transfer","Transferências","Finanças",listOf(Field("amount","Valor",FieldKind.MONEY,required=true),Field("account","Origem",FieldKind.REFERENCE,required=true),Field("destination","Destino",FieldKind.REFERENCE,required=true))),
        ItemSpec("card","Cartões","Finanças",listOf(Field("limit","Limite",FieldKind.MONEY),Field("closing","Dia de fechamento",FieldKind.DECIMAL),Field("due","Dia de vencimento",FieldKind.DECIMAL))),
        ItemSpec("budget","Orçamentos","Finanças",listOf(Field("category","Categoria",required=true),Field("amount","Limite mensal",FieldKind.MONEY,required=true))),
        ItemSpec("subscription","Assinaturas e recorrências","Finanças",transaction,true),
        ItemSpec("installment_plan","Compras parceladas","Finanças",listOf(Field("amount","Total",FieldKind.MONEY),Field("count","Parcelas",FieldKind.DECIMAL))),
        ItemSpec("investment","Investimentos manuais","Finanças",listOf(Field("amount","Valor investido",FieldKind.MONEY),Field("current","Valor atual",FieldKind.MONEY),Field("institution","Instituição")))
    )
}
