package app.veyra.feature.studies
import app.veyra.model.*
object Catalog { val specs=listOf(
    ItemSpec("subject","Disciplinas","Estudos",listOf(Field("teacher","Professor"),Field("schedule","Horários"),Field("minimum","Média necessária",FieldKind.DECIMAL))),
    ItemSpec("exam","Provas e trabalhos","Estudos",listOf(Field("time","Horário"),Field("weight","Peso",FieldKind.DECIMAL),Field("grade","Nota",FieldKind.DECIMAL)),true),
    ItemSpec("grade","Notas escolares","Estudos",listOf(Field("grade","Nota",FieldKind.DECIMAL,required=true),Field("weight","Peso",FieldKind.DECIMAL))),
    ItemSpec("attendance","Frequência","Estudos",listOf(Field("present","Presente",FieldKind.CHOICE,listOf("Sim","Não")))),
    ItemSpec("flashcard","Flashcards","Estudos",listOf(Field("answer","Resposta",required=true),Field("interval","Intervalo • dias",FieldKind.DECIMAL))),
    ItemSpec("focus","Sessões de foco","Estudos",listOf(Field("minutes","Minutos",FieldKind.DECIMAL,required=true)))
) }
