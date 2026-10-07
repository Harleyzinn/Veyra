package app.veyra.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.veyra.model.*
import app.veyra.feature.finance.FinancialDomain

private data class FinanceInput(val key:String,val label:String,val money:Boolean=false,val options:List<String> = emptyList())
private fun financeInputs(type:String):List<FinanceInput> = when(type){
    "account"->listOf(FinanceInput("opening","Saldo inicial",true),FinanceInput("bank","Instituição"),FinanceInput("accountType","Tipo",options=listOf("Conta corrente","Conta digital","Poupança","Dinheiro","Carteira","Investimentos","Outras")),FinanceInput("icon","Ícone • wallet/bank/cash/savings"),FinanceInput("color","Cor • #RRGGBB"))
    "card"->listOf(FinanceInput("bank","Banco"),FinanceInput("brand","Bandeira"),FinanceInput("lastFour","Últimos quatro dígitos"),FinanceInput("limit","Limite",true),FinanceInput("closing","Dia de fechamento • 1–31"),FinanceInput("due","Dia de vencimento • 1–31"),FinanceInput("account","Conta para pagamento"),FinanceInput("color","Cor • #RRGGBB"))
    "budget"->listOf(FinanceInput("category","Categoria"),FinanceInput("amount","Limite mensal",true),FinanceInput("thresholds","Alertas em % • 50,75,90,100"))
    "savings_goal"->listOf(FinanceInput("amount","Valor objetivo",true),FinanceInput("saved","Já reservado",true),FinanceInput("monthlyContribution","Aporte mensal planejado",true),FinanceInput("targetDate","Data alvo • AAAA-MM-DD"),FinanceInput("goalType","Objetivo",options=listOf("Reserva de emergência","Viagem","Celular","Computador","Carro","Casa","Outro")),FinanceInput("reserveMonths","Reserva em meses • 3,6,12 ou personalizado"),FinanceInput("account","Conta vinculada"))
    "debt"->listOf(FinanceInput("person","Credor / pessoa"),FinanceInput("direction","Direção",options=listOf("A pagar","A receber")),FinanceInput("amount","Valor original",true),FinanceInput("paid","Valor quitado",true),FinanceInput("interest","Juros mensais em %"),FinanceInput("installments","Quantidade de parcelas"),FinanceInput("installmentAmount","Valor de parcela",true),FinanceInput("dueDate","Próximo vencimento • AAAA-MM-DD"))
    "financial_asset","investment"->listOf(FinanceInput("amount","Valor investido / aquisição",true),FinanceInput("current","Valor atual",true),FinanceInput("institution","Instituição / origem"),FinanceInput("assetType","Tipo",options=listOf("Investimento","Imóvel","Veículo","Dinheiro","Outro ativo")))
    "financial_category"->listOf(FinanceInput("kind","Utilizar em",options=listOf("income","expense")),FinanceInput("parent","Categoria principal • opcional"),FinanceInput("icon","Ícone"),FinanceInput("color","Cor • #RRGGBB"))
    "financial_rule"->listOf(FinanceInput("contains","Descrição contém"),FinanceInput("category","Aplicar categoria"),FinanceInput("subcategory","Aplicar subcategoria"),FinanceInput("kind","Tipo",options=listOf("expense","income")),FinanceInput("enabled","Ativa",options=listOf("yes","no")))
    else->listOf(FinanceInput("amount","Valor",true),FinanceInput("category","Categoria"))
}

internal fun financeEntityLabel(type:String)=mapOf("account" to "Conta ou carteira","card" to "Cartão de crédito","budget" to "Orçamento","savings_goal" to "Meta financeira","debt" to "Dívida ou empréstimo","financial_asset" to "Ativo do patrimônio","investment" to "Investimento","financial_category" to "Categoria","financial_rule" to "Regra automática")[type] ?: Registry.spec(type).label

@Composable fun FinanceEntityEditor(initial:Item,all:List<Item>,vm:VeyraViewModel,dismiss:()->Unit){
    var title by remember(initial.id){mutableStateOf(initial.title)};var notes by remember(initial.id){mutableStateOf(initial.notes)}
    val fields=remember(initial.id){mutableStateMapOf<String,String>().apply{putAll(initial.fields);putIfAbsent("currency",vm.preferences["financeCurrency"] ?: "BRL");if(initial.type=="debt")putIfAbsent("direction","A pagar");if(initial.type=="financial_rule")putIfAbsent("enabled","yes")}}
    var error by remember{mutableStateOf("")}
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth().padding(12.dp).imePadding().fillMaxHeight(.94f),shape=MaterialTheme.shapes.extraLarge){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(financeEntityLabel(initial.type),style=MaterialTheme.typography.headlineMedium)
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
                item{FinanceField("Nome",title,{title=it.take(200)})}
                item{Choice("Moeda",listOf("BRL","USD","EUR","GBP"),fields["currency"].orEmpty(),{fields["currency"]=it})}
                financeInputs(initial.type).forEach{input->item{
                    when{
                        input.options.isNotEmpty()->Choice(input.label,input.options,fields[input.key].orEmpty(),{fields[input.key]=it}){v->when(v){"income"->"Entradas";"expense"->"Gastos";"yes"->"Sim";"no"->"Não";else->v}}
                        input.key=="account"->Choice(input.label,listOf("")+all.filter{it.type=="account" && it.deletedAt==0L}.map{it.id},fields[input.key].orEmpty(),{fields[input.key]=it}){id->all.firstOrNull{it.id==id}?.title ?: "Nenhuma"}
                        else->FinanceField(input.label,fields[input.key].orEmpty(),{fields[input.key]=it},input.money || input.key in listOf("closing","due","interest","installments","reserveMonths"))
                    }
                }}
                item{FinanceField("Observações",notes,{notes=it},lines=3)}
                if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                TextButton(onClick=dismiss,Modifier.weight(1f)){Text("Cancelar")}
                Button(onClick={runCatching{
                    val result=initial.copy(title=title.trim(),notes=notes,fields=fields.toMap().filterKeys{!it.endsWith("Minor")}+mapOf("financialVersion" to "3"))
                    require(result.title.isNotBlank()){ "Informe o nome." }
                    financeInputs(initial.type).filter{it.money}.forEach{input->val value=result.value(input.key);if(value.isNotBlank())Money.parseCents(value)}
                    FinancialDomain.validate(result,all)
                    vm.commitFinance(listOf(result)){dismiss()}
                }.onFailure{error=it.message ?: "Confira os dados."}},enabled=!vm.busy,modifier=Modifier.weight(1f)){Text("Salvar")}
            }
        }}
    }
}
