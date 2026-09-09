package io.github.mzuhairkhan.pause

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * Drives [OverlayService] under Robolectric to prove the null-intent (OS sticky-restart) branch
 * of `onStartCommand` actually resumes a persisted session instead of discarding it -- the bug a
 * throwaway probe first proved with `expected:<1> but was:<0>` before this fix existed.
 *
 * [OverlayBackTest][io.github.mzuhairkhan.pause.OverlayBackTest]'s KDoc rejects driving this
 * service from an *instrumented* test, because starting a `specialUse` foreground service on a
 * real device hits background-start restrictions and would be flaky. That reasoning does not
 * carry over here: Robolectric has no real `system_server` to restrict anything, so calling
 * `onStartCommand` directly is neither flaky nor uninformative -- it is a real assertion against
 * real (shadowed) `AlarmManager`, `NotificationManager` and `AudioManager` state.
 *
 * `PauseAlarm` and `PauseState` are driven directly rather than through the service's private
 * `scheduleTimer`/`startBreakIfConfigured` -- they are what a now-dead *previous* process would
 * have already written before the OS restarts this one, which is exactly the scenario under test.
 *
 * [tearDown] always calls `onDestroy()` on whatever was created: `OverlayService.running` is a
 * companion `MutableStateFlow`, and Robolectric can reuse its sandbox (and so the same loaded
 * companion object) across test *classes* with a matching `@Config` -- a test here that left it
 * `true` was observed breaking `SettingsGatingTest`'s "starts idle" assumption in CI, despite the
 * two files sharing nothing else. `onDestroy()` also stops the real `HandlerThread` the break
 * test's `ensurePollThread()` starts, which would otherwise leak across the whole test JVM.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OverlayServiceTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val alarmManager get() = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val audioManager get() = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var createdService: OverlayService? = null

    private fun newService(): OverlayService =
        Robolectric.buildService(OverlayService::class.java).create().get().also { createdService = it }

    @After
    fun tearDown() {
        // onDestroy() is idempotent (every teardown step is null-safe or already-cancelled), so
        // this is safe even for the test that already calls it itself as part of the assertion.
        createdService?.onDestroy()
        createdService = null
    }

    @Test
    fun `a null-intent restart resumes an armed timer instead of cancelling it`() {
        ShadowSettings.setCanDrawOverlays(true)
        val end = System.currentTimeMillis() + 30 * 60_000L
        // What the previous, now-dead process would have left behind before it was killed.
        PauseState.setTimer(app, System.currentTimeMillis(), end)
        PauseAlarm.schedule(app, end)
        assertEquals("precondition: the timer alarm is armed", 1, shadowOf(alarmManager).scheduledAlarms.size)

        val service = newService()
        val result = service.onStartCommand(null, 0, 1)

        assertEquals(android.app.Service.START_STICKY, result)
        assertEquals(
            "a sticky restart must not cancel the alarm the dead process already armed",
            1,
            shadowOf(alarmManager).scheduledAlarms.size
        )
        assertEquals(
            "the persisted timer must still be on disk after a sticky restart",
            end,
            PauseState.snapshot(app).timerEndMillis
        )
    }

    @Test
    fun `an explicit start still resets to idle even with stale persisted state`() {
        ShadowSettings.setCanDrawOverlays(true)
        // Simulate leftover state from a session that ended abnormally -- an explicit start
        // (the Start button, the notification action) must not resume it; only a null-intent
        // OS restart should.
        val end = System.currentTimeMillis() + 30 * 60_000L
        PauseState.setTimer(app, System.currentTimeMillis(), end)
        PauseAlarm.schedule(app, end)

        val service = newService()
        service.onStartCommand(Intent(app, OverlayService::class.java), 0, 1)

        assertTrue(
            "an explicit start must cancel any stale alarm",
            shadowOf(alarmManager).scheduledAlarms.isEmpty()
        )
        assertEquals(0L, PauseState.snapshot(app).timerEndMillis)
    }

    @Test
    fun `a timer that expired while the process was dead fires the wind-down`() {
        ShadowSettings.setCanDrawOverlays(true)
        // The deadline is already in the past -- the alarm either already fired into a dead
        // process or never got the chance -- so a sticky restart must catch it up now rather
        // than silently dropping it.
        val end = System.currentTimeMillis() - 5_000L
        PauseState.setTimer(app, end - 60_000L, end)
        // Give muteMedia() something to zero, so a nonzero->zero transition actually proves
        // showBreathing() ran -- resetToIdle() alone never touches audio.
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)

        val service = newService()
        service.onStartCommand(null, 0, 1)

        assertEquals(0L, PauseState.snapshot(app).timerEndMillis)
        assertTrue(
            "an expired timer must still fire the wind-down, which mutes media on entry",
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        )
    }

    @Test
    fun `break state survives a sticky restart`() {
        ShadowSettings.setCanDrawOverlays(true)
        val until = System.currentTimeMillis() + 10 * 60_000L
        PauseState.setBreak(app, until, setOf("com.example.blocked"))

        val service = newService()
        service.onStartCommand(null, 0, 1)
        // Let the resumed break's first poll tick run once on the main looper.
        shadowOf(Looper.getMainLooper()).idle()

        // If the restored deadline weren't still in the future, the poll's own guard
        // (remaining <= 0 -> stopBreak()) would have cleared this immediately.
        assertEquals(
            "a break that's still ahead must not be torn down by its own first poll",
            until,
            PauseState.snapshot(app).breakUntilMillis
        )
    }

    @Test
    fun `onDestroy still cancels a pending alarm on a manual stop`() {
        val end = System.currentTimeMillis() + 30 * 60_000L
        PauseAlarm.schedule(app, end)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        val service = newService()
        service.onDestroy()

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertFalse("a manually stopped service must report itself as not running", OverlayService.running.value)
    }

    @Test
    fun `a manual stop also clears the persisted timer, not just the alarm`() {
        // onDestroy cancels the alarm, so nothing will ever fire it again. Leaving the deadline
        // on disk would make every PauseState reader (the widget, a later restore) believe a
        // timer is still running that nothing backs. stopBreak() already clears its half.
        ShadowSettings.setCanDrawOverlays(true)
        val end = System.currentTimeMillis() + 30 * 60_000L
        PauseState.setTimer(app, System.currentTimeMillis(), end)
        PauseAlarm.schedule(app, end)

        val service = newService()
        service.onStartCommand(null, 0, 1)
        assertEquals("precondition: the timer resumed", end, PauseState.snapshot(app).timerEndMillis)

        service.onDestroy()

        assertEquals(
            "a stopped service must not leave a timer on disk that no alarm backs",
            0L,
            PauseState.snapshot(app).timerEndMillis
        )
    }
}
