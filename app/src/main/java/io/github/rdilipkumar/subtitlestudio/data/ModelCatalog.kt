package io.github.rdilipkumar.subtitlestudio.data

import io.github.rdilipkumar.subtitlestudio.core.model.Lang

enum class PackKind { SPEECH, TRANSLATE }

data class PackFile(val name: String, val url: String, val bytes: Long)

/**
 * A language pack: model files downloaded once, then used fully offline.
 *
 * Speech packs are Whisper models fine-tuned per language by IIT Madras (vasista22) and
 * others, converted for sherpa-onnx. Translation packs are AI4Bharat's IndicTrans2 (distilled),
 * converted to ONNX. Sizes are exact so a broken download is caught.
 */
data class ModelPack(
  val id: String,
  val kind: PackKind,
  val title: String,
  val detail: String,
  /** "Good" or "Basic", shown so users know what to expect. */
  val quality: String,
  val languages: List<Lang>,
  val files: List<PackFile>,
  /** For speech packs: the language code to give Whisper. */
  val whisperLanguage: String = "",
  val encoderFile: String = "",
  val decoderFile: String = "",
  val tokensFile: String = "",
) {
  val totalBytes: Long get() = files.sumOf { it.bytes }
}

object ModelCatalog {

  private const val INDIC_ASR = "https://huggingface.co/ippocode/indic-asr-onnx/resolve/main/models"
  private const val WHISPER_EN = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base.en/resolve/main"
  private const val IT2_IN_EN = "https://huggingface.co/hari31416/indictrans2-indic-en-dist-200M-ONNX-int8/resolve/main"
  private const val IT2_EN_IN = "https://huggingface.co/hari31416/indictrans2-en-indic-dist-200M-ONNX-int8/resolve/main"

  private fun indicWhisper(
    id: String,
    folder: String,
    lang: Lang,
    small: Boolean,
    by: String,
  ): ModelPack {
    val enc = if (small) 112_413_411L else 12_926_471L
    val dec = if (small) 262_200_764L else 89_846_458L
    return ModelPack(
      id = id,
      kind = PackKind.SPEECH,
      title = "${lang.label} speech",
      detail = (if (small) "Whisper small" else "Whisper tiny") + ", tuned for ${lang.label} by $by",
      quality = if (small) "Good" else "Basic",
      languages = listOf(lang),
      files = listOf(
        PackFile("encoder.int8.onnx", "$INDIC_ASR/$folder/encoder.int8.onnx", enc),
        PackFile("decoder.int8.onnx", "$INDIC_ASR/$folder/decoder.int8.onnx", dec),
        PackFile("tokens.txt", "$INDIC_ASR/$folder/tokens.txt", 816_730L),
      ),
      whisperLanguage = lang.code,
      encoderFile = "encoder.int8.onnx",
      decoderFile = "decoder.int8.onnx",
      tokensFile = "tokens.txt",
    )
  }

  val speech: Map<Lang, ModelPack> = mapOf(
    Lang.TAMIL to indicWhisper("speech-ta", "whisper-small-ta", Lang.TAMIL, small = true, by = "IIT Madras"),
    Lang.MALAYALAM to indicWhisper("speech-ml", "whisper-small-ml", Lang.MALAYALAM, small = true, by = "Kavya Manohar"),
    Lang.TELUGU to indicWhisper("speech-te", "whisper-tiny-te", Lang.TELUGU, small = false, by = "IIT Madras"),
    Lang.KANNADA to indicWhisper("speech-kn", "whisper-tiny-kn", Lang.KANNADA, small = false, by = "IIT Madras"),
    Lang.HINDI to indicWhisper("speech-hi", "whisper-small-hi", Lang.HINDI, small = true, by = "IIT Madras"),
    Lang.ENGLISH to ModelPack(
      id = "speech-en",
      kind = PackKind.SPEECH,
      title = "English speech",
      detail = "Whisper base (English)",
      quality = "Good",
      languages = listOf(Lang.ENGLISH),
      files = listOf(
        PackFile("base.en-encoder.int8.onnx", "$WHISPER_EN/base.en-encoder.int8.onnx", 29_120_534L),
        PackFile("base.en-decoder.int8.onnx", "$WHISPER_EN/base.en-decoder.int8.onnx", 130_669_978L),
        PackFile("base.en-tokens.txt", "$WHISPER_EN/base.en-tokens.txt", 835_554L),
      ),
      whisperLanguage = "en",
      encoderFile = "base.en-encoder.int8.onnx",
      decoderFile = "base.en-decoder.int8.onnx",
      tokensFile = "base.en-tokens.txt",
    ),
  )

  private val translateLangs = listOf(Lang.TAMIL, Lang.TELUGU, Lang.KANNADA, Lang.MALAYALAM, Lang.HINDI)

  /** Indian languages to English. */
  val toEnglish = ModelPack(
    id = "translate-in-en",
    kind = PackKind.TRANSLATE,
    title = "Translate to English",
    detail = "IndicTrans2 by AI4Bharat: Tamil, Telugu, Kannada, Malayalam, Hindi → English",
    quality = "Good",
    languages = translateLangs,
    files = listOf(
      PackFile("encoder_model.onnx", "$IT2_IN_EN/encoder_model.onnx", 831_436L),
      PackFile("encoder_model.onnx.data", "$IT2_IN_EN/encoder_model.onnx.data", 120_068_096L),
      PackFile("decoder_model.onnx", "$IT2_IN_EN/decoder_model.onnx", 1_585_382L),
      PackFile("decoder_with_past_model.onnx", "$IT2_IN_EN/decoder_with_past_model.onnx", 1_398_395L),
      PackFile("decoder_shared.onnx.data", "$IT2_IN_EN/decoder_shared.onnx.data", 110_052_040L),
      PackFile("model.SRC", "$IT2_IN_EN/model.SRC", 3_256_903L),
      PackFile("model.TGT", "$IT2_IN_EN/model.TGT", 759_425L),
      PackFile("dict.SRC.json", "$IT2_IN_EN/dict.SRC.json", 3_391_208L),
      PackFile("dict.TGT.json", "$IT2_IN_EN/dict.TGT.json", 644_755L),
    ),
  )

  /** English to Indian languages. */
  val fromEnglish = ModelPack(
    id = "translate-en-in",
    kind = PackKind.TRANSLATE,
    title = "Translate from English",
    detail = "IndicTrans2 by AI4Bharat: English → Tamil, Telugu, Kannada, Malayalam, Hindi",
    quality = "Good",
    languages = translateLangs,
    files = listOf(
      PackFile("encoder_model.onnx", "$IT2_EN_IN/encoder_model.onnx", 831_399L),
      PackFile("encoder_model.onnx.data", "$IT2_EN_IN/encoder_model.onnx.data", 73_791_488L),
      PackFile("decoder_model.onnx", "$IT2_EN_IN/decoder_model.onnx", 2_037_502L),
      PackFile("decoder_with_past_model.onnx", "$IT2_EN_IN/decoder_with_past_model.onnx", 1_850_479L),
      PackFile("decoder_shared.onnx.data", "$IT2_EN_IN/decoder_shared.onnx.data", 203_048_944L),
      PackFile("model.SRC", "$IT2_EN_IN/model.SRC", 759_425L),
      PackFile("model.TGT", "$IT2_EN_IN/model.TGT", 3_256_903L),
      PackFile("dict.SRC.json", "$IT2_EN_IN/dict.SRC.json", 645_314L),
      PackFile("dict.TGT.json", "$IT2_EN_IN/dict.TGT.json", 3_390_440L),
    ),
  )

  val all: List<ModelPack> = speech.values.toList() + listOf(toEnglish, fromEnglish)

  fun byId(id: String): ModelPack? = all.firstOrNull { it.id == id }

  /**
   * Packs needed to translate [from] into [to], in order. Between two Indian languages the
   * text goes through English (both packs).
   */
  fun translationPath(from: Lang, to: Lang): List<ModelPack> = when {
    from == to -> emptyList()
    to == Lang.ENGLISH -> listOf(toEnglish)
    from == Lang.ENGLISH -> listOf(fromEnglish)
    else -> listOf(toEnglish, fromEnglish)
  }
}
