package com.example

import com.example.data.model.SrtFormatter
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoFormat
import com.example.data.network.SecureUrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `video format validator accepts MP4 MOV MKV WEBM AVI`() {
        assertEquals(VideoFormat.MP4, VideoFormat.fromFileNameOrMime("clip.mp4", null))
        assertEquals(VideoFormat.MOV, VideoFormat.fromFileNameOrMime("clip.MOV", null))
        assertEquals(VideoFormat.MKV, VideoFormat.fromFileNameOrMime("movie.mkv", null))
        assertEquals(VideoFormat.WEBM, VideoFormat.fromFileNameOrMime("web.webm", null))
        assertEquals(VideoFormat.AVI, VideoFormat.fromFileNameOrMime("legacy.avi", null))
        assertNull(VideoFormat.fromFileNameOrMime("notes.txt", "text/plain"))
    }

    @Test
    fun `srt formatter builds valid UTF-8 Khmer subtitle blocks`() {
        val segments = listOf(
            SubtitleSegment(1, 1200L, 4550L, "Hello everyone", "សួស្តីអ្នកទាំងអស់គ្នា"),
            SubtitleSegment(2, 5000L, 8250L, "Welcome", "សូមស្វាគមន៍")
        )
        val srt = SrtFormatter.toSrt(segments)
        assertTrue(srt.contains("1\n00:00:01,200 --> 00:00:04,550\nសួស្តីអ្នកទាំងអស់គ្នា"))
        assertTrue(srt.contains("2\n00:00:05,000 --> 00:00:08,250\nសូមស្វាគមន៍"))
    }

    @Test
    fun `secure url validator enforces HTTPS only and blocks credentials`() {
        assertTrue(SecureUrlValidator.isValidHttpsUrl("https://api.khmertranslator.com"))
        assertFalse(SecureUrlValidator.isValidHttpsUrl("http://api.khmertranslator.com"))
        assertFalse(SecureUrlValidator.isValidHttpsUrl("https://user:secret@api.khmertranslator.com"))
        assertFalse(SecureUrlValidator.isValidHttpsUrl(""))
    }
}
