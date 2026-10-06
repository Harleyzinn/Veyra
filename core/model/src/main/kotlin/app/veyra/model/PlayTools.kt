package app.veyra.model

import kotlin.random.Random

object DecisionTools {
    fun options(text:String)=text.lines().map(String::trim).filter(String::isNotBlank).distinctBy{it.lowercase()}.also{require(it.size<=500){"Use até 500 opções"}}
    fun draw(text:String,count:Int,random:Random=Random.Default):List<String>{val values=options(text);require(count in 1..values.size){"Quantidade deve ficar entre 1 e ${values.size}"};return values.shuffled(random).take(count)}
    fun teams(text:String,count:Int,random:Random=Random.Default):List<List<String>>{val values=options(text);require(count in 1..values.size){"Quantidade de equipes inválida"};val shuffled=values.shuffled(random);return List(count){team->shuffled.filterIndexed{index,_->index%count==team}}}
    fun dice(count:Int,sides:Int,random:Random=Random.Default):List<Int>{require(count in 1..20 && sides in 2..1000);return List(count){random.nextInt(1,sides+1)}}
}

object TicTacToe {
    val lines=listOf(listOf(0,1,2),listOf(3,4,5),listOf(6,7,8),listOf(0,3,6),listOf(1,4,7),listOf(2,5,8),listOf(0,4,8),listOf(2,4,6))
    fun result(board:List<String>):String?{require(board.size==9);lines.forEach{line->val symbol=board[line.first()];if(symbol.isNotBlank() && line.all{board[it]==symbol})return symbol};return if(board.none{it.isBlank()})"Empate"else null}
    fun move(board:List<String>,cell:Int,symbol:String):List<String>{require(cell in 0..8 && symbol in listOf("X","O") && board[cell].isBlank() && result(board)==null);return board.toMutableList().apply{this[cell]=symbol}}
    fun bot(board:List<String>):Int? {
        if(result(board)!=null)return null
        val empty=board.indices.filter{board[it].isBlank()}
        fun winning(symbol:String)=empty.firstOrNull{result(move(board,it,symbol))==symbol}
        return winning("O") ?: winning("X") ?: if(4 in empty)4 else empty.firstOrNull{it in listOf(0,2,6,8)} ?: empty.firstOrNull()
    }
}
