package app.veyra.feature.habits
import app.veyra.model.*
object Catalog { val specs=listOf(
    ItemSpec("habit","Hábitos","Rotina",listOf(Field("frequency","Frequência",FieldKind.CHOICE,listOf("Diária","Dias úteis","Fim de semana")),Field("target","Meta diária",FieldKind.DECIMAL),Field("reminder","Lembrete • HH:mm"))),
    ItemSpec("water","Água","Rotina",listOf(Field("ml","Volume • ml",FieldKind.DECIMAL,required=true))),
    ItemSpec("health","Saúde e medições","Rotina",listOf(Field("metric","Métrica"),Field("value","Valor",FieldKind.DECIMAL),Field("unit","Unidade"))),
    ItemSpec("sleep","Sono","Rotina",listOf(Field("hours","Horas dormidas",FieldKind.DECIMAL),Field("quality","Qualidade",FieldKind.CHOICE,listOf("Boa","Regular","Ruim")))),
    ItemSpec("workout","Treinos","Rotina",listOf(Field("minutes","Duração • min",FieldKind.DECIMAL),Field("exercise","Exercícios e séries")))
) }
