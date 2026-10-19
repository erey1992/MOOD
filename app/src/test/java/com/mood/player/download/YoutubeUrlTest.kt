package com.mood.player.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YoutubeUrlTest {
    private val id = "BaW_jenozKc"
    private val canonical = "https://www.youtube.com/watch?v=$id"

    @Test fun acceptsExplicitYoutubeMusicPlaylistAndKeepsSongLinksIndividual() {
        assertEquals(YoutubeLink("https://www.youtube.com/playlist?list=PL_test-123", true), youtubeLink("Escucha https://music.youtube.com/playlist?list=PL_test-123&si=tracking"))
        assertEquals(YoutubeLink(canonical, false), youtubeLink("https://music.youtube.com/watch?v=$id&list=PL_test-123"))
        assertEquals(YoutubeLink(canonical, false), youtubeLink("https://youtu.be/$id"))
        assertEquals(YoutubeLink("https://www.youtube.com/playlist?list=PL_test-123", true), youtubeLink("https://youtube.com/embed/videoseries?list=PL_test-123"))
        assertEquals(YoutubeLink("https://www.youtube.com/playlist?list=PL_test-123", true), youtubeLink("https://youtube.com/watch?list=PL_test-123"))
    }

    @Test fun rejectsUntrustedOrMalformedPlaylistLinks() {
        for (url in listOf("https://youtube.com.evil.test/playlist?list=PL_test", "https://user@youtube.com/playlist?list=PL_test", "https://youtube.com/playlist?list=../../abc", "https://youtube.com/playlist?list=%ZZ", "https://youtube.com/playlist", "https://example.com/playlist?list=PL_test")) assertNull(youtubeLink(url))
    }

    @Test fun stripsPlaylistAndTracking() {
        assertEquals(canonical, youtubeVideoUrl("https://www.youtube.com/watch?v=$id&list=PL123&index=2&t=12"))
        assertEquals(canonical, youtubeVideoUrl("https://youtu.be/$id?si=abc"))
    }

    @Test fun supportsSharedTextAndVideoVariants() {
        for (url in listOf("https://m.youtube.com/watch?v=$id", "https://music.youtube.com/watch?v=$id", "https://youtube.com/shorts/$id", "https://www.youtube.com/live/$id", "https://youtube.com/embed/$id")) {
            assertEquals(canonical, youtubeVideoUrl("Mira este video: $url"))
        }
    }

    @Test fun rejectsOtherHostsAndDisguisedUrls() {
        for (url in listOf("https://youtube.com.evil.test/watch?v=$id", "https://youtube.com@evil.test/watch?v=$id", "https://evil.test@youtube.com/watch?v=$id", "https://youtube.com:8080/watch?v=$id", "file:///youtube.com/watch?v=$id", "--exec rm")) {
            assertNull(youtubeVideoUrl(url))
        }
    }

    @Test fun rejectsPlaylistsAndMalformedVideoIds() {
        for (url in listOf("https://youtube.com/playlist?list=PL123", "https://youtube.com/watch", "https://youtu.be/short", "https://youtu.be/$id/extra", "https://youtube.com/watch?v=%ZZ", "https://youtube.com/watch?v=../../abcde")) {
            assertNull(youtubeVideoUrl(url))
        }
    }
}
