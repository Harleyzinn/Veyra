package app.veyra.android
import app.veyra.model.*
object Registry {
    val specs=app.veyra.feature.productivity.Catalog.specs+app.veyra.feature.finance.Catalog.specs+app.veyra.feature.notes.Catalog.specs+app.veyra.feature.habits.Catalog.specs+app.veyra.feature.studies.Catalog.specs+app.veyra.feature.life.Catalog.specs+app.veyra.feature.automation.Catalog.specs+ExtraCatalog.specs+listOf(ItemSpec("sketch","Desenho e esboços","Criatividade"),ItemSpec("scanner","Leitor de QR e códigos","Ferramentas"),ItemSpec("decisions","Sorteios, equipes e dados","Ferramentas"),ItemSpec("playroom","Jogos rápidos","Lazer"),ItemSpec("worldclock","Relógio mundial","Ferramentas"),ItemSpec("city","Clima","Clima"),ItemSpec("tools","Ferramentas","Ferramentas"),ItemSpec("assistant","Assistente","Sistema"))
    fun spec(type:String)=specs.firstOrNull{it.type==type} ?: ItemSpec(type,type,"Sistema")
    val contexts=setOf("project","trip","subject","vehicle","asset")
    fun referenceTypes(key:String)=when(key){"account","destination"->setOf("account");"card"->setOf("card");else->contexts}
}
