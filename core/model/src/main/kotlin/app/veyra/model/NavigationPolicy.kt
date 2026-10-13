package app.veyra.model

/** Menu data never authorizes execution or removes a destination from discovery. */
object NavigationPolicy {
    fun push(stack:List<String>,id:String,available:Set<String>):List<String> = if(id !in available || stack.lastOrNull()==id)stack else (stack+id).takeLast(20)
    fun matches(text:String,query:String):Boolean {
        fun fold(value:String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.ROOT)
        return fold(text).contains(fold(query.trim()))
    }
    fun ids(raw:String,available:Set<String>)=raw.split(',').filter{it in available}.distinct()
    fun ordered(raw:String,available:List<String>):List<String> = ids(raw,available.toSet()) + available.filter{it !in ids(raw,available.toSet())}
    fun visit(raw:String,id:String,available:Set<String>)=if(id in available)(listOf(id)+ids(raw,available).filter{it!=id}).take(8).joinToString(",")else raw
    fun area(group:String)=when(group){
        "Produtividade","Notas e diário","Rotina"->"Produtividade"
        "Finanças"->"Finanças"
        "Sistema"->"Inteligência"
        "Documentos","Organização"->"Organização"
        else->"Vida pessoal"
    }
    val financeSections=linkedMapOf(
        "Dia a dia" to listOf("Resumo","Movimentações","A pagar","A receber","Calendário"),
        "Contas" to listOf("Contas","Cartões","Recorrências","Assinaturas"),
        "Planejamento" to listOf("Fluxo futuro","Simulador","Orçamentos","Metas","Dívidas","Patrimônio"),
        "Análise" to listOf("Conferência","Relatórios","Alertas"),
        "Administração" to listOf("Modelos e regras","Configurações","Lixeira")
    )
}
