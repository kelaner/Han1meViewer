package com.yenaly.han1meviewer.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadMetadataTest {

    @Test
    fun parseDownloadedMetadata_readsNestedArtistFields() {
        val metadata = parseDownloadedMetadata(
            """
            {
              "artist": {
                "name": "  Artist Name  ",
                "genre": "  Drama  ",
                "avatarUrl": "https://example.test/avatar.jpg"
              }
            }
            """.trimIndent()
        )

        assertEquals("Artist Name", metadata.author)
        assertEquals("Drama", metadata.genre)
    }

    @Test
    fun parseDownloadedMetadata_readsFieldsWithoutUnrelatedRequiredVideoFields() {
        val metadata = parseDownloadedMetadata(
            """
            {
              "artist": {
                "name": "Artist Name",
                "genre": "Drama"
              }
            }
            """.trimIndent()
        )

        assertEquals("Artist Name", metadata.author)
        assertEquals("Drama", metadata.genre)
    }

    @Test
    fun parseDownloadedMetadata_handlesMissingArtist() {
        val metadata = parseDownloadedMetadata("{\"title\":\"legacy video\"}")

        assertNull(metadata.author)
        assertNull(metadata.genre)
    }

    @Test
    fun parseDownloadedMetadata_handlesNullAndEmptyArtistFields() {
        val metadata = parseDownloadedMetadata(
            """
            {
              "artist": {
                "name": null,
                "genre": "   "
              }
            }
            """.trimIndent()
        )

        assertNull(metadata.author)
        assertNull(metadata.genre)
    }

    @Test
    fun parseDownloadedMetadata_handlesArtistFieldTypeErrorsWithoutCrashing() {
        val metadata = parseDownloadedMetadata(
            """
            {
              "artist": {
                "name": {"unexpected": true},
                "genre": ["unexpected"]
              }
            }
            """.trimIndent()
        )

        assertNull(metadata.author)
        assertNull(metadata.genre)
    }
}
