package app.veyra.feature.productivity
import app.veyra.model.*
object Catalog {
    val specs=listOf(
        ItemSpec("task","Tarefas","Produtividade",listOf(Field("priority","Prioridade",FieldKind.CHOICE,listOf("Baixa","Média","Alta","Urgente")),Field("status","Etapa",FieldKind.CHOICE,listOf("A fazer","Fazendo","Concluído")),Field("recurrence","Repetir",FieldKind.CHOICE,listOf("Nunca","Diária","Semanal","Mensal")),Field("reminder","Lembrete • HH:mm")),true),
        ItemSpec("project","Projetos","Produtividade",listOf(Field("deadline","Prazo",FieldKind.DATE))),
        ItemSpec("event","Calendário","Produtividade",listOf(Field("time","Horário • HH:mm"),Field("end","Fim • HH:mm"),Field("place","Local"),Field("reminder","Lembrete • HH:mm"))),
        ItemSpec("goal","Metas","Produtividade",listOf(Field("target","Objetivo numérico",FieldKind.DECIMAL),Field("progress","Progresso",FieldKind.DECIMAL),Field("deadline","Prazo",FieldKind.DATE))),
        ItemSpec("plan","Planejamento","Produtividade",listOf(Field("period","Período",FieldKind.CHOICE,listOf("Dia","Semana","Mês","Trimestre","Ano")),Field("review","Revisão e aprendizado"))),
        ItemSpec("inbox","Inbox","Produtividade",listOf(Field("read","Lido",FieldKind.CHOICE,listOf("Não","Sim"))),true)
    )
}
