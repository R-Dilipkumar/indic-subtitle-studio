package io.github.rdilipkumar.subtitlestudio.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import io.github.rdilipkumar.subtitlestudio.data.ModelPack
import io.github.rdilipkumar.subtitlestudio.data.PackState
import io.github.rdilipkumar.subtitlestudio.render.StylePresets
import io.github.rdilipkumar.subtitlestudio.ui.common.CaptionPreview
import io.github.rdilipkumar.subtitlestudio.ui.common.Pill
import io.github.rdilipkumar.subtitlestudio.ui.common.PillRow
import io.github.rdilipkumar.subtitlestudio.ui.common.SectionTitle
import io.github.rdilipkumar.subtitlestudio.ui.common.SwitchRow
import io.github.rdilipkumar.subtitlestudio.ui.common.formatBytes
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio

/** "What should we do with this video?" — the choices before the automatic run. */
@Composable
fun SetupScreen(
  projectId: String,
  onBack: () -> Unit,
  onStarted: () -> Unit,
  onSkip: () -> Unit,
  onOpenPacks: () -> Unit,
) {
  val vm: SetupViewModel = viewModel()
  LaunchedEffect(projectId) { vm.bind(projectId) }
  val meta by vm.meta.collectAsState()
  val frame by vm.frame.collectAsState()
  val c by vm.choices.collectAsState()
  val packs by vm.packStates.collectAsState()
  val message by vm.message.collectAsState()
  val speech = vm.speechPack()
  val speechState = packs[speech.id] ?: PackState.Missing
  val translatePacks = if (c.showTranslation) vm.translationPacks() else emptyList()
  val pickSubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) vm.importSubtitles(uri, onSkip)
  }

  Column(Modifier.fillMaxSize().background(Studio.bg).statusBarsPadding().navigationBarsPadding()) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
      Column(Modifier.weight(1f)) {
        Text("What should we do?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(meta?.name ?: "", color = Studio.muted, fontSize = 12.sp, maxLines = 1)
      }
    }
    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
    ) {
      // Live preview of the chosen look on a frame of the video.
      val (doc, cue) = vm.sampleDoc()
      Row(verticalAlignment = Alignment.Top) {
        CaptionPreview(
          frame = frame,
          videoW = meta?.width ?: 1080,
          videoH = meta?.height ?: 1920,
          doc = doc,
          cue = cue,
          timeMs = 700,
          modifier = Modifier.width(150.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text("Preview", color = Studio.muted, fontSize = 12.sp)
          Text("This is how captions will look. You can change every part later in the editor.", color = Studio.text, fontSize = 13.sp)
          Spacer(Modifier.height(10.dp))
          Text("Your video stays untouched. All edits happen on a copy, and export makes a new file.", color = Studio.muted, fontSize = 12.sp)
        }
      }

      SectionTitle("Spoken language", hint = "The language people speak in the video")
      PillRow(Lang.entries, c.language, { if (it == Lang.ENGLISH) "English" else "${it.label} · ${it.nativeLabel}" }, { l -> vm.change { it.copy(language = l) } })
      PackLine(speech, speechState, onDownload = { vm.download(speech) }, onOpenPacks = onOpenPacks)

      SectionTitle("Lines on screen", hint = "Show any mix. The first one is the biggest.")
      SwitchRow(
        title = if (c.language == Lang.ENGLISH) "Words" else "Original words (${c.language.nativeLabel})",
        checked = c.showNative,
        onChange = { v -> vm.change { it.copy(showNative = v) } },
      )
      if (c.language.isIndic) {
        SwitchRow(
          title = "English letters (phonetic)",
          subtitle = "So anyone can read along: " + io.github.rdilipkumar.subtitlestudio.core.phonetic.Romanizer.romanize(SetupViewModel.SAMPLES.getValue(c.language).first, c.phoneticMode),
          checked = c.showPhonetic,
          onChange = { v -> vm.change { it.copy(showPhonetic = v) } },
        )
        if (c.showPhonetic) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            Pill("Casual (chat style)", c.phoneticMode == PhoneticMode.CASUAL, { vm.change { it.copy(phoneticMode = PhoneticMode.CASUAL) } })
            Pill("Exact (with marks)", c.phoneticMode == PhoneticMode.EXACT, { vm.change { it.copy(phoneticMode = PhoneticMode.EXACT) } })
          }
        }
      }
      SwitchRow(
        title = "Translation",
        subtitle = "Meaning in another language, made offline",
        checked = c.showTranslation,
        onChange = { v -> vm.change { it.copy(showTranslation = v) } },
      )
      if (c.showTranslation) {
        val targets = Lang.entries.filter { it != c.language }
        PillRow(targets, c.translateTo ?: targets.first(), { "Into ${it.label}" }, { l -> vm.change { it.copy(translateTo = l) } })
        for (p in translatePacks) PackLine(p, packs[p.id] ?: PackState.Missing, onDownload = { vm.download(p) }, onOpenPacks = onOpenPacks)
      }

      SectionTitle("Caption size", hint = "Short captions suit Reels and Shorts; sentences suit films and talks")
      PillRow(CaptionLength.entries, c.length, { it.label }, { l -> vm.change { it.copy(length = l) } })

      SectionTitle("Look")
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (p in StylePresets.all) {
          val selected = p.id == c.presetId
          Column(
            Modifier
              .width(92.dp)
              .clip(RoundedCornerShape(14.dp))
              .border(2.dp, if (selected) Studio.accent else Studio.surface2, RoundedCornerShape(14.dp))
              .clickable { vm.change { it.copy(presetId = p.id) } }
              .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            val sample = vm.sampleDoc().let { (d, cu) -> d.copy(style = p.style, transform = p.transform.copy(y = 0.55f)) to cu }
            CaptionPreview(frame, meta?.width ?: 1080, meta?.height ?: 1920, sample.first, sample.second, 700, Modifier.fillMaxWidth())
            Text(p.label, fontSize = 12.sp, color = if (selected) Studio.accent else Studio.text, modifier = Modifier.padding(top = 4.dp))
          }
        }
      }
      Spacer(Modifier.height(20.dp))
    }

    // Actions
    Column(Modifier.fillMaxWidth().background(Studio.surface).padding(16.dp)) {
      val ready = speechState == PackState.Ready
      Button(
        onClick = { if (ready) vm.start(onStarted) else vm.download(speech) },
        enabled = speechState !is PackState.Downloading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Studio.accent, contentColor = Studio.onAccent),
      ) {
        Icon(if (ready) Icons.Rounded.AutoAwesome else Icons.Rounded.CloudDownload, null)
        Spacer(Modifier.width(8.dp))
        Text(
          when {
            ready -> "Make subtitles"
            speechState is PackState.Downloading -> "Downloading ${c.language.label} pack…"
            else -> "Get ${c.language.label} pack (${formatBytes(speech.totalBytes)}) to start"
          },
          fontWeight = FontWeight.SemiBold,
        )
      }
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.startBlank(onSkip) }, modifier = Modifier.weight(1f)) {
          Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Write my own")
        }
        OutlinedButton(onClick = { pickSubs.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
          Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Import .srt")
        }
      }
    }
  }

  message?.let {
    AlertDialog(
      onDismissRequest = vm::clearMessage,
      confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } },
      text = { Text(it) },
    )
  }
}

@Composable
private fun PackLine(pack: ModelPack, state: PackState, onDownload: () -> Unit, onOpenPacks: () -> Unit) {
  Column(
    Modifier
      .fillMaxWidth()
      .padding(top = 8.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(Studio.surface)
      .padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(pack.title, color = Studio.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("${pack.detail} · ${pack.quality} · ${formatBytes(pack.totalBytes)}", color = Studio.muted, fontSize = 12.sp)
      }
      when (state) {
        PackState.Ready -> Icon(Icons.Rounded.CheckCircle, "Ready", tint = Studio.green)
        is PackState.Downloading -> Text("${(state.fraction * 100).toInt()}%", color = Studio.accent)
        else -> TextButton(onClick = onDownload) { Text("Download") }
      }
    }
    if (state is PackState.Downloading) {
      Spacer(Modifier.height(8.dp))
      LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth(), color = Studio.accent)
    }
    if (state is PackState.Failed) {
      Text(state.message, color = Studio.danger, fontSize = 12.sp)
      TextButton(onClick = onOpenPacks) { Text("Other ways to get it") }
    }
  }
}
