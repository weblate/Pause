package io.github.mzuhairkhan.pause

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * Proves the screen-state receiver's lifecycle: registered for the life of a "Stop for now"
 * break (so [BreakPolling] has a real [screenOn][BreakPolling.shouldQueryForeground] to gate
 * on), and unregistered once the break ends. Does not attempt to prove the foreground query
 * itself is skipped while the screen is off -- that would mean faking `UsageStatsManager`
 * events, which is disproportionate to what's actually load-bearing here: the pure gate
 * function (see [BreakPollingTest][io.github.mzuhairkhan.pause.BreakPollingTest]) already
 * covers the decision, so what's left to prove is that the service actually wires it up.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BreakScreenStateTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val screenOffIntent = Intent(Intent.ACTION_SCREEN_OFF)

    private var createdService: OverlayService? = null

    private fun newService(): OverlayService =
        Robolectric.buildService(OverlayService::class.java).create().get().also { createdService = it }

    @After
    fun tearDown() {
        createdService?.onDestroy()
        createdService = null
    }

    @Test
    fun `resuming a break on a sticky restart registers the screen-state receiver`() {
        ShadowSettings.setCanDrawOverlays(true)
        val until = System.currentTimeMillis() + 10 * 60_000L
        PauseState.setBreak(app, until, setOf("com.example.blocked"))

        assertFalse(
            "precondition: nothing has registered a screen-state receiver yet",
            shadowOf(app).hasReceiverForIntent(screenOffIntent)
        )

        newService().onStartCommand(null, 0, 1)

        assertTrue(
            "a resumed break must register a screen-state receiver, or the poll can never skip",
            shadowOf(app).hasReceiverForIntent(screenOffIntent)
        )
    }

    @Test
    fun `stopping the service while a break is active unregisters the screen-state receiver`() {
        ShadowSettings.setCanDrawOverlays(true)
        val until = System.currentTimeMillis() + 10 * 60_000L
        PauseState.setBreak(app, until, setOf("com.example.blocked"))

        val service = newService()
        service.onStartCommand(null, 0, 1)
        assertTrue(
            "precondition: the break is active and the receiver is registered",
            shadowOf(app).hasReceiverForIntent(screenOffIntent)
        )

        service.onDestroy()

        assertFalse(
            "stopping the service must not leave the screen-state receiver registered",
            shadowOf(app).hasReceiverForIntent(screenOffIntent)
        )
    }
}
