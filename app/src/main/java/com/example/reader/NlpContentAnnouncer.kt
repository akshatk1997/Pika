package com.example.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.model.ReaderArticle
import java.util.Locale

data class NarrationSegment(
  val index: Int,
  val paragraphIndex: Int,
  val displayText: String,
  val spokenText: String,
  val isHeading: Boolean = false
)

data class NarratorState(
  val isActive: Boolean = false,
  val isSpeaking: Boolean = false,
  val isPaused: Boolean = false,
  val currentIndex: Int = 0,
  val totalSegments: Int = 0,
  val speechRate: Float = 1.0f,
  val statusNote: String? = null
)

/**
 * Pure on-device NLP Content Announcer & Reader Narration Engine.
 *
 * Strict Zero-Persistence Guarantee:
 * - All extracted webpage text and NLP sentence segments exist exclusively in volatile RAM.
 * - Never writes to disk, SQLite, SharedPreferences, cache, logs, or Android Clipboard.
 * - Immediately wipes all volatile segments on stop, close, navigation, or teardown.
 */
class NlpContentAnnouncer(
  context: Context,
  private val onStateChanged: (NarratorState) -> Unit
) {

  companion object {
    private val ABBREVIATIONS = setOf(
      "mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.", "st.",
      "vs.", "etc.", "e.g.", "i.e.", "fig.", "no.", "vol.", "inc.",
      "ltd.", "co.", "u.s.", "u.k.", "u.n.", "a.m.", "p.m.", "jan.",
      "feb.", "mar.", "apr.", "jun.", "jul.", "aug.", "sep.", "oct.",
      "nov.", "dec."
    )

    private val URL_REGEX = Regex("""https?://([^\s/]+)[^\s]*""", RegexOption.IGNORE_CASE)
    private val MULTI_SPACE_REGEX = Regex("""\s+""")
    private val SYMBOL_NOISE_REGEX = Regex("""[#*`_~^|<>{}\[\]\\]+""")
    private val SENTENCE_SPLIT_REGEX = Regex("""(?<=[.!?。！？])\s+(?=[A-Z0-9"'(“‘\u00C0-\u024F\u0900-\u097F])""")

    /**
     * Pure on-device NLP normalizer converting web prose into natural spoken cadence.
     */
    fun normalizeForSpeech(raw: String): String {
      if (raw.isBlank()) return ""
      var text = raw
        .replace(URL_REGEX) { match ->
          val host = match.groupValues[1].removePrefix("www.")
          " link to $host "
        }
        .replace("\u2014", ", ")
        .replace("\u2013", ", ")
        .replace(" - ", ", ")
        .replace("&", " and ")
        .replace("%", " percent")
        .replace(SYMBOL_NOISE_REGEX, " ")
        .replace(Regex("""\s*([,;:])\s*"""), "$1 ")
        .replace(Regex("""([.!?]){2,}"""), "$1")
        .replace(MULTI_SPACE_REGEX, " ")
        .trim()

      // Expand common abbreviations for natural voice pronunciation
      text = text
        .replace(Regex("""\be\.g\.\b""", RegexOption.IGNORE_CASE), "for example")
        .replace(Regex("""\bi\.e\.\b""", RegexOption.IGNORE_CASE), "that is")
        .replace(Regex("""\bvs\.\b""", RegexOption.IGNORE_CASE), "versus")
        .replace(Regex("""\betc\.\b""", RegexOption.IGNORE_CASE), "and so on")
        .trim()

      return text
    }

    /**
     * Pure on-device NLP sentence segmenter preserving abbreviations and decimals.
     */
    fun segmentArticle(article: ReaderArticle): List<NarrationSegment> {
      val segments = mutableListOf<NarrationSegment>()
      var idx = 0

      val cleanTitle = article.title.trim()
      if (cleanTitle.isNotEmpty()) {
        val spokenTitle = normalizeForSpeech(cleanTitle)
        if (spokenTitle.isNotEmpty()) {
          segments += NarrationSegment(
            index = idx++,
            paragraphIndex = -1,
            displayText = cleanTitle,
            spokenText = spokenTitle,
            isHeading = true
          )
        }
      }

      article.paragraphs.forEachIndexed { pIdx, rawParagraph ->
        val para = rawParagraph.replace(MULTI_SPACE_REGEX, " ").trim()
        if (para.isEmpty()) return@forEachIndexed

        val sentences = splitIntoNaturalSentences(para)
        for (sentence in sentences) {
          val spoken = normalizeForSpeech(sentence)
          if (spoken.length >= 2) {
            segments += NarrationSegment(
              index = idx++,
              paragraphIndex = pIdx,
              displayText = sentence,
              spokenText = spoken,
              isHeading = false
            )
          }
        }
      }
      return segments
    }

    internal fun splitIntoNaturalSentences(paragraph: String): List<String> {
      val clean = paragraph.trim()
      if (clean.length <= 160) return listOf(clean)

      val rawParts = SENTENCE_SPLIT_REGEX.split(clean)
      val merged = mutableListOf<String>()
      val buffer = StringBuilder()

      for (part in rawParts) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        if (buffer.isNotEmpty()) buffer.append(' ')
        buffer.append(trimmed)

        val current = buffer.toString()
        val lastWord = current.substringAfterLast(' ').lowercase()
        val endsWithAbbrev = lastWord in ABBREVIATIONS ||
          (lastWord.length == 2 && lastWord[1] == '.' && lastWord[0].isLetter())
        if (!endsWithAbbrev) {
          // Guard against ultra-long unpunctuated sentences (> 360 chars) so TTS stays responsive
          if (current.length > 360) {
            chunkLongSentence(current).forEach { merged += it }
          } else {
            merged += current
          }
          buffer.setLength(0)
        }
      }
      if (buffer.isNotEmpty()) {
        val rem = buffer.toString()
        if (rem.length > 360) {
          chunkLongSentence(rem).forEach { merged += it }
        } else {
          merged += rem
        }
      }
      return if (merged.isEmpty()) listOf(clean) else merged
    }

    private fun chunkLongSentence(sentence: String): List<String> {
      val chunks = mutableListOf<String>()
      var start = 0
      while (start < sentence.length) {
        val maxEnd = (start + 280).coerceAtMost(sentence.length)
        if (maxEnd == sentence.length) {
          chunks += sentence.substring(start).trim()
          break
        }
        val commaBreak = sentence.lastIndexOf(',', maxEnd).takeIf { it > start + 80 }
        val spaceBreak = sentence.lastIndexOf(' ', maxEnd).takeIf { it > start + 40 }
        val splitAt = commaBreak?.plus(1) ?: spaceBreak ?: maxEnd
        val piece = sentence.substring(start, splitAt).trim()
        if (piece.isNotEmpty()) chunks += piece
        start = splitAt
      }
      return chunks
    }
  }

  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())

  private var tts: TextToSpeech? = null
  @Volatile private var isReady = false
  @Volatile private var pendingStartOnReady = false

  // Volatile RAM-only segments — never persisted, wiped immediately on stop/close
  private var segments: List<NarrationSegment> = emptyList()
  private var currentIdx: Int = 0
  private var isSpeakingNow: Boolean = false
  private var isPausedNow: Boolean = false
  private var speechRate: Float = 1.0f

  private fun ensureTtsInitialized() {
    if (tts != null) return
    try {
      tts = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          isReady = true
          try {
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(1.0f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
              override fun onStart(utteranceId: String?) {
                val segIdx = utteranceId?.removePrefix("kio_nlp_")?.toIntOrNull() ?: return
                mainHandler.post {
                  if (segments.isEmpty()) return@post
                  currentIdx = segIdx.coerceIn(0, segments.lastIndex)
                  isSpeakingNow = true
                  isPausedNow = false
                  emitState()
                }
              }

              override fun onDone(utteranceId: String?) {
                val segIdx = utteranceId?.removePrefix("kio_nlp_")?.toIntOrNull() ?: return
                mainHandler.post {
                  if (segments.isEmpty() || !isSpeakingNow || isPausedNow) return@post
                  val next = segIdx + 1
                  if (next < segments.size) {
                    speakFromIndex(next)
                  } else {
                    isSpeakingNow = false
                    isPausedNow = false
                    currentIdx = 0
                    emitState(statusNote = "Finished narrating page")
                  }
                }
              }

              @Deprecated("Deprecated in Java")
              override fun onError(utteranceId: String?) {
                mainHandler.post {
                  isSpeakingNow = false
                  isPausedNow = false
                  emitState(statusNote = "On-device voice engine unavailable")
                }
              }
            })
          } catch (_: Throwable) {
          }
          if (pendingStartOnReady && segments.isNotEmpty()) {
            pendingStartOnReady = false
            mainHandler.post { speakFromIndex(currentIdx) }
          }
        } else {
          isReady = false
          mainHandler.post {
            emitState(statusNote = "Install Android Text-to-Speech voice to narrate")
          }
        }
      }
    } catch (_: Throwable) {
      isReady = false
    }
  }

  fun startArticle(article: ReaderArticle, autoSpeak: Boolean = true): List<NarrationSegment> {
    val built = segmentArticle(article)
    segments = built
    currentIdx = 0
    isPausedNow = false
    isSpeakingNow = false
    if (built.isEmpty()) {
      emitState(statusNote = "No readable content found")
      return emptyList()
    }
    ensureTtsInitialized()
    if (autoSpeak) {
      if (isReady) {
        speakFromIndex(0)
      } else {
        pendingStartOnReady = true
        isSpeakingNow = true
        emitState(statusNote = "Initializing on-device NLP voice...")
      }
    } else {
      emitState()
    }
    return built
  }

  fun speakFromIndex(index: Int) {
    val list = segments
    if (list.isEmpty()) return
    val clamped = index.coerceIn(0, list.lastIndex)
    currentIdx = clamped
    isPausedNow = false
    ensureTtsInitialized()

    if (!isReady || tts == null) {
      pendingStartOnReady = true
      isSpeakingNow = true
      emitState(statusNote = "Preparing voice engine...")
      return
    }

    val seg = list[clamped]
    try {
      tts?.stop()
      tts?.setSpeechRate(speechRate)
      tts?.speak(seg.spokenText, TextToSpeech.QUEUE_FLUSH, null, "kio_nlp_$clamped")
      if (seg.isHeading) {
        tts?.playSilentUtterance(320L, TextToSpeech.QUEUE_ADD, "kio_pause_$clamped")
      }
      isSpeakingNow = true
      emitState()
    } catch (_: Throwable) {
      isSpeakingNow = false
      emitState(statusNote = "Speech engine unavailable")
    }
  }

  fun togglePlayPause() {
    if (segments.isEmpty()) return
    if (isSpeakingNow && !isPausedNow) {
      pause()
    } else {
      speakFromIndex(currentIdx)
    }
  }

  fun pause() {
    pendingStartOnReady = false
    try {
      tts?.stop()
    } catch (_: Throwable) {
    }
    isSpeakingNow = false
    isPausedNow = segments.isNotEmpty()
    emitState()
  }

  fun nextSegment() {
    if (segments.isEmpty()) return
    val next = (currentIdx + 1).coerceAtMost(segments.lastIndex)
    speakFromIndex(next)
  }

  fun previousSegment() {
    if (segments.isEmpty()) return
    val prev = (currentIdx - 1).coerceAtLeast(0)
    speakFromIndex(prev)
  }

  fun setSpeechRate(rate: Float) {
    speechRate = rate.coerceIn(0.6f, 2.25f)
    try {
      tts?.setSpeechRate(speechRate)
    } catch (_: Throwable) {
    }
    if (isSpeakingNow && !isPausedNow && segments.isNotEmpty()) {
      speakFromIndex(currentIdx)
    } else {
      emitState()
    }
  }

  /**
   * Immediately halts narration and wipes every extracted sentence from volatile RAM.
   */
  fun wipeAndStop() {
    pendingStartOnReady = false
    try {
      tts?.stop()
    } catch (_: Throwable) {
    }
    segments = emptyList()
    currentIdx = 0
    isSpeakingNow = false
    isPausedNow = false
    emitState()
  }

  fun shutdown() {
    wipeAndStop()
    try {
      tts?.shutdown()
    } catch (_: Throwable) {
    }
    tts = null
    isReady = false
  }

  private fun emitState(statusNote: String? = null) {
    onStateChanged(
      NarratorState(
        isActive = segments.isNotEmpty(),
        isSpeaking = isSpeakingNow,
        isPaused = isPausedNow,
        currentIndex = currentIdx,
        totalSegments = segments.size,
        speechRate = speechRate,
        statusNote = statusNote
      )
    )
  }
}
