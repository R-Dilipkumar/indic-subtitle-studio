package io.github.rdilipkumar.subtitlestudio.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio
import java.util.Locale

/** "1:05.4" style time. */
fun formatTime(ms: Long, tenths: Boolean = true): String {
  val t = ms.coerceAtLeast(0)
  val m = t / 60_000
  val s = (t / 1000) % 60
  return if (tenths) String.format(Locale.ROOT, "%d:%02d.%d", m, s, (t % 1000) / 100)
  else String.format(Locale.ROOT, "%d:%02d", m, s)
}

fun formatBytes(b: Long): String = when {
  b >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1f GB", b / 1e9)
  b >= 1_000_000 -> String.format(Locale.ROOT, "%d MB", (b / 1e6).toInt())
  b >= 1_000 -> String.format(Locale.ROOT, "%d KB", (b / 1e3).toInt())
  else -> "$b B"
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, hint: String? = null) {
  Column(modifier.padding(top = 14.dp, bottom = 6.dp)) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Studio.text, fontWeight = FontWeight.SemiBold)
    if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Studio.muted)
  }
}

/** A pill that can be on or off. */
@Composable
fun Pill(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  accent: Color = Studio.accent,
) {
  val bg = if (selected) accent.copy(alpha = 0.18f) else Studio.surface2
  val border = if (selected) accent else Studio.outline
  val fg = when {
    !enabled -> Studio.muted.copy(alpha = 0.5f)
    selected -> accent
    else -> Studio.text
  }
  Row(
    modifier
      .clip(RoundedCornerShape(50))
      .background(bg)
      .border(1.dp, border, RoundedCornerShape(50))
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(6.dp))
    }
    Text(text, color = fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** A row of pills where exactly one is chosen. */
@Composable
fun <T> PillRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (o in options) Pill(label(o), o == selected, { onSelect(o) })
  }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, enabled: Boolean = true) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(enabled = enabled) { onChange(!checked) }
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, color = if (enabled) Studio.text else Studio.muted, style = MaterialTheme.typography.bodyLarge)
      if (subtitle != null) Text(subtitle, color = Studio.muted, style = MaterialTheme.typography.bodySmall)
    }
    Switch(
      checked = checked,
      onCheckedChange = onChange,
      enabled = enabled,
      colors = SwitchDefaults.colors(checkedTrackColor = Studio.accent, checkedThumbColor = Studio.onAccent),
    )
  }
}

@Composable
fun LabeledSlider(
  title: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onChange: (Float) -> Unit,
  valueText: String,
  onDone: () -> Unit = {},
) {
  Column(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(title, color = Studio.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
      Text(valueText, color = Studio.muted, style = MaterialTheme.typography.bodySmall)
    }
    Slider(
      value = value.coerceIn(range.start, range.endInclusive),
      onValueChange = onChange,
      onValueChangeFinished = onDone,
      valueRange = range,
      colors = SliderDefaults.colors(thumbColor = Studio.accent, activeTrackColor = Studio.accent),
    )
  }
}

@Composable
fun ColorSwatches(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    for (c in colors) {
      val isSel = c == selected
      Box(
        Modifier
          .size(30.dp)
          .clip(CircleShape)
          .background(Color(c))
          .border(if (isSel) 3.dp else 1.dp, if (isSel) Studio.accent else Studio.outline, CircleShape)
          .clickable { onPick(c) },
      )
    }
  }
}

/** A small coloured legend dot with a label. */
@Composable
fun LegendDot(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(4.dp))
    Text(label, color = Studio.muted, fontSize = 11.sp)
  }
}

@Composable
fun Badge(text: String, color: Color) {
  Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
    Text(text, color = color, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
  }
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp))
