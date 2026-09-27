package com.example.nyasaplayer.core.data.dto

import com.example.nyasaplayer.core.common.models.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class FirestorePlaybackStateDtoTest {

    @Test
    fun origin_fields_survive_the_dto() {
        val state = PlaybackState(
            currentSongId = "a",
            queueSongIds = listOf("a"),
            originKind = "playlist",
            originId = "p1",
            originName = "Road Trip",
        )

        assertEquals(state, FirestorePlaybackStateDto.fromDomain(state).toDomain())
    }

    @Test
    fun a_document_saved_before_origins_has_blank_origin_fields() {
        val restored = FirestorePlaybackStateDto(currentSongId = "a").toDomain()

        assertEquals("", restored.originKind)
        assertEquals("", restored.originId)
        assertEquals("", restored.originName)
    }
}
