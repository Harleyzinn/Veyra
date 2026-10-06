package app.veyra.feature.weather
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder
import app.veyra.model.Item

object Weather {
    private fun json(url:String):JSONObject {
        val connection=URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout=12000;connection.readTimeout=15000
        return try {
            require(connection.responseCode in 200..299){"Serviço de clima indisponível (${connection.responseCode})"}
            JSONObject(connection.inputStream.bufferedReader().use{it.readText()})
        } finally {connection.disconnect()}
    }
    fun search(query:String):List<Item> {
        require(query.trim().length>=2){"Digite pelo menos 2 letras"}
        val results=json("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query,"UTF-8")}&count=8&language=pt&format=json").optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { n -> results.getJSONObject(n).let { city ->
            Item(id="city:${city.getLong("id")}",type="city",title="${city.getString("name")}, ${city.optString("admin1")} • ${city.optString("country")}".take(200),
                fields=mapOf("latitude" to city.getDouble("latitude").toString(),"longitude" to city.getDouble("longitude").toString()))
        } }
    }
    fun forecast(city:Item):Item {
        val lat=city.value("latitude").toDouble();val lon=city.value("longitude").toDouble()
        require(lat in -90.0..90.0 && lon in -180.0..180.0)
        val data=json("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&hourly=temperature_2m,precipitation_probability&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max&timezone=auto&forecast_days=7")
        return city.copy(fields=city.fields+mapOf("cache" to data.toString(),"updated" to System.currentTimeMillis().toString()))
    }
    fun condition(code:Int)=when(code) {0->"Céu limpo";1,2->"Parcialmente nublado";3->"Nublado";45,48->"Névoa";in 51..67->"Chuva leve";in 71..77->"Neve";in 80..82->"Pancadas de chuva";in 95..99->"Tempestade";else->"Condição variável"}
}
