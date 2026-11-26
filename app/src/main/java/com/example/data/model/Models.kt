package com.example.data.model

import kotlinx.serialization.Serializable
import java.util.Locale

enum class VideoFormat(val extension: String, val mimeTypes: List<String>) {
    MP4("MP4", listOf("video/mp4")),
    MOV("MOV", listOf("video/quicktime")),
    MKV("MKV", listOf("video/x-matroska", "application/x-matroska")),
    WEBM("WEBM", listOf("video/webm")),
    AVI("AVI", listOf("video/x-msvideo", "video/avi", "video/msvideo"));

    companion object {
        val supportedExtensions = entries.map { it.extension.lowercase() }.toSet()

        fun fromFileNameOrMime(fileName: String, mimeType: String?): VideoFormat? {
            val ext = fileName.substringAfterLast('.', "").uppercase(Locale.US)
            entries.firstOrNull { it.extension == ext }?.let { return it }
            if (mimeType != null) {
                entries.firstOrNull { fmt -> fmt.mimeTypes.any { it.equals(mimeType, ignoreCase = true) } }
                    ?.let { return it }
            }
            return null
        }
    }
}

enum class SourceLanguage(
    val code: String,
    val englishName: String,
    val khmerName: String
) {
    AUTO("auto", "Auto Detect", "រកឃើញស្វ័យប្រវត្តិ"),
    ENGLISH("en", "English", "អង់គ្លេស"),
    CHINESE("zh", "Chinese (Mandarin)", "ចិន"),
    THAI("th", "Thai", "ថៃ"),
    VIETNAMESE("vi", "Vietnamese", "វៀតណាម"),
    JAPANESE("ja", "Japanese", "ជប៉ុន"),
    KOREAN("ko", "Korean", "កូរ៉េ"),
    FRENCH("fr", "French", "បារាំង"),
    SPANISH("es", "Spanish", "អេស្ប៉ាញ"),
    ARABIC("ar", "Arabic", "អារ៉ាប់"),
    HINDI("hi", "Hindi", "ហិណ្ឌូ");

    companion object {
        fun fromCode(code: String): SourceLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AUTO
    }
}

enum class DubbingVoiceProfile(
    val id: String,
    val englishName: String,
    val khmerName: String,
    val pitch: Float,
    val speechRate: Float
) {
    SREYPOV_FEMALE("sreypov", "SreyPov (Natural Female)", "ស្រីពៅ (សំឡេងនារីធម្មជាតិ)", 1.08f, 1.0f),
    DARA_MALE("dara", "Dara (Studio Male)", "តារា (សំឡេងបុរសស្ទូឌីយោ)", 0.86f, 0.98f),
    STUDIO_NARRATOR("narrator", "Vannak (Warm Narrator)", "វណ្ណៈ (អ្នកអត្ថាធិប្បាយ)", 0.94f, 0.93f);

    companion object {
        fun fromId(id: String): DubbingVoiceProfile =
            entries.firstOrNull { it.id == id } ?: SREYPOV_FEMALE
    }
}

enum class BackendMode(val id: String) {
    FIREBASE_AI_LOGIC("firebase_ai"),
    CUSTOM_HTTPS_SERVER("custom_https");

    companion object {
        fun fromId(id: String): BackendMode =
            entries.firstOrNull { it.id == id } ?: FIREBASE_AI_LOGIC
    }
}

enum class ProcessingStage(val stepOrder: Int) {
    IDLE(0),
    IMPORTING(1),
    EXTRACTING_AUDIO(2),
    TRANSCRIBING_TRANSLATING(3),
    SYNTHESIZING_VOICE(4),
    MIXING_RENDERING(5),
    GENERATING_SRT(6),
    COMPLETED(7),
    PAUSED_OFFLINE(8),
    FAILED(9)
}

@Serializable
data class SubtitleSegment(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val sourceText: String,
    val khmerText: String
)

enum class AudioPreviewMode {
    DUBBED_WITH_BGM,
    ORIGINAL_AUDIO,
    BGM_ONLY
}

object SrtFormatter {
    fun toSrt(segments: List<SubtitleSegment>, includeBilingual: Boolean = false): String {
        if (segments.isEmpty()) return ""
        return buildString {
            segments.sortedBy { it.startMs }.forEachIndexed { idx, segment ->
                val lineNumber = idx + 1
                val start = formatSrtTime(segment.startMs)
                val end = formatSrtTime(maxOf(segment.endMs, segment.startMs + 350L))
                append(lineNumber).append('\n')
                append(start).append(" --> ").append(end).append('\n')
                append(segment.khmerText.trim())
                if (includeBilingual && segment.sourceText.isNotBlank()) {
                    append('\n').append(segment.sourceText.trim())
                }
                append("\n\n")
            }
        }
    }

    fun formatSrtTime(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val minutes = (clamped % 3_600_000L) / 60_000L
        val seconds = (clamped % 60_000L) / 1_000L
        val millis = clamped % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun formatDisplayDuration(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSeconds = clamped / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
