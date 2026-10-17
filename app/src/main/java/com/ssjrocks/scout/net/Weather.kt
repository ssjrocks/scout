package com.ssjrocks.scout.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

@Serializable
data class Hour(val time: String, val temp: Double, val code: Int, val rain: Int, val isDay: Boolean)

@Serializable
data class Day(val date: String, val max: Double, val min: Double, val code: Int, val rain: Int, val uv: Double, val sunrise: String, val sunset: String)

@Serializable
data class Weather(
    val place: String,
    val temp: Double,
    val feels: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val wind: Double,
    val hours: List<Hour>,
    val days: List<Day>,
    val fetched: Long,
)

@Serializable
data class Place(val name: String, val area: String, val lat: Double, val lon: Double)

/** Open-Meteo: free, no key, works from the phone directly. */
object WeatherApi {

    suspend fun fetch(place: String, lat: Double, lon: Double, fahrenheit: Boolean): Weather {
        val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,relative_humidity_2m" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
            "&timezone=auto&forecast_days=7$unit"
        val j = Json.parseToJsonElement(Http.get(url)).jsonObject
        val cur = j["current"]!!.jsonObject
        val h = j["hourly"]!!.jsonObject
        val d = j["daily"]!!.jsonObject
        fun JsonObject.arr(k: String) = this[k]!!.jsonArray
        val nowIso = cur["time"]!!.jsonPrimitive.content
        val hTimes = h.arr("time").map { it.jsonPrimitive.content }
        val start = hTimes.indexOfFirst { it >= nowIso.take(13) }.coerceAtLeast(0)
        val hours = (start until minOf(start + 24, hTimes.size)).map { i ->
            Hour(
                hTimes[i], h.arr("temperature_2m")[i].jsonPrimitive.double, h.arr("weather_code")[i].jsonPrimitive.int,
                h.arr("precipitation_probability")[i].jsonPrimitive.intOrNull ?: 0, h.arr("is_day")[i].jsonPrimitive.int == 1,
            )
        }
        val days = d.arr("time").indices.map { i ->
            Day(
                d.arr("time")[i].jsonPrimitive.content, d.arr("temperature_2m_max")[i].jsonPrimitive.double,
                d.arr("temperature_2m_min")[i].jsonPrimitive.double, d.arr("weather_code")[i].jsonPrimitive.int,
                d.arr("precipitation_probability_max")[i].jsonPrimitive.intOrNull ?: 0,
                d.arr("uv_index_max")[i].jsonPrimitive.doubleOrNull ?: 0.0,
                d.arr("sunrise")[i].jsonPrimitive.content, d.arr("sunset")[i].jsonPrimitive.content,
            )
        }
        return Weather(
            place, cur["temperature_2m"]!!.jsonPrimitive.double, cur["apparent_temperature"]!!.jsonPrimitive.double,
            cur["weather_code"]!!.jsonPrimitive.int, cur["is_day"]!!.jsonPrimitive.int == 1,
            cur["relative_humidity_2m"]!!.jsonPrimitive.intOrNull ?: 0, cur["wind_speed_10m"]!!.jsonPrimitive.doubleOrNull ?: 0.0,
            hours, days, System.currentTimeMillis(),
        )
    }

    suspend fun search(name: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&name=" + URLEncoder.encode(name, "UTF-8")
        val j = Json.parseToJsonElement(Http.get(url)).jsonObject
        val results = j["results"]?.jsonArray ?: return emptyList()
        return results.map { r ->
            val o = r.jsonObject
            val area = listOfNotNull(o["admin1"]?.jsonPrimitive?.content, o["country"]?.jsonPrimitive?.content).joinToString(", ")
            Place(o["name"]!!.jsonPrimitive.content, area, o["latitude"]!!.jsonPrimitive.double, o["longitude"]!!.jsonPrimitive.double)
        }
    }

    /** WMO weather code -> (emoji, words). */
    fun describe(code: Int, isDay: Boolean = true): Pair<String, String> = when (code) {
        0 -> (if (isDay) "☀️" else "🌙") to "Clear"
        1 -> (if (isDay) "🌤️" else "🌙") to "Mostly clear"
        2 -> (if (isDay) "⛅" else "☁️") to "Partly cloudy"
        3 -> "☁️" to "Cloudy"
        45, 48 -> "🌫️" to "Fog"
        51, 53, 55 -> "🌦️" to "Drizzle"
        56, 57 -> "🌧️" to "Freezing drizzle"
        61 -> "🌦️" to "Light rain"
        63 -> "🌧️" to "Rain"
        65 -> "🌧️" to "Heavy rain"
        66, 67 -> "🌧️" to "Freezing rain"
        71, 73, 75, 77 -> "🌨️" to "Snow"
        80 -> "🌦️" to "Showers"
        81, 82 -> "🌧️" to "Heavy showers"
        85, 86 -> "🌨️" to "Snow showers"
        95 -> "⛈️" to "Thunderstorm"
        96, 99 -> "⛈️" to "Storm with hail"
        else -> "🌡️" to "—"
    }
}
