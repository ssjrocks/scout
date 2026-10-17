package com.ssjrocks.scout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssjrocks.scout.data.Settings
import com.ssjrocks.scout.net.Weather
import com.ssjrocks.scout.net.WeatherApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeatherSheet(w: Weather, s: Settings, onDismiss: () -> Unit, onChangePlace: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val (emoji, words) = WeatherApi.describe(w.code, w.isDay)
    val unit = if (s.fahrenheit) "°F" else "°C"
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.place, style = MaterialTheme.typography.titleMedium)
                    Text(words, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onChangePlace) { Text("Change") }
            }
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 56.sp)
                Spacer(Modifier.width(16.dp))
                Text("${w.temp.roundToInt()}$unit", fontSize = 56.sp, fontWeight = FontWeight.Normal)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("Feels like ${w.feels.roundToInt()}°", style = MaterialTheme.typography.bodyMedium)
                    Text("Humidity ${w.humidity}%", style = MaterialTheme.typography.bodyMedium)
                    Text("Wind ${w.wind.roundToInt()} km/h", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Next 24 hours", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(w.hours) { h ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val hour = runCatching { LocalDateTime.parse(h.time).hour }.getOrDefault(0)
                            Text(if (hour == 0) "12am" else if (hour < 12) "${hour}am" else if (hour == 12) "12pm" else "${hour - 12}pm", style = MaterialTheme.typography.labelMedium)
                            Text(WeatherApi.describe(h.code, h.isDay).first, fontSize = 22.sp)
                            Text("${h.temp.roundToInt()}°", style = MaterialTheme.typography.titleSmall)
                            Text(if (h.rain > 0) "💧${h.rain}%" else " ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Text("7 days", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp))
            w.days.forEachIndexed { i, d ->
                val day = runCatching { LocalDate.parse(d.date) }.getOrNull()
                val name = when (i) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> day?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: d.date
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(if (d.rain > 0) "💧${d.rain}%" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(56.dp))
                    Text(WeatherApi.describe(d.code).first, fontSize = 20.sp, modifier = Modifier.width(36.dp))
                    Text("${d.max.roundToInt()}°", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(40.dp))
                    Text("${d.min.roundToInt()}°", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                }
                if (i == 0) {
                    Text(
                        "UV ${d.uv.roundToInt()} · sunrise ${d.sunrise.takeLast(5)} · sunset ${d.sunset.takeLast(5)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, bottom = 6.dp),
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Weather from Open-Meteo · updated ${shortAge(w.fetched)} ago", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
