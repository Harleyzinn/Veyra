package app.veyra.model

import kotlin.test.*
import kotlin.random.Random

class PlayToolsTest {
    @Test fun drawsAreUniqueAndNormalizeDuplicateNames(){val drawn=DecisionTools.draw("Ana\n ana \nBia\nCaio",3,Random(1));assertEquals(setOf("Ana","Bia","Caio"),drawn.toSet());assertFailsWith<IllegalArgumentException>{DecisionTools.draw("Ana",2)}}
    @Test fun teamsHaveEveryPlayerOnceAndBalancedSizes(){val teams=DecisionTools.teams((1..11).joinToString("\n"),3,Random(2));assertEquals(11,teams.flatten().distinct().size);assertTrue(teams.maxOf{it.size}-teams.minOf{it.size}<=1);assertFailsWith<IllegalArgumentException>{DecisionTools.teams("Ana",0)}}
    @Test fun diceRespectBounds(){val values=DecisionTools.dice(20,6,Random(3));assertEquals(20,values.size);assertTrue(values.all{it in 1..6});assertFailsWith<IllegalArgumentException>{DecisionTools.dice(21,6)}}
    @Test fun detectsWinTieAndIllegalMove(){assertEquals("X",TicTacToe.result(listOf("X","X","X","O","O","","","","")));assertEquals("Empate",TicTacToe.result(listOf("X","O","X","X","O","O","O","X","X")));assertFailsWith<IllegalArgumentException>{TicTacToe.move(listOf("X","","","","","","","",""),0,"O")}}
    @Test fun botWinsBeforeBlockingAndBlocksHumanWin(){assertEquals(5,TicTacToe.bot(listOf("X","X","","O","O","","","","")));assertEquals(2,TicTacToe.bot(listOf("X","X","","","O","","","","")));assertNull(TicTacToe.bot(listOf("X","X","X","O","","O","","","")))}
}
