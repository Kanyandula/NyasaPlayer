package com.example.nyasaplayer.core.playback

import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * T02: the origin the service publishes as session extras reaches every collector's snapshot, over a
 * real `MediaSession` and `MediaController`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QueueOriginExtrasTest {

    private lateinit var context: Context
    private lateinit var session: MediaSession
    private val collectors = mutableListOf<BasePlayerStateCollector>()

    private val album = QueueOrigin.Album(id = "al1", name = "Kalindula")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        session = MediaSession.Builder(context, ReconnectPlayer()).setId("t02").build()
    }

    @After
    fun tearDown() {
        collectors.forEach { it.releaseController() }
        session.release()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun connectedCollector(connection: ControllerConnection): BasePlayerStateCollector =
        object : BasePlayerStateCollector(connection, TestScope(UnconfinedTestDispatcher())) {
            override val positionPollIntervalMs: Long = 1_000L
        }.also {
            collectors += it
            it.connectController()
            idle()
        }

    private fun publish(origin: QueueOrigin) {
        session.setSessionExtras(Bundle().apply { putQueueOrigin(origin) })
        idle()
    }

    @Test
    fun a_published_origin_reaches_the_snapshot() {
        val collector = connectedCollector(ControllerConnection(context, session.token))

        publish(album)

        assertEquals(album, collector.playbackState.value.queueOrigin)
    }

    @Test
    fun an_origin_published_before_connecting_is_there_on_connect() {
        publish(album)

        val collector = connectedCollector(ControllerConnection(context, session.token))

        assertEquals(album, collector.playbackState.value.queueOrigin)
    }

    @Test
    fun every_collector_on_the_shared_connection_sees_it() {
        val connection = ControllerConnection(context, session.token)
        val car = connectedCollector(connection)
        val mobile = connectedCollector(connection)

        publish(QueueOrigin.Favourites)

        assertEquals(QueueOrigin.Favourites, car.playbackState.value.queueOrigin)
        assertEquals(QueueOrigin.Favourites, mobile.playbackState.value.queueOrigin)
    }

    @Test
    fun a_later_queue_with_no_origin_clears_it() {
        val collector = connectedCollector(ControllerConnection(context, session.token))
        publish(album)
        assertEquals("precondition", album, collector.playbackState.value.queueOrigin)

        publish(QueueOrigin.None)

        assertEquals(QueueOrigin.None, collector.playbackState.value.queueOrigin)
    }
}
