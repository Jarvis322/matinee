package com.tcltv.sahne.data

import androidx.annotation.DrawableRes
import com.tcltv.sahne.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

data class CurrentWeather(val temperature: Int, val unit: TemperatureUnit, val condition: WeatherCondition, val isDay: Boolean)

enum class WeatherCondition(@DrawableRes val dayIcon: Int, @DrawableRes val nightIcon: Int) {
    CLEAR(R.drawable.ic_sun, R.drawable.ic_moon),
    CLOUDY(R.drawable.ic_cloud, R.drawable.ic_cloud),
    FOG(R.drawable.ic_mist, R.drawable.ic_mist),
    RAIN(R.drawable.ic_cloud_rain, R.drawable.ic_cloud_rain),
    SNOW(R.drawable.ic_snowflake, R.drawable.ic_snowflake),
    STORM(R.drawable.ic_cloud_storm, R.drawable.ic_cloud_storm);

    companion object {
        // WMO weather interpretation codes, as returned by Open-Meteo.
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0, 1 -> CLEAR
            2, 3 -> CLOUDY
            45, 48 -> FOG
            in 51..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            95, 96, 99 -> STORM
            else -> CLOUDY
        }
    }
}

class WeatherException(message: String, val retryable: Boolean) : IOException(message)

class WeatherClient {

    suspend fun current(location: WeatherLocation, unit: TemperatureUnit): CurrentWeather {
        val unitParam = if (unit == TemperatureUnit.FAHRENHEIT) "&temperature_unit=fahrenheit" else ""
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&timezone=auto%s",
            location.latitude, location.longitude, unitParam,
        )
        val json = getWithRetry(url)
        val current = json.optJSONObject("current") ?: throw WeatherException("Missing current block", retryable = false)
        // opt* plus NaN checks: has() is true for an explicit JSON null, and get* would then throw a RuntimeException.
        val temperature = current.optDouble("temperature_2m")
        val code = current.optDouble("weather_code")
        if (temperature.isNaN() || code.isNaN()) throw WeatherException("Incomplete current block", retryable = false)
        return CurrentWeather(
            temperature = temperature.roundToInt(),
            unit = unit,
            condition = WeatherCondition.fromWmoCode(code.toInt()),
            isDay = current.optInt("is_day", 1) == 1,
        )
    }

    suspend fun searchCities(query: String, language: String): List<WeatherLocation> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val json = getWithRetry("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=6&language=$language&format=json")
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val latitude = item.optDouble("latitude")
            val longitude = item.optDouble("longitude")
            if (latitude.isNaN() || longitude.isNaN()) return@mapNotNull null
            val region = listOf(item.optString("admin1"), item.optString("country")).filter { it.isNotBlank() }
            val display = if (region.isEmpty()) name else "$name, ${region.first()}"
            WeatherLocation(display, latitude, longitude)
        }
    }

    private suspend fun getWithRetry(url: String): JSONObject {
        var attempt = 0
        var backoffMillis = 2_000L
        while (true) {
            coroutineContext.ensureActive()
            try {
                return get(url)
            } catch (e: IOException) {
                val retryable = (e as? WeatherException)?.retryable ?: true
                if (!retryable || attempt >= MAX_RETRIES) throw e
                attempt++
                delay(backoffMillis)
                backoffMillis *= 2
            }
        }
    }

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            when {
                code in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    runCatching { JSONObject(body) }.getOrElse { throw WeatherException("Malformed response", retryable = false) }
                }
                code == 429 || code >= 500 -> throw WeatherException("HTTP $code", retryable = true)
                else -> throw WeatherException("HTTP $code", retryable = false)
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 8_000
        const val MAX_RETRIES = 2
    }
}
