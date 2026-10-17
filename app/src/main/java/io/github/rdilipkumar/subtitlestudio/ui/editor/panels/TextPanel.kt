package io.github.rdilipkumar.subtitlestudio.ui.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rdilipkumar.subtitlestudio.core.model.Cue
import io.github.rdilipkumar.subtitlestudio.core.model.TranslationState
import io.github.rdilipkumar.subtitlestudio.core.model.Word
import io.github.rdilipkumar.subtitlestudio.ui.common.Badge
import io.github.rdilipkumar.subtitlestudio.ui.common.formatTime
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorUi
import io.github.rdilipkumar.subtitlestudio.ui.editor.EditorViewModel
import io.github.rdilipkumar.subtitlestudio.ui.theme.Studio

/** Fix the words, their English-letters spelling and the translation of one caption. */
@Composable
fun TextPanel(ui: EditorUi, vm: EditorViewModel, positionMs: Long, onSeek: (Long) -> Unit) {
  val cue = ui.selected
  if (cue == null) {
    Text(
      "Tap a caption on the video, the timeline or the list to edit it.",
      color = Studio.muted,
      modifier = Modifier.padding(16.dp),
    )
    return
  }
  val index = ui.doc.indexOf(cue.id)
  var wordToEdit by remember { mutableStateOf<Word?>(null) }

  Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = { ui.doc.cues.getOrNull(index - 1)?.let { vm.select(it.id); onSeek(it.startMs) } }, enabled = index > 0) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous")
      }
      Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Caption ${index + 1} of ${ui.doc.cues.size}", fontWeight = FontWeight.SemiBold)
        Text("${formatTime(cue.startMs)} – ${formatTime(cue.endMs)}", color = Studio.muted, fontSize = 12.sp)
      }
      IconButton(onClick = { ui.doc.cues.getOrNull(index + 1)?.let { vm.select(it.id); onSeek(it.startMs) } }, enabled = index < ui.doc.cues.lastIndex) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next")
      }
    }

    // The whole line, editable directly.
    var line by remember(cue.id) { mutableStateOf(cue.text) }
    LaunchedEffect(cue.text) {
      if (line.trim().split(Regex("\\s+")) != cue.text.split(' ')) line = cue.text
    }
    OutlinedTextField(
      value = line,
      onValueChange = {
        line = it
        if (it.isNotBlank()) vm.editCueText(cue.id, it)
      },
      label = { Text("Words") },
      modifier = Modifier.fillMaxWidth(),
    )

    // Word by word: tap to fix one word or its spelling. Tap the scissors to split there.
    Text("Tap a word to fix it or its English letters", color = Studio.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
      for ((i, w) in cue.words.withIndex()) {
        WordChip(w, active = positionMs in w.startMs until w.endMs) { wordToEdit = w }
        if (i < cue.words.lastIndex) {
          IconButton(onClick = { vm.splitAfter(cue.id, i) }) {
            Icon(Icons.Rounded.ContentCut, "Split here", tint = Studio.muted)
          }
        }
      }
    }

    // Translation.
    if (ui.doc.tracks.showTranslation || cue.translation.isNotBlank()) {
      var tr by remember(cue.id) { mutableStateOf(cue.translation) }
      LaunchedEffect(cue.translation) { if (tr != cue.translation && cue.translationState != TranslationState.USER) tr = cue.translation }
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Text("Translation (${vm.translationTarget().label})", color = Studio.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        when (cue.translationState) {
          TranslationState.AUTO -> Badge("Auto", Studio.markAi)
          TranslationState.USER -> Badge("Yours", Studio.markEdited)
          TranslationState.STALE -> Badge("Words changed — check", Studio.markStale)
          TranslationState.NONE -> Unit
        }
      }
      OutlinedTextField(tr, { tr = it; vm.editTranslation(cue.id, it) }, modifier = Modifier.fillMaxWidth())
      TextButton(onClick = { vm.translate(setOf(cue.id)) }) {
        Icon(Icons.Rounded.Translate, null)
        Spacer(Modifier.width(6.dp))
        Text("Translate this caption again")
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
      SmallAction("Join with previous", Icons.Rounded.CallMerge) { vm.mergePrev(cue.id) }
      SmallAction("Join with next", Icons.Rounded.CallMerge) { vm.mergeNext(cue.id) }
      SmallAction("Delete", Icons.Rounded.Delete) { vm.delete(cue.id) }
    }
  }

  wordToEdit?.let { w -> WordDialog(cue, w, ui.dictionary.containsKey(w.text), vm, onClose = { wordToEdit = null }) }
}

@Composable
private fun WordChip(w: Word, active: Boolean, onClick: () -> Unit) {
  val border = when {
    active -> Studio.accent
    w.confidence < Cue.LOW_CONFIDENCE -> Studio.markCheck
    w.edited || w.phoneticByUser -> Studio.markEdited
    else -> Studio.outline
  }
  Column(
    Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(Studio.surface2)
      .border(1.5.dp, border, RoundedCornerShape(10.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(w.text, color = Studio.text, fontSize = 15.sp)
    if (w.phonetic != w.text) Text(w.phonetic, color = if (w.phoneticByUser) Studio.markEdited else Studio.muted, fontSize = 12.sp)
  }
}

@Composable
private fun WordDialog(cue: Cue, w: Word, inDictionary: Boolean, vm: EditorViewModel, onClose: () -> Unit) {
  var text by remember { mutableStateOf(w.text) }
  var spelling by remember { mutableStateOf(w.phonetic) }
  var remember by remember { mutableStateOf(inDictionary) }
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text("Fix this word") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(text, { text = it }, label = { Text("Word") }, singleLine = true)
        OutlinedTextField(spelling, { spelling = it }, label = { Text("In English letters") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { remember = !remember }) {
          Checkbox(remember, { remember = it })
          Text("Always spell this word this way", fontSize = 14.sp)
        }
        if (w.confidence < Cue.LOW_CONFIDENCE) Text("The AI was not sure about this part. Listen and check.", color = Studio.markCheck, fontSize = 12.sp)
        Text("${formatTime(w.startMs)} – ${formatTime(w.endMs)}", color = Studio.muted, fontSize = 12.sp)
      }
    },
    confirmButton = {
      TextButton(onClick = {
        if (text.trim() != w.text) vm.editWord(cue.id, w.id, text)
        else if (spelling.trim() != w.phonetic || remember != inDictionary) vm.editWordPhonetic(cue.id, w.id, spelling, remember)
        onClose()
      }) { Text("Save") }
    },
    dismissButton = {
      TextButton(onClick = { vm.editWord(cue.id, w.id, ""); onClose() }) { Text("Delete word", color = Studio.danger) }
    },
  )
}
