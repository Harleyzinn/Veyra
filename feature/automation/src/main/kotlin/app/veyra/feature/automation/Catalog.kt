package app.veyra.feature.automation
import app.veyra.model.*
object Catalog { val specs=listOf(
    ItemSpec("rule","Automações","Sistema",listOf(Field("condition","Quando",FieldKind.CHOICE,listOf("Tarefa atrasada","Orçamento em 80%","Produto vence em 7 dias","Manutenção próxima"),true),Field("enabled","Ativa",FieldKind.CHOICE,listOf("Sim","Não")))),
    ItemSpec("template","Templates","Sistema",listOf(Field("lines","Tarefas • uma por linha",required=true)))
) }
