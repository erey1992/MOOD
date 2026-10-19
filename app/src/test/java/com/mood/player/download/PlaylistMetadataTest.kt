package com.mood.player.download

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaylistMetadataTest {
    @Test fun readsPlaylistOrderMetadataAndDeduplicatesSongs() {
        val result = parsePlaylistMetadata("""{"title":"Mi lista","entries":[
            {"id":"jNQXAC9IVRw","title":"Primera","channel":"Autor","duration":19.5},
            {"id":"BaW_jenozKc","title":"Segunda","duration":null},
            {"id":"jNQXAC9IVRw","title":"Repetida"},null,{"id":"invalid"}] }""")
        assertEquals("Mi lista", result.title)
        assertEquals(listOf("Primera", "Segunda"), result.songs.map { it.title })
        assertEquals("Autor", result.songs[0].author)
        assertEquals(19500L, result.songs[0].durationMs)
        assertNull(result.songs[1].durationMs)
        assertEquals(2, result.unavailable)
    }

    @Test fun prefersMusicTitleAndArtistAndDoesNotInventMissingMetadata() {
        val song = parseSongMetadata(JSONObject("""{"id":"jNQXAC9IVRw","title":"Título del video","track":"Canción","artist":"Artista","uploader":"Canal","duration":180}"""))!!
        assertEquals("Canción", song.title)
        assertEquals("Artista", song.author)
        val unknown = parseSongMetadata(JSONObject("""{"id":"BaW_jenozKc","title":null,"uploader":null}"""))!!
        assertNull(unknown.author)
        assertNull(unknown.durationMs)
        assertNull(unknown.title)
    }

    @Test fun rejectsNonYoutubeEntriesAndKeepsUnavailableVideoForIndividualRetry() {
        assertNull(parseSongMetadata(JSONObject("""{"id":"bad","url":"https://evil.test/watch?v=jNQXAC9IVRw"}""")))
        val result = parsePlaylistMetadata("""{"entries":[{"id":"jNQXAC9IVRw","title":"[Private video]","availability":"private"}]}""")
        assertEquals(1, result.songs.size)
        assertEquals("https://www.youtube.com/watch?v=jNQXAC9IVRw", result.songs.single().url)
    }
}
