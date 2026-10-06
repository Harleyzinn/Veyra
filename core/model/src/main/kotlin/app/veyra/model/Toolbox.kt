package app.veyra.model
import java.security.SecureRandom
import kotlin.math.*

object Toolbox {
    fun password(length:Int,symbols:Boolean):String {
        require(length in 8..128)
        val alphabet="abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"+if(symbols) "!@#$%&*+-_?" else ""
        val random=SecureRandom();return (1..length).map{alphabet[random.nextInt(alphabet.length)]}.joinToString("")
    }
    fun compound(principal:Double,monthly:Double,rate:Double,months:Int):Double {
        require(principal>=0 && monthly>=0 && months in 0..1200 && rate>=0)
        var amount=principal;repeat(months){amount=amount*(1+rate/100)+monthly};require(amount.isFinite());return amount
    }
    fun calculate(expression:String):Double {
        require(expression.length<=500)
        class Parser {
            var position=0
            fun skip(){while(position<expression.length && expression[position].isWhitespace()) position++}
            fun take(c:Char):Boolean {skip();return if(position<expression.length && expression[position]==c){position++;true}else false}
            fun sum():Double {var value=product();while(true){value=when{take('+')->value+product();take('-')->value-product();else->return value}}}
            fun product():Double {var value=power();while(true){value=when{take('*')->value*power();take('/')->value/power();else->return value}}}
            fun power():Double {if(take('+'))return power();if(take('-'))return -power();val value=atom();return if(take('^')) value.pow(power()) else value}
            fun atom():Double {
                skip();if(take('+')) return atom();if(take('-'))return -atom()
                if(take('(')){val value=sum();require(take(')')){"Feche os parênteses"};return value}
                val start=position
                while(position<expression.length && expression[position].isLetter())position++
                if(position>start) {
                    val name=expression.substring(start,position).lowercase()
                    if(name=="pi")return PI;if(name=="e")return E
                    require(take('('));val x=sum();require(take(')'))
                    return when(name){"sin"->sin(x);"cos"->cos(x);"tan"->tan(x);"sqrt"->sqrt(x);"log"->log10(x);"ln"->ln(x);"abs"->abs(x);else->error("Função desconhecida")}
                }
                while(position<expression.length && (expression[position].isDigit() || expression[position] in ".,"))position++
                require(position>start){"Expressão inválida"}
                var value=expression.substring(start,position).replace(',','.').toDouble()
                if(take('%'))value/=100;return value
            }
        }
        val parser=Parser();val value=parser.sum();parser.skip();require(parser.position==expression.length && value.isFinite()){"Resultado inválido"};return value
    }
    val units=mapOf("m" to 1.0,"km" to 1000.0,"cm" to .01,"mm" to .001,"kg" to 1.0,"g" to .001,"lb" to .45359237,"L" to 1.0,"mL" to .001,"h" to 3600.0,"min" to 60.0,"s" to 1.0,"GB" to 1e9,"MB" to 1e6,"KB" to 1e3)
    fun convert(value:Double,from:String,to:String):Double {
        val families=listOf(setOf("m","km","cm","mm"),setOf("kg","g","lb"),setOf("L","mL"),setOf("h","min","s"),setOf("GB","MB","KB"),setOf("°C","°F","K"))
        require(families.any{from in it && to in it}){"Escolha unidades da mesma grandeza"}
        if(from in setOf("°C","°F","K")){val c=when(from){"°F"->(value-32)*5/9;"K"->value-273.15;else->value};require(c>=-273.15);return when(to){"°F"->c*9/5+32;"K"->c+273.15;else->c}}
        return value*units.getValue(from)/units.getValue(to)
    }
}
