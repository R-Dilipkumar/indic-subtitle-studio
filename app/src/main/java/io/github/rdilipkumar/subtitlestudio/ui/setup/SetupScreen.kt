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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rdilipkumar.subtitlestudio.core.model.Lang
import io.github.rdilipkumar.subtitlestudio.core.model.PhoneticMode
import io.github.rdilipkumar.subtitlestudio.core.model.TrackKind
import io.github.rdilipkumar.subtitlestudio.core.timing.CaptionLength
import io.github.rdilipkumar.subtitlestudio.data.ModelCatalog
import io.github.rdilipkumar.subtitlestudio.data.ModelPack
import io.github.rdilipkumar.subtitlestudio.data.PackState
import io.github.rdilipkumar.subtitlestudio.render.StylePresets
import io.github.rdilipkumar.subtitlestudio.ui.common.Badge
import io.github.rdilipkumar.subtitlestudio.ui.common.CaptionPreview
import io.github.rdilipkumar.subtitlestudio.ui.common.ChoiceCard
import io.github.rdilipkumar.subtitlestudio.ui.common.PillFlow
import io.github.rdilipkumar.subtitlestudio.ui.common.PlayingCaptionPreview
import io.github.rdilipkumar.subtitlestudio.ui.common.StepCard
import io.github.rdilipkumar.subtitlestudio.ui.common.StepHeader
import io.github.rdilipkumar.subtitlestudio.ui.common.SubHeading
import io.github.rdilipkumar.subtitlestudio.ui.common.SwitchRow
import io.github.rdilipkumar.subtitlestudio.ui.common.formatBytes
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio

/**
 * "What should we do with this video?" — the choices before the automatic run, in four steps.
 * A live preview stays pinned on top and plays the sample captions, so every choice shows.
 */
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
  val pickSubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) vm.importSubtitles(uri, onSkip)
  }
  var pickingLanguage by remember { mutableStateOf(false) }
  var wholeFrame by remember { mutableStateOf(false) }
  val videoW = meta?.width ?: 1080
  val videoH = meta?.height ?: 1920

  Column(Modifier.fillMaxSize().background(Studio.bg).statusBarsPadding().navigationBarsPadding()) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
      Column(Modifier.weight(1f)) {
        Text("What should we do?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(meta?.name ?: "", color = Studio.muted, fontSize = 12.sp, maxLines = 1)
      }
    }

    // Live preview, pinned so every change shows while scrolling through the options.
    val (doc, loopMs) = remember(c) { vm.sample() }
    Box(
      Modifier
        .padding(horizontal = 16.dp)
        .fillMaxWidth()
        .height(if (wholeFrame) 300.dp else 170.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable { wholeFrame = !wholeFrame },
    ) {
      if (wholeFrame) {
        PlayingCaptionPreview(frame, videoW, videoH, doc, loopMs, Modifier.align(Alignment.Center))
      } else {
        PlayingCaptionPreview(frame, videoW, videoH, doc, loopMs, Modifier.fillMaxSize(), closeUp = true)
      }
      PreviewTag("Live preview", Modifier.align(Alignment.TopStart).padding(8.dp))
      Row(
        Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp)
          .clip(RoundedCornerShape(50))
          .background(Color.Black.copy(alpha = 0.6f))
          .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(if (wholeFrame) Icons.Rounded.ZoomIn else Icons.Rounded.Fullscreen, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (wholeFrame) "Close up" else "Whole frame", color = Color.White, fontSize = 11.sp)
      }
    }

    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
    ) {
      Spacer(Modifier.height(12.dp))

      // 1. Spoken language
      StepCard {
        StepHeader(1, "Spoken language", "The language people speak in the video")
        LanguageCard(c.language, speech, speechState, onClick = { pickingLanguage = true })
        if (speechState is PackState.Downloading) {
          LinearProgressIndicator(progress = { speechState.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Studio.accent)
        }
        if (speechState is PackState.Failed) {
          Text(speechState.message, color = Studio.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
          TextButton(onClick = onOpenPacks) { Text("Other ways to get it") }
        }
        speech.licenseNote?.let { Text(it, color = Studio.markCheck, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        if (c.language == Lang.TAMIL) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Lots of English words in the video? Tanglish handles them better.", color = Studio.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.change { it.copy(language = Lang.TANGLISH) } }) { Text("Use Tanglish") }
          }
        }
      }
      Spacer(Modifier.height(12.dp))

      // 2. What the subtitles show
      StepCard {
        StepHeader(2, "What the subtitles show", "Pick the main line. You can add more lines under it.")
        LinesStep(c, vm)
        if (c.isShown(TrackKind.TRANSLATION)) {
          SubHeading("Translate into")
          val targets = Lang.entries.filter { it != c.language && it.isTranslationTarget }
          PillFlow(targets, c.translateTo ?: targets.first(), { LanguageInfo.name(it) }, { l -> vm.change { it.copy(translateTo = l) } })
          val translatePacks = vm.translationPacks()
          if (translatePacks.size > 1) {
            Text(
              "Between two Indian languages the app goes through English, so it needs both packs.",
              color = Studio.muted,
              fontSize = 12.sp,
              modifier = Modifier.padding(top = 8.dp),
            )
          }
          for (p in translatePacks) PackLine(p, packs[p.id] ?: PackState.Missing, onDownload = { vm.download(p) }, onOpenPacks = onOpenPacks)
        }
      }
      Spacer(Modifier.height(12.dp))

      // 3. Caption size
      StepCard {
        StepHeader(3, "Caption size", "How many words are on screen at once")
        val examples = remember(c) { CaptionLength.entries.associateWith { SetupSamples.firstCaptionText(c, it) } }
        for (row in CaptionLength.entries.chunked(2)) {
          Row(Modifier.height(IntrinsicSize.Min).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (l in row) {
              ChoiceCard(
                l.label,
                c.length == l,
                { vm.change { it.copy(length = l) } },
                Modifier.weight(1f).fillMaxHeight(),
                description = lengthHint(l),
                example = examples[l],
              )
            }
          }
        }
      }
      Spacer(Modifier.height(12.dp))

      // 4. Look
      StepCard {
        StepHeader(4, "Look", "Font, colours and motion. Watch the preview at the top.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          for (p in StylePresets.all) {
            val selected = p.id == c.presetId
            val (sample, _) = remember(c, p.id) { vm.sample(presetId = p.id) }
            val cue = sample.cues.firstOrNull()
            Column(
              Modifier
                .width(136.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, if (selected) Studio.accent else Studio.surface2, RoundedCornerShape(14.dp))
                .clickable { vm.change { it.copy(presetId = p.id) } }
                .padding(4.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              // A still moment, a little after the second word starts, so the word highlight shows.
              val t = cue?.words?.getOrNull(1)?.startMs?.plus(150) ?: 700L
              CaptionPreview(frame, videoW, videoH, sample, cue, t, Modifier.fillMaxWidth().height(84.dp), closeUp = true)
              Text(p.label, fontSize = 12.sp, color = if (selected) Studio.accent else Studio.text, modifier = Modifier.padding(top = 4.dp))
            }
          }
        }
      }

      Text(
        "Your video stays untouched. All edits happen on a copy, and export makes a new file. You can change every choice later in the editor.",
        color = Studio.muted,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 16.dp),
      )
    }

    // Actions
    Column(Modifier.fillMaxWidth().background(Studio.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
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

  if (pickingLanguage) {
    LanguageSheet(
      selected = c.language,
      packs = packs,
      onPick = { l ->
        vm.change { it.copy(language = l) }
        pickingLanguage = false
      },
      onClose = { pickingLanguage = false },
    )
  }

  message?.let {
    AlertDialog(
      onDismissRequest = vm::clearMessage,
      confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } },
      text = { Text(it) },
    )
  }
}

/** Step 2: the main line as big cards, then switches for the extra lines and their options. */
@Composable
private fun LinesStep(c: SetupChoices, vm: SetupViewModel) {
  val lang = c.language
  val sentence = SetupSamples.SENTENCES.getValue(lang).first
  val examples = mapOf(
    TrackKind.NATIVE to sentence,
    TrackKind.PHONETIC to SetupSamples.phonetic(lang, sentence, c.phoneticMode),
    TrackKind.TRANSLATION to SetupSamples.translation(lang, c.translateTo),
  )
  val kinds = if (lang.isIndic) listOf(TrackKind.PHONETIC, TrackKind.TRANSLATION, TrackKind.NATIVE) else listOf(TrackKind.NATIVE, TrackKind.TRANSLATION)

  for (k in kinds) {
    ChoiceCard(
      mainTitle(k, lang),
      c.main == k,
      { vm.setMain(k) },
      Modifier.fillMaxWidth().padding(bottom = 8.dp),
      description = mainDescription(k, lang),
      example = examples[k],
    )
  }

  SubHeading("Add more lines under it")
  for (k in kinds.filter { it != c.main }) {
    SwitchRow(
      title = extraTitle(k, lang),
      subtitle = examples[k],
      checked = c.isShown(k),
      onChange = { v -> vm.change { it.withLine(k, v) } },
    )
  }

  if (c.isShown(TrackKind.PHONETIC)) {
    SubHeading("How to spell the English letters")
    val words = SetupSamples.words(lang)
    val word = words.drop(1).firstOrNull { w -> w.any { it.code >= 0x0900 } } ?: words.first()
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ChoiceCard(
        "Casual",
        c.phoneticMode == PhoneticMode.CASUAL,
        { vm.change { it.copy(phoneticMode = PhoneticMode.CASUAL) } },
        Modifier.weight(1f).fillMaxHeight(),
        description = "Like chat messages. Easy for everyone.",
        example = SetupSamples.phonetic(lang, word, PhoneticMode.CASUAL),
      )
      ChoiceCard(
        "Exact",
        c.phoneticMode == PhoneticMode.EXACT,
        { vm.change { it.copy(phoneticMode = PhoneticMode.EXACT) } },
        Modifier.weight(1f).fillMaxHeight(),
        description = "With accent marks, for exact sounds.",
        example = SetupSamples.phonetic(lang, word, PhoneticMode.EXACT),
      )
    }
  }
}

private fun mainTitle(k: TrackKind, lang: Lang): String = when (k) {
  TrackKind.PHONETIC -> "How it sounds"
  TrackKind.TRANSLATION -> "What it means"
  TrackKind.NATIVE -> when (lang) {
    Lang.ENGLISH -> "The words"
    Lang.TANGLISH -> "Tamil script"
    else -> "Original script"
  }
}

private fun mainDescription(k: TrackKind, lang: Lang): String = when (k) {
  TrackKind.PHONETIC -> "The words in English letters, so anyone can read along. Not a translation."
  TrackKind.TRANSLATION -> "Translated into another language, on the phone."
  TrackKind.NATIVE -> when (lang) {
    Lang.ENGLISH -> "Exactly what is said, in English."
    Lang.TANGLISH -> "Tamil words in Tamil letters, English words in English."
    else -> "The exact words in ${lang.nativeLabel} letters."
  }
}

private fun extraTitle(k: TrackKind, lang: Lang): String = when (k) {
  TrackKind.PHONETIC -> "English letters (how it sounds)"
  TrackKind.TRANSLATION -> "Translation (what it means)"
  TrackKind.NATIVE -> when (lang) {
    Lang.ENGLISH -> "The English words"
    Lang.TANGLISH -> "Tamil script"
    else -> "Original script (${lang.nativeLabel})"
  }
}

private fun lengthHint(l: CaptionLength): String = when (l) {
  CaptionLength.ONE_WORD -> "Fast and punchy"
  CaptionLength.PUNCHY -> "Best for Reels and Shorts"
  CaptionLength.LINE -> "Good for vlogs and YouTube"
  CaptionLength.SENTENCE -> "For films, talks and interviews"
}

@Composable
private fun PreviewTag(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    color = Color.White,
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    modifier = modifier
      .clip(RoundedCornerShape(50))
      .background(Color.Black.copy(alpha = 0.6f))
      .padding(horizontal = 8.dp, vertical = 3.dp),
  )
}

/** The letter "a" of the language's script in a circle, so languages are told apart at a glance. */
@Composable
private fun LangBadge(lang: Lang, selected: Boolean) {
  val glyph = LanguageInfo.glyph(lang)
  Box(
    Modifier
      .size(44.dp)
      .clip(CircleShape)
      .background(if (selected) Studio.accent.copy(alpha = 0.18f) else Studio.surface3),
    contentAlignment = Alignment.Center,
  ) {
    Text(glyph, color = if (selected) Studio.accent else Studio.text, fontSize = if (glyph.length > 1) 15.sp else 20.sp, fontWeight = FontWeight.Bold)
  }
}

/** How good the speech pack is, whether it is on the phone, and any licence limit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackBadges(pack: ModelPack, state: PackState) {
  FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (pack.quality == "Good") Badge("Good accuracy", Studio.green) else Badge("Basic accuracy", Studio.markCheck)
    when (state) {
      PackState.Ready -> Badge("Ready", Studio.green)
      is PackState.Downloading -> Badge("Downloading ${(state.fraction * 100).toInt()}%", Studio.accent)
      is PackState.Failed -> Badge("Download failed", Studio.danger)
      PackState.Missing -> Badge("Download ${formatBytes(pack.totalBytes)}", Studio.muted)
    }
    if (pack.licenseNote != null) Badge("Personal use only", Studio.markCheck)
  }
}

/** The chosen language. Tapping it opens the full list. */
@Composable
private fun LanguageCard(lang: Lang, pack: ModelPack, state: PackState, onClick: () -> Unit) {
  val shape = RoundedCornerShape(14.dp)
  Row(
    Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(Studio.surface2)
      .border(1.dp, Studio.outline, shape)
      .clickable(onClick = onClick)
      .padding(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    LangBadge(lang, selected = true)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(LanguageInfo.name(lang), color = Studio.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
      Text(LanguageInfo.description(lang), color = Studio.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
      PackBadges(pack, state)
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(Icons.Rounded.UnfoldMore, null, tint = Studio.accent)
      Text("Change", color = Studio.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
  }
}

/** Every language, grouped, each with what it is for and its pack status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(selected: Lang, packs: Map<String, PackState>, onPick: (Lang) -> Unit, onClose: () -> Unit) {
  ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Studio.surface) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
      Text("What language do they speak?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
      Text(
        "Pick the one you hear most. Each language has its own speech pack, downloaded once.",
        color = Studio.muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 4.dp),
      )
      for (g in LanguageInfo.Group.entries) {
        SubHeading(g.title)
        for (l in LanguageInfo.order.filter { LanguageInfo.group(it) == g }) {
          val pack = ModelCatalog.speech.getValue(l)
          ChoiceCard(
            LanguageInfo.name(l),
            l == selected,
            { onPick(l) },
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            description = LanguageInfo.description(l),
            leading = { LangBadge(l, l == selected) },
            extra = { PackBadges(pack, packs[pack.id] ?: PackState.Missing) },
          )
        }
      }
    }
  }
}

@Composable
private fun PackLine(pack: ModelPack, state: PackState, onDownload: () -> Unit, onOpenPacks: () -> Unit) {
  Column(
    Modifier
      .fillMaxWidth()
      .padding(top = 8.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(Studio.surface2)
      .padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(pack.title, color = Studio.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("${pack.detail} · ${formatBytes(pack.totalBytes)}", color = Studio.muted, fontSize = 12.sp)
      }
      when (state) {
        PackState.Ready -> Badge("Ready", Studio.green)
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
    if (state != PackState.Ready) {
      Text("Without it, the captions are made without a translation.", color = Studio.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
  }
}
