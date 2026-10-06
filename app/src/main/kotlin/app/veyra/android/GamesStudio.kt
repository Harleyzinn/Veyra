package app.veyra.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import app.veyra.model.TicTacToe
import kotlinx.coroutines.delay

@Composable fun GamesStudio(){
    var game by rememberSaveable{mutableStateOf("Jogo da velha")}
    LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageHeading("UMA PAUSA LEVE","Jogue um pouco.","Sem anúncios. Sem internet. Só uma pausa no seu dia.")}
        item{Choice("Jogo",listOf("Jogo da velha","Memória","Adivinhe o número"),game,{game=it})}
        item{when(game){"Jogo da velha"->TicTacToePanel();"Memória"->MemoryPanel();else->GuessPanel()}}
        item{Spacer(Modifier.height(80.dp))}
    }
}
@Composable private fun TicTacToePanel(){
    var board by rememberSaveable{mutableStateOf(List(9){""})};var bot by rememberSaveable{mutableStateOf(true)};var turn by rememberSaveable{mutableStateOf("X")};var wins by rememberSaveable{mutableIntStateOf(0)}
    val outcome=TicTacToe.result(board)
    StudioCard{
        Row{Switch(bot,{bot=it;board=List(9){""};turn="X"});Text("Jogar contra o Veyra",Modifier.padding(start=12.dp,top=12.dp))}
        Text(if(outcome==null)"Vez de $turn"else if(outcome=="Empate")"Deu empate!"else "$outcome venceu!",style=MaterialTheme.typography.headlineSmall)
        for(row in 0..2)Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            for(column in 0..2){val cell=row*3+column;Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).semantics{contentDescription="Casa ${cell+1}, ${board[cell].ifBlank{"vazia"}}"}.clickable(enabled=outcome==null && board[cell].isBlank()){
                var next=TicTacToe.move(board,cell,turn)
                if(bot){if(TicTacToe.result(next)=="X")wins++;TicTacToe.bot(next)?.let{next=TicTacToe.move(next,it,"O")}}else turn=if(turn=="X")"O"else "X"
                board=next
            },contentAlignment=Alignment.Center){Text(board[cell],style=MaterialTheme.typography.displayMedium,color=if(board[cell]=="X")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)}}
        }
        if(bot)Text("Suas vitórias nesta sessão: $wins")
        Button(onClick={board=List(9){""};turn="X"}){Text("Nova partida")}
    }
}
@Composable private fun MemoryPanel(){
    var cards by rememberSaveable{mutableStateOf((0..7).flatMap{listOf(it,it)}.shuffled())};var matched by rememberSaveable{mutableStateOf(emptyList<Int>())};var selected by rememberSaveable{mutableStateOf(emptyList<Int>())};var moves by rememberSaveable{mutableIntStateOf(0)}
    val symbols=listOf("☀","☾","★","♥","♦","♣","♫","⚑")
    LaunchedEffect(selected){if(selected.size==2){delay(700);if(cards[selected[0]]==cards[selected[1]])matched=matched+selected;selected=emptyList()}}
    StudioCard{
        Text(if(matched.size==16)"Todos os pares encontrados!"else "${matched.size/2} de 8 pares • $moves tentativas",style=MaterialTheme.typography.titleLarge)
        for(row in 0..3)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            for(column in 0..3){val cell=row*4+column;val revealed=cell in matched || cell in selected
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(if(revealed)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable(enabled=!revealed && selected.size<2){selected=selected+cell;if(selected.size==2)moves++},contentAlignment=Alignment.Center){Text(if(revealed)symbols[cards[cell]]else "?",style=MaterialTheme.typography.headlineLarge)}
            }
        }
        Button(onClick={cards=(0..7).flatMap{listOf(it,it)}.shuffled();matched=emptyList();selected=emptyList();moves=0}){Text("Embaralhar novamente")}
    }
}
@Composable private fun GuessPanel(){
    var answer by rememberSaveable{mutableIntStateOf(kotlin.random.Random.nextInt(1,101))};var input by rememberSaveable{mutableStateOf("")};var attempts by rememberSaveable{mutableIntStateOf(0)};var result by rememberSaveable{mutableStateOf("Pensei em um número de 1 a 100.")};var won by rememberSaveable{mutableStateOf(false)}
    StudioCard{Text(result,style=MaterialTheme.typography.titleLarge);Text("$attempts tentativas");OutlinedTextField(input,{input=it},label={Text("Seu palpite")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(onClick={val value=input.toIntOrNull();if(value==null || value !in 1..100)result="Use um número de 1 a 100."else {attempts++;result=if(value==answer){won=true;"Acertou em $attempts tentativas!"}else if(value<answer)"Meu número é maior."else "Meu número é menor."}},enabled=!won){Text("Tentar")};TextButton(onClick={answer=kotlin.random.Random.nextInt(1,101);input="";attempts=0;won=false;result="Pensei em um número de 1 a 100."}){Text("Novo desafio")}}
}
