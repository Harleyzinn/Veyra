package app.veyra.feature.notes
import app.veyra.model.*
object Catalog { val specs=listOf(
    ItemSpec("note","Notas","Notas e diário",listOf(Field("folder","Pasta"),Field("archived","Arquivada",FieldKind.CHOICE,listOf("Não","Sim")))),
    ItemSpec("journal","Diário e humor","Notas e diário",listOf(Field("mood","Humor",FieldKind.CHOICE,listOf("Muito feliz","Feliz","Neutro","Triste","Muito triste")),Field("gratitude","Gratidão"))),
    ItemSpec("link","Links e favoritos","Notas e diário",listOf(Field("url","URL")))
) }
