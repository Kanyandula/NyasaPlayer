package com.example.nyasaplayer.core.playback

import android.os.Looper
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A collector that never connected: its controller is `null`, not disconnected.
 *
 * T27's tripwire is for the state nothing known produces — a controller that is there and dead.
 * Never having had one is ordinary, and must stay out of Crashlytics (T27 acceptance criterion 2).
 * Its own class because `ReconnectingCollectorTest.setUp` connects every time.
 */
@RunWith(RobolectricTestRunner::class)
class NeverConnectedCollectorTest {

    private lateinit var session: MediaSession
    private lateinit var collector: CountingCollector

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        session = MediaSession.Builder(context, ReconnectPlayer()).setId("t27-never").build()
        collector = CountingCollector(ControllerConnection(context, session.token))
    }

    @After
    fun tearDown() {
        session.release()
    }

    @Test
    fun aCommandBeforeAnyConnection_reportsNothing() {
        assertFalse("precondition: no controller was ever attached", collector.transport.play())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a null controller is expected, not evidence", 0, collector.disconnectedReports)
    }

    private class CountingCollector(connection: ControllerConnection) :
        BasePlayerStateCollector(connection, TestScope()) {

        var disconnectedReports = 0

        override val positionPollIntervalMs: Long = 1_000L

        override fun onControllerFoundDisconnected() {
            disconnectedReports++
        }
    }
}
