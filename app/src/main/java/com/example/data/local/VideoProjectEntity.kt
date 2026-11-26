package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.data.model.ProcessingStage
import com.example.data.model.SubtitleSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

@Entity(tableName = "video_projects")
data class VideoProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val format: String,
    val sourceLanguageCode: String,
    val detectedLanguageCode: String? = null,
    val voiceProfileId: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val localSourceVideoPath: String,
    val extractedAudioPath: String? = null,
    val thumbnailPath: String? = null,
    val dubbedVideoPath: String? = null,
    val srtFilePath: String? = null,
    val segmentsJson: String = "[]",
    val enableDubbing: Boolean = true,
    val enableSubtitles: Boolean = true,
    val preserveBgm: Boolean = true,
    val bgmDuckingLevel: Float = 0.25f,
    val stageName: String = ProcessingStage.IDLE.name,
    val lastCompletedStageName: String = ProcessingStage.IDLE.name,
    val progress: Float = 0f,
    val ttsAvailable: Boolean = true,
    val errorMessage: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val stage: ProcessingStage
        get() = runCatching { ProcessingStage.valueOf(stageName) }.getOrDefault(ProcessingStage.IDLE)

    val lastCompletedStage: ProcessingStage
        get() = runCatching { ProcessingStage.valueOf(lastCompletedStageName) }
            .getOrDefault(ProcessingStage.IDLE)

    fun parsedSegments(): List<SubtitleSegment> =
        runCatching { Json.decodeFromString<List<SubtitleSegment>>(segmentsJson) }
            .getOrDefault(emptyList())
}

@Dao
interface VideoProjectDao {
    @Query("SELECT * FROM video_projects ORDER BY updatedAtMillis DESC")
    fun observeAllProjects(): Flow<List<VideoProjectEntity>>

    @Query("SELECT * FROM video_projects WHERE id = :id LIMIT 1")
    fun observeProjectById(id: String): Flow<VideoProjectEntity?>

    @Query("SELECT * FROM video_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): VideoProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: VideoProjectEntity)

    @Query("DELETE FROM video_projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("SELECT * FROM video_projects WHERE stageName IN ('PAUSED_OFFLINE', 'FAILED') ORDER BY updatedAtMillis DESC")
    suspend fun getInterruptedProjects(): List<VideoProjectEntity>
}
