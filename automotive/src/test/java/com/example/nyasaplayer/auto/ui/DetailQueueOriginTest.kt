package com.example.nyasaplayer.auto.ui

import com.example.nyasaplayer.auto.ui.navigation.CarDestination
import com.example.nyasaplayer.core.playback.QueueOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

/** T03: a detail screen plays from the collection it shows, named once it has loaded. */
class DetailQueueOriginTest {

    @Test
    fun each_detail_names_its_collection() {
        assertEquals(QueueOrigin.Album("al1", "Kalindula"), CarDestination.Album("al1").queueOrigin("Kalindula"))
        assertEquals(QueueOrigin.Playlist("p1", "Road Trip"), CarDestination.Playlist("p1").queueOrigin("Road Trip"))
        assertEquals(
            QueueOrigin.Artist("ar1", "Lucius Banda"),
            CarDestination.CatalogArtist("ar1").queueOrigin("Lucius Banda"),
        )
    }

    @Test
    fun destinations_routed_elsewhere_have_no_origin_here() {
        assertEquals(QueueOrigin.None, CarDestination.Downloads.queueOrigin("Downloads"))
    }
}
