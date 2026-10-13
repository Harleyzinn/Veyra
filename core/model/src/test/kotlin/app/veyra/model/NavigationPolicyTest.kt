package app.veyra.model
import kotlin.test.*
class NavigationPolicyTest {
    @Test fun discoveryAcceptsUnaccentedQueries(){assertTrue(NavigationPolicy.matches("Relógio mundial","relogio"));assertTrue(NavigationPolicy.matches("Finanças"," FINANCAS "));assertFalse(NavigationPolicy.matches("Notas","seguranca"))}
    @Test fun stalePreferencesNeverRemoveAvailableDestinations(){
        val ids=listOf("notes","task","finance","hub")
        assertEquals(listOf("task","notes","finance","hub"),NavigationPolicy.ordered("task,task,removed",ids))
        assertEquals(listOf("notes"),NavigationPolicy.ids("removed,notes,notes",ids.toSet()))
    }
    @Test fun recentVisitsAreUniqueAndBounded(){
        val ids=(0..15).map{"p$it"}.toSet();var recent=""
        ids.forEach{recent=NavigationPolicy.visit(recent,it,ids)}
        assertEquals(8,recent.split(',').size)
        assertTrue(NavigationPolicy.visit(recent,"p15",ids).startsWith("p15,"))
        assertEquals(recent,NavigationPolicy.visit(recent,"removed",ids))
    }
    @Test fun financialGroupsPreserveEveryOriginalSectionExactlyOnce(){
        val sections=NavigationPolicy.financeSections.values.flatten()
        assertEquals(21,sections.size);assertEquals(21,sections.toSet().size)
        assertTrue(listOf("Resumo","Recorrências","Lixeira","Configurações","Fluxo futuro","A pagar","A receber","Modelos e regras").all{it in sections})
    }
}
