package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.SettingsRepository
import com.example.data.local.VideoProjectDao
import com.example.data.local.VideoProjectEntity
import com.example.data.model.BackendMode
import com.example.data.model.DubbingVoiceProfile
import com.example.data.model.ProcessingStage
import com.example.data.model.SourceLanguage
import com.example.data.model.SrtFormatter
import com.example.data.model.SubtitleSegment
import com.example.data.network.GeminiTranslationService
import com.example.data.network.NetworkMonitor
import com.example.data.network.SecureBackendClient
import com.example.data.network.SecureUrlValidator
import com.example.media.AudioDubbingMixer
import com.example.media.AudioExtractor
import com.example.media.KhmerVoiceSynthesizer
import com.example.media.MediaExportHelper
import com.example.media.VideoImportHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val TAG = "TranslationRepo"

class TranslationRepository(
    private val context: Context,
    private val dao: VideoProjectDao,
    private val settingsRepository: SettingsRepository,
    val networkMonitor: NetworkMonitor,
    private val geminiService: GeminiTranslationService = GeminiTranslationService(),
    val backendClient: SecureBackendClient = SecureBackendClient(),
    private val importHelper: VideoImportHelper = VideoImportHelper(context),
    private val audioExtractor: AudioExtractor = AudioExtractor(context),
    private val voiceSynthesizer: KhmerVoiceSynthesizer = KhmerVoiceSynthesizer(context),
    private val dubbingMixer: AudioDubbingMixer = AudioDubbingMixer(context),
    val exportHelper: MediaExportHelper = MediaExportHelper(context)
) {
    val allProjects: Flow<List<VideoProjectEntity>> = dao.observeAllProjects()

    fun observeProject(projectId: String): Flow<VideoProjectEntity?> =
        dao.observeProjectById(projectId)

    suspend fun importVideo(
        uri: Uri,
        sourceLanguage: SourceLanguage,
        voiceProfile: DubbingVoiceProfile,
        enableDubbing: Boolean,
        enableSubtitles: Boolean,
        preserveBgm: Boolean,
        bgmDuckingLevel: Float
    ): VideoProjectEntity {
        val meta = importHelper.importFromUri(uri)
        val project = VideoProjectEntity(
            id = meta.projectId,
            title = meta.title,
            format = meta.format.extension,
            sourceLanguageCode = sourceLanguage.code,
            voiceProfileId = voiceProfile.id,
            durationMs = meta.durationMs,
            width = meta.width,
            height = meta.height,
            fileSizeBytes = meta.fileSizeBytes,
            localSourceVideoPath = meta.localVideoFile.absolutePath,
            thumbnailPath = meta.thumbnailFile?.absolutePath,
            enableDubbing = enableDubbing,
            enableSubtitles = enableSubtitles,
            preserveBgm = preserveBgm,
            bgmDuckingLevel = bgmDuckingLevel,
            stageName = ProcessingStage.IDLE.name,
            lastCompletedStageName = ProcessingStage.IMPORTING.name,
            progress = 0.05f
        )
        dao.upsertProject(project)
        return project
    }

    suspend fun processProject(projectId: String): VideoProjectEntity {
        var current = dao.getProjectById(projectId)
            ?: throw IllegalStateException("រកមិនឃើញគម្រោងវីដេអូទេ។")

        val sourceVideoFile = File(current.localSourceVideoPath)
        if (!sourceVideoFile.exists()) {
            throw IllegalStateException("បាត់ឯកសារវីដេអូដើម។ សូមនាំចូលម្តងទៀត។")
        }

        try {
            // Stage 1: Extract compact audio (skip if already extracted for seamless resume)
            val existingAudio = current.extractedAudioPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
            val audioFile = if (existingAudio != null) {
                existingAudio
            } else {
                current = updateStage(current, ProcessingStage.EXTRACTING_AUDIO, 0.14f)
                val extracted = audioExtractor.extractCompactAudio(current.id, sourceVideoFile)
                current = current.copy(
                    extractedAudioPath = extracted.absolutePath,
                    lastCompletedStageName = ProcessingStage.EXTRACTING_AUDIO.name,
                    updatedAtMillis = System.currentTimeMillis()
                )
                dao.upsertProject(current)
                extracted
            }

            // Stage 2: AI Speech Transcription & Khmer Translation (skip if segments already present)
            var segments = current.parsedSegments()
            if (segments.isEmpty()) {
                if (!networkMonitor.checkCurrentlyOnline()) {
                    throw OfflineInterruptionException("គ្មានការតភ្ជាប់អ៊ីនធឺណិត។ កិច្ចការត្រូវបានផ្អាកដោយសុវត្ថិភាព។")
                }

                current = updateStage(current, ProcessingStage.TRANSCRIBING_TRANSLATING, 0.35f)
                val settings = settingsRepository.settingsFlow.first()
                val translationOutput = if (settings.backendMode == BackendMode.CUSTOM_HTTPS_SERVER) {
                    if (!SecureUrlValidator.isValidHttpsUrl(settings.customHttpsUrl)) {
                        throw IllegalStateException("អាសយដ្ឋានម៉ាស៊ីនមេ HTTPS ក្នុងការកំណត់មិនត្រឹមត្រូវទេ។")
                    }
                    backendClient.translateAudioFile(
                        baseUrl = settings.customHttpsUrl,
                        audioFile = audioFile,
                        sourceLanguageCode = current.sourceLanguageCode,
                        voiceProfileId = current.voiceProfileId
                    )
                } else {
                    geminiService.transcribeAndTranslateToKhmer(
                        audioFile = audioFile,
                        mimeType = "audio/mp4",
                        sourceLanguage = SourceLanguage.fromCode(current.sourceLanguageCode),
                        durationMs = current.durationMs
                    )
                }

                segments = translationOutput.segments
                current = current.copy(
                    detectedLanguageCode = translationOutput.detectedLanguageCode,
                    segmentsJson = Json.encodeToString(segments),
                    lastCompletedStageName = ProcessingStage.TRANSCRIBING_TRANSLATING.name,
                    progress = 0.58f,
                    updatedAtMillis = System.currentTimeMillis()
                )
                dao.upsertProject(current)
            }

            // Stage 3: Khmer AI Voice Dubbing Synthesis
            var ttsAvailable = true
            var ttsClips = emptyMap<Int, File>()
            if (current.enableDubbing && segments.isNotEmpty()) {
                current = updateStage(current, ProcessingStage.SYNTHESIZING_VOICE, 0.64f)
                val voiceProfile = DubbingVoiceProfile.fromId(current.voiceProfileId)
                val ttsResult = voiceSynthesizer.synthesizeSegments(
                    projectId = current.id,
                    segments = segments,
                    voiceProfile = voiceProfile
                ) { done, total ->
                    val frac = 0.64f + 0.16f * (done.toFloat() / maxOf(1, total))
                    current = current.copy(progress = frac)
                }
                ttsAvailable = ttsResult.ttsAvailable
                ttsClips = ttsResult.clipFilesBySegmentIndex
            }

            // Stage 4: BGM & SFX Preservation + Dubbed Video Rendering
            current = updateStage(current, ProcessingStage.MIXING_RENDERING, 0.84f)
            val dubbedMp4 = dubbingMixer.mixAndRenderDubbedVideo(
                projectId = current.id,
                sourceVideoFile = sourceVideoFile,
                extractedAudioFile = audioFile,
                durationMs = current.durationMs,
                segments = segments,
                ttsClips = ttsClips,
                enableDubbing = current.enableDubbing,
                preserveBgm = current.preserveBgm,
                bgmDuckingLevel = current.bgmDuckingLevel
            )

            // Stage 5: Khmer SRT Subtitle Generation
            current = updateStage(current, ProcessingStage.GENERATING_SRT, 0.94f)
            val srtFile = if (current.enableSubtitles) {
                val srtContent = SrtFormatter.toSrt(segments, includeBilingual = false)
                exportHelper.writeSrtToAppStorage(current.id, srtContent)
            } else {
                null
            }

            val completed = current.copy(
                dubbedVideoPath = dubbedMp4.absolutePath,
                srtFilePath = srtFile?.absolutePath,
                ttsAvailable = ttsAvailable,
                stageName = ProcessingStage.COMPLETED.name,
                lastCompletedStageName = ProcessingStage.COMPLETED.name,
                progress = 1f,
                errorMessage = null,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertProject(completed)
            return completed
        } catch (e: CancellationException) {
            throw e
        } catch (e: OfflineInterruptionException) {
            val paused = current.copy(
                stageName = ProcessingStage.PAUSED_OFFLINE.name,
                errorMessage = e.message,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertProject(paused)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video processing failed", e)
            val isNetworkFailure = e is UnknownHostException ||
                e is SocketTimeoutException ||
                e is IOException ||
                !networkMonitor.checkCurrentlyOnline()
            val stage = if (isNetworkFailure) ProcessingStage.PAUSED_OFFLINE else ProcessingStage.FAILED
            val failed = current.copy(
                stageName = stage.name,
                errorMessage = e.localizedMessage ?: "ដំណើរការបកប្រែវីដេអូត្រូវបានរំខាន។",
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertProject(failed)
            throw e
        }
    }

    suspend fun updateSubtitles(projectId: String, updatedSegments: List<SubtitleSegment>): VideoProjectEntity? {
        val current = dao.getProjectById(projectId) ?: return null
        val normalized = updatedSegments.sortedBy { it.startMs }
            .mapIndexed { idx, seg -> seg.copy(index = idx + 1) }
        val srtFile = exportHelper.writeSrtToAppStorage(
            projectId = projectId,
            srtContent = SrtFormatter.toSrt(normalized, includeBilingual = false)
        )
        val updated = current.copy(
            segmentsJson = Json.encodeToString(normalized),
            srtFilePath = srtFile.absolutePath,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertProject(updated)
        return updated
    }

    suspend fun deleteProject(projectId: String) {
        val project = dao.getProjectById(projectId)
        if (project != null) {
            runCatching { File(project.localSourceVideoPath).delete() }
            project.extractedAudioPath?.let { runCatching { File(it).delete() } }
            project.thumbnailPath?.let { runCatching { File(it).delete() } }
            project.dubbedVideoPath?.let { runCatching { File(it).delete() } }
            project.srtFilePath?.let { runCatching { File(it).delete() } }
            runCatching { File(context.filesDir, "tts/$projectId").deleteRecursively() }
        }
        dao.deleteProjectById(projectId)
    }

    suspend fun clearTemporaryCache() {
        runCatching { context.cacheDir.deleteRecursively() }
        runCatching { File(context.filesDir, "tts").deleteRecursively() }
    }

    private suspend fun updateStage(
        project: VideoProjectEntity,
        stage: ProcessingStage,
        progress: Float
    ): VideoProjectEntity {
        val updated = project.copy(
            stageName = stage.name,
            progress = progress,
            errorMessage = null,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertProject(updated)
        return updated
    }
}

class OfflineInterruptionException(message: String) : IOException(message)
