package app.veyra.feature.life
import app.veyra.model.*
object Catalog { val specs=listOf(
    ItemSpec("trip","Viagens","Vida pessoal",listOf(Field("destination","Destino"),Field("endDate","Volta",FieldKind.DATE),Field("amount","Orçamento",FieldKind.MONEY))),
    ItemSpec("reservation","Reservas e roteiro","Vida pessoal",listOf(Field("place","Local"),Field("code","Código da reserva"),Field("time","Horário"))),
    ItemSpec("packing","Mala e checklists","Vida pessoal",listOf(Field("category","Categoria"),Field("quantity","Quantidade",FieldKind.DECIMAL)),true),
    ItemSpec("contact","Contatos importantes","Vida pessoal",listOf(Field("phone","Telefone"),Field("email","Email"),Field("category","Categoria"))),
    ItemSpec("birthday","Aniversários e datas","Vida pessoal",listOf(Field("birth","Data de nascimento",FieldKind.DATE))),
    ItemSpec("vehicle","Veículos","Casa e veículo",listOf(Field("plate","Placa"),Field("model","Modelo"),Field("year","Ano",FieldKind.DECIMAL))),
    ItemSpec("fuel","Abastecimentos","Casa e veículo",listOf(Field("liters","Litros",FieldKind.DECIMAL,required=true),Field("amount","Total",FieldKind.MONEY,required=true),Field("odometer","Quilometragem",FieldKind.DECIMAL,required=true))),
    ItemSpec("maintenance","Manutenções","Casa e veículo",listOf(Field("amount","Custo",FieldKind.MONEY),Field("next","Próxima revisão",FieldKind.DATE)),true),
    ItemSpec("shopping","Lista de compras","Casa e veículo",listOf(Field("quantity","Quantidade",FieldKind.DECIMAL),Field("unit","Unidade"),Field("amount","Preço unitário",FieldKind.MONEY),Field("category","Categoria")),true),
    ItemSpec("pantry","Despensa","Casa e veículo",listOf(Field("quantity","Quantidade",FieldKind.DECIMAL),Field("expiry","Validade",FieldKind.DATE))),
    ItemSpec("recipe","Receitas culinárias","Casa e veículo",listOf(Field("ingredients","Ingredientes • uma linha por item"),Field("minutes","Preparo • min",FieldKind.DECIMAL),Field("servings","Porções",FieldKind.DECIMAL))),
    ItemSpec("asset","Patrimônio e garantias","Casa e veículo",listOf(Field("amount","Valor",FieldKind.MONEY),Field("warranty","Garantia até",FieldKind.DATE),Field("serial","Número de série"))),
    ItemSpec("document","Documentos","Vida pessoal",listOf(Field("expiry","Vencimento",FieldKind.DATE))),
    ItemSpec("book","Livros","Biblioteca",listOf(Field("author","Autor"),Field("pages","Total de páginas",FieldKind.DECIMAL),Field("progress","Página atual",FieldKind.DECIMAL),Field("rating","Avaliação • 0 a 5",FieldKind.DECIMAL))),
    ItemSpec("movie","Filmes e séries","Biblioteca",listOf(Field("status","Status",FieldKind.CHOICE,listOf("Quero assistir","Assistindo","Concluído","Abandonado")),Field("progress","Episódio",FieldKind.DECIMAL),Field("rating","Avaliação • 0 a 5",FieldKind.DECIMAL))),
    ItemSpec("game","Jogos","Biblioteca",listOf(Field("platform","Plataforma"),Field("status","Status",FieldKind.CHOICE,listOf("Backlog","Jogando","Zerado","Abandonado")),Field("hours","Horas jogadas",FieldKind.DECIMAL)))
) }
