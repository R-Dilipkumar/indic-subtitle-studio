package io.github.rdilipkumar.subtitlestudio.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
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
import io.github.rdilipkumar.subtitlestudio.core.model.SubtitleMode
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

/**
 * The two subtitle modes side by side: the sounds in English letters (no translation) or the
 * meaning. [mode] is null when the user has made their own mix of lines. The examples are
 * taken from the user's own captions when there are any.
 */
@Composable
fun SubtitleModeSwitch(
  mode: SubtitleMode?,
  onPick: (SubtitleMode) -> Unit,
  modifier: Modifier = Modifier,
  soundsExample: String = "vanakkam nanbargale",
  meaningExample: String = "Hello friends",
) {
  Column(modifier) {
    Text("Subtitle mode", color = Studio.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ChoiceCard(
        "How it sounds",
        mode == SubtitleMode.PHONETIC,
        { onPick(SubtitleMode.PHONETIC) },
        Modifier.weight(1f).fillMaxHeight(),
        description = "English letters, no translation",
        example = soundsExample,
      )
      ChoiceCard(
        "What it means",
        mode == SubtitleMode.TRANSLATION,
        { onPick(SubtitleMode.TRANSLATION) },
        Modifier.weight(1f).fillMaxHeight(),
        description = "Translated into another language",
        example = meaningExample,
      )
    }
    if (mode == null) {
      Text("Your own mix of lines (see Lines on screen).", color = Studio.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
  }
}

/**
 * A big tappable option: title, a line saying what it does, and an example drawn like a
 * subtitle so the choice is easy to picture.
 */
@Composable
fun ChoiceCard(
  title: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  description: String? = null,
  example: String? = null,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  extra: (@Composable () -> Unit)? = null,
) {
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier
      .clip(shape)
      .background(if (selected) Studio.accent.copy(alpha = 0.10f) else Studio.surface2)
      .border(if (selected) 2.dp else 1.dp, if (selected) Studio.accent else Studio.outline, shape)
      .clickable(onClick = onClick)
      .padding(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    if (leading != null) {
      leading()
      Spacer(Modifier.width(12.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(title, color = if (selected) Studio.accent else Studio.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
      if (description != null) {
        Text(description, color = Studio.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
      }
      if (example != null) CaptionChip(example, Modifier.padding(top = 8.dp))
      extra?.invoke()
    }
    Spacer(Modifier.width(8.dp))
    if (trailing != null) trailing() else RadioMark(selected)
  }
}

/** A round tick mark for the chosen option, an empty ring for the others. */
@Composable
fun RadioMark(selected: Boolean) {
  if (selected) {
    Icon(Icons.Rounded.CheckCircle, "Chosen", tint = Studio.accent, modifier = Modifier.size(22.dp))
  } else {
    Box(Modifier.padding(2.dp).size(18.dp).border(2.dp, Studio.outline, CircleShape))
  }
}

/** Example text drawn like a small subtitle: light text on a dark strip. */
@Composable
fun CaptionChip(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    color = Color.White,
    fontSize = 13.sp,
    fontWeight = FontWeight.SemiBold,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(Color.Black.copy(alpha = 0.55f))
      .padding(horizontal = 8.dp, vertical = 3.dp),
  )
}

/** A numbered step: "1  Spoken language", with a hint under it. */
@Composable
fun StepHeader(number: Int, title: String, hint: String? = null) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(Studio.accent), contentAlignment = Alignment.Center) {
      Text("$number", color = Studio.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.width(10.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = Studio.text, fontWeight = FontWeight.SemiBold)
  }
  if (hint != null) Text(hint, color = Studio.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 34.dp, bottom = 10.dp))
}

/** One step's panel: groups its options so steps don't run into each other. */
@Composable
fun StepCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(Studio.surface)
      .padding(14.dp),
    content = content,
  )
}

/** A small label inside a step ("Add more lines"), smaller than the step title. */
@Composable
fun SubHeading(text: String, modifier: Modifier = Modifier) {
  Text(text, color = Studio.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 14.dp, bottom = 6.dp))
}

/** Like [PillRow], but wraps onto more lines so every option stays in sight. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> PillFlow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
  FlowRow(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
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
