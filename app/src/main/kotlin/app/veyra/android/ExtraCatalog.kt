package app.veyra.android

import app.veyra.model.*

/** Local records use the same encrypted backup, search and reminders as the workspace. */
object ExtraCatalog {
    private fun choice(key:String,label:String,vararg values:String)=Field(key,label,FieldKind.CHOICE,values.toList())
    val specs=listOf(
        ItemSpec("routine","Central de rotina","Seu dia"),
        ItemSpec("checklist","Checklists rápidos","Seu dia",listOf(Field("list","Nome da lista"),Field("priority","Prioridade",FieldKind.CHOICE,listOf("Baixa","Média","Alta"))),true),
        ItemSpec("countdown","Contagem regressiva","Seu dia",listOf(Field("category","Ocasião"))),
        ItemSpec("meal","Cardápio semanal","Casa e veículo",listOf(choice("meal","Refeição","Café da manhã","Almoço","Lanche","Jantar"),Field("ingredients","Ingredientes • uma linha por item"),Field("minutes","Preparo em minutos",FieldKind.DECIMAL)),true),
        ItemSpec("chore","Rotina da casa","Casa e veículo",listOf(Field("room","Cômodo"),Field("person","Responsável")),true),
        ItemSpec("pet","Cuidados dos pets","Casa e veículo",listOf(Field("pet","Nome do pet"),choice("care","Cuidado","Alimentação","Vacina","Passeio","Banho","Veterinário"),Field("time","Horário")),true),
        ItemSpec("medicine","Medicamentos","Bem-estar",listOf(Field("dose","Dose prescrita"),Field("time","Horário"),Field("stock","Quantidade em estoque",FieldKind.DECIMAL)),true),
        ItemSpec("appointment","Consultas e cuidados","Bem-estar",listOf(Field("place","Local / profissional"),Field("time","Horário"),Field("phone","Telefone")),true),
        ItemSpec("mood","Humor e energia","Bem-estar",listOf(choice("mood","Como você está?","Muito bem","Bem","Neutro","Mal","Muito mal"),choice("energy","Energia","Alta","Média","Baixa"),Field("reason","O que influenciou seu dia?"))),
        ItemSpec("measurement","Medidas corporais","Bem-estar",listOf(Field("weight","Peso em kg",FieldKind.DECIMAL),Field("waist","Cintura em cm",FieldKind.DECIMAL),Field("height","Altura em cm",FieldKind.DECIMAL))),
        ItemSpec("exercise","Séries de exercícios","Bem-estar",listOf(Field("sets","Séries",FieldKind.DECIMAL),Field("reps","Repetições",FieldKind.DECIMAL),Field("load","Carga em kg",FieldKind.DECIMAL),Field("minutes","Duração em minutos",FieldKind.DECIMAL)),true),
        ItemSpec("savings_goal","Metas financeiras","Finanças",listOf(Field("amount","Meta em R$",FieldKind.MONEY,required=true),Field("saved","Já reservado em R$",FieldKind.MONEY),Field("account","Conta",FieldKind.REFERENCE))),
        ItemSpec("debt","Dívidas e empréstimos","Finanças",listOf(Field("person","Pessoa / instituição"),choice("direction","Tipo","A pagar","A receber"),Field("amount","Valor total",FieldKind.MONEY,required=true),Field("paid","Valor quitado",FieldKind.MONEY)),true),
        ItemSpec("bill","Contas a pagar","Finanças",listOf(Field("amount","Valor",FieldKind.MONEY,required=true),Field("category","Categoria"),Field("account","Conta",FieldKind.REFERENCE)),true),
        ItemSpec("wishlist","Lista de desejos","Vida pessoal",listOf(Field("amount","Preço estimado",FieldKind.MONEY),Field("url","Link"),choice("priority","Prioridade","Baixa","Média","Alta")),true),
        ItemSpec("gift","Presentes","Vida pessoal",listOf(Field("person","Para quem"),Field("amount","Orçamento",FieldKind.MONEY),Field("occasion","Ocasião")),true),
        ItemSpec("course","Cursos e aprendizado","Estudos",listOf(Field("platform","Plataforma"),Field("lessons","Total de aulas",FieldKind.DECIMAL),Field("completed","Aulas concluídas",FieldKind.DECIMAL),Field("url","Link"))),
        ItemSpec("job","Vagas e candidaturas","Vida pessoal",listOf(Field("company","Empresa"),choice("status","Etapa","Interesse","Enviado","Entrevista","Proposta","Encerrado"),Field("url","Link"))),
        ItemSpec("subscription_audit","Revisão de serviços","Finanças",listOf(Field("amount","Custo mensal",FieldKind.MONEY),choice("use","Frequência de uso","Todo dia","Toda semana","Raramente","Nunca"),Field("renewal","Próxima renovação",FieldKind.DATE)))
    ).map { spec -> if(spec.checkable) spec.copy(fields=spec.fields+Field("reminder","Lembrete • HH:mm")) else spec }
}
