package io.github.mzuhairkhan.pause

import io.github.mzuhairkhan.pause.ui.theme.Accents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatTest {
    @Test fun formatsZeroAsMinuteSeconds() {
        assertEquals("0:00", TimeFormat.remainingLong(0))
    }

    @Test fun formatsSecondsWithLeadingZero() {
        assertEquals("0:05", TimeFormat.remainingLong(5))
        assertEquals("0:59", TimeFormat.remainingLong(59))
    }

    @Test fun formatsMinutesAndSeconds() {
        assertEquals("1:00", TimeFormat.remainingLong(60))
        assertEquals("9:09", TimeFormat.remainingLong(549))
        assertEquals("59:59", TimeFormat.remainingLong(3599))
    }

    @Test fun switchesToHoursAtAnHour() {
        assertEquals("1:00:00", TimeFormat.remainingLong(3600))
        assertEquals("2:05:09", TimeFormat.remainingLong(2 * 3600 + 5 * 60 + 9))
    }

    @Test fun treatsNegativeAsZero() {
        assertEquals("0:00", TimeFormat.remainingLong(-30))
    }
}

class HourglassMathTest {
    @Test fun fullProgressMapsToStartFill() {
        assertEquals(HourglassMath.START_FILL, HourglassMath.fill(1f), 1e-6f)
    }

    @Test fun emptyProgressMapsToEndFill() {
        assertEquals(HourglassMath.END_FILL, HourglassMath.fill(0f), 1e-6f)
    }

    @Test fun fillNeverReadsFullOrEmpty() {
        // Across the whole range the glyph stays strictly between empty and full.
        for (i in 0..100) {
            val fill = HourglassMath.fill(i / 100f)
            assertTrue(fill in 0.05f..0.85f)
        }
    }

    @Test fun fillIsMonotonicInProgress() {
        var previous = HourglassMath.fill(0f)
        for (i in 1..100) {
            val fill = HourglassMath.fill(i / 100f)
            assertTrue("fill should increase with progress", fill >= previous)
            previous = fill
        }
    }

    @Test fun outOfRangeProgressIsClamped() {
        assertEquals(HourglassMath.fill(0f), HourglassMath.fill(-1f), 1e-6f)
        assertEquals(HourglassMath.fill(1f), HourglassMath.fill(2f), 1e-6f)
    }

    @Test fun surfaceIsSqrtOfFill() {
        assertEquals(Math.sqrt(HourglassMath.fill(0.5f).toDouble()).toFloat(), HourglassMath.surface(0.5f), 1e-6f)
    }
}

class BubblePositionTest {
    @Test fun roundTripPreservesFractionWithinTolerance() {
        val max = 1080
        for (frac in listOf(0f, 0.25f, 0.5f, 0.7333f, 1f)) {
            val px = BubblePosition.toPixels(frac, max)
            val back = BubblePosition.toFraction(px, max)
            // Round-trips to within one pixel's worth of the original fraction.
            assertEquals(frac, back, 1f / max + 1e-4f)
        }
    }

    @Test fun pixelsClampedToBounds() {
        assertEquals(0, BubblePosition.toPixels(-0.5f, 500))
        assertEquals(500, BubblePosition.toPixels(1.5f, 500))
    }

    @Test fun zeroMaxNeverDividesByZero() {
        // A degenerate screen (e.g. before metrics settle) must not throw or yield NaN.
        assertEquals(0, BubblePosition.toPixels(0.5f, 0))
        assertEquals(0f, BubblePosition.toFraction(0, 0), 1e-6f)
    }

    @Test fun fractionClampedToUnitRange() {
        assertEquals(1f, BubblePosition.toFraction(9999, 500), 1e-6f)
        assertEquals(0f, BubblePosition.toFraction(-10, 500), 1e-6f)
    }
}

class BubblePresetsTest {
    @Test fun fixedPresetsIgnoreCustomValues() {
        assertEquals(0.122f, BubblePresets.metrics(BubblePresets.INSTAGRAM, 0.5f, 0.5f).sizeFraction, 1e-6f)
        assertEquals(0.134f, BubblePresets.metrics(BubblePresets.TIKTOK, 0.5f, 0.5f).sizeFraction, 1e-6f)
        assertEquals(0.019f, BubblePresets.metrics(BubblePresets.TIKTOK, 0.5f, 0.5f).edgeFraction, 1e-6f)
        assertEquals(0.111f, BubblePresets.metrics(BubblePresets.SHORTS, 0.5f, 0.5f).sizeFraction, 1e-6f)
        assertEquals(0.029f, BubblePresets.metrics(BubblePresets.SHORTS, 0.5f, 0.5f).edgeFraction, 1e-6f)
    }

    @Test fun customUsesSliderValues() {
        val m = BubblePresets.metrics(BubblePresets.CUSTOM, 0.18f, 0.05f)
        assertEquals(0.18f, m.sizeFraction, 1e-6f)
        assertEquals(0.05f, m.edgeFraction, 1e-6f)
    }

    @Test fun customClampsOutOfRange() {
        assertEquals(BubblePresets.SIZE_MAX, BubblePresets.metrics(BubblePresets.CUSTOM, 9f, 0.03f).sizeFraction, 1e-6f)
        assertEquals(BubblePresets.EDGE_MIN, BubblePresets.metrics(BubblePresets.CUSTOM, 0.15f, 0f).edgeFraction, 1e-6f)
    }

    @Test fun unknownPresetFallsBackToInstagram() {
        assertEquals(
            BubblePresets.metrics(BubblePresets.INSTAGRAM, 0f, 0f).sizeFraction,
            BubblePresets.metrics(99, 0f, 0f).sizeFraction,
            1e-6f
        )
    }
}

class SettingsRangesTest {
    @Test fun breathSecondsClampToOneToTwenty() {
        assertEquals(1, SettingsRanges.breathSeconds(0))
        assertEquals(1, SettingsRanges.breathSeconds(-99))
        assertEquals(20, SettingsRanges.breathSeconds(1000))
        assertEquals(7, SettingsRanges.breathSeconds(7))
    }

    @Test fun lockSecondsAllowZero() {
        assertEquals(0, SettingsRanges.lockSeconds(-5))
        assertEquals(60, SettingsRanges.lockSeconds(120))
        assertEquals(15, SettingsRanges.lockSeconds(15))
    }

    @Test fun blockMinutesClampToOneToOneTwenty() {
        assertEquals(1, SettingsRanges.blockMinutes(0))
        assertEquals(120, SettingsRanges.blockMinutes(99999))
        assertEquals(5, SettingsRanges.blockMinutes(5))
    }

    @Test fun snoozeMinutesClampToOneToSixty() {
        assertEquals(1, SettingsRanges.snoozeMinutes(0))
        assertEquals(60, SettingsRanges.snoozeMinutes(1000))
        assertEquals(5, SettingsRanges.snoozeMinutes(5))
    }

    @Test fun themeModeClampToValidEnum() {
        assertEquals(0, SettingsRanges.themeMode(-1))
        assertEquals(2, SettingsRanges.themeMode(9))
        assertEquals(1, SettingsRanges.themeMode(1))
    }

    @Test fun fractionClampsAndDefangsNaN() {
        assertEquals(0f, SettingsRanges.fraction(-1f), 1e-6f)
        assertEquals(1f, SettingsRanges.fraction(2f), 1e-6f)
        assertEquals(0.5f, SettingsRanges.fraction(0.5f), 1e-6f)
        assertEquals(0f, SettingsRanges.fraction(Float.NaN), 1e-6f)
    }
}

class SettingsDefaultsTest {
    @Test fun firstRunValuesAreTheChosenDefaults() {
        assertFalse(SettingsDefaults.SHOW_COUNTDOWN)   // bubble shows the draining hourglass
        assertTrue(SettingsDefaults.BREATHING_ENABLED) // wind-down on by default
        assertEquals(4, SettingsDefaults.INHALE_SECONDS)
        assertEquals(7, SettingsDefaults.HOLD_SECONDS)
        assertEquals(8, SettingsDefaults.EXHALE_SECONDS)
        assertEquals(30, SettingsDefaults.LOCK_SECONDS)
        assertEquals(5, SettingsDefaults.SNOOZE_MINUTES)
        assertEquals(30, SettingsDefaults.BLOCK_MINUTES)
    }

    @Test fun everyNumericDefaultIsWithinItsRange() {
        // A default outside its range would be silently clamped on read — a real footgun.
        assertEquals(SettingsDefaults.INHALE_SECONDS, SettingsRanges.breathSeconds(SettingsDefaults.INHALE_SECONDS))
        assertEquals(SettingsDefaults.HOLD_SECONDS, SettingsRanges.breathSeconds(SettingsDefaults.HOLD_SECONDS))
        assertEquals(SettingsDefaults.EXHALE_SECONDS, SettingsRanges.breathSeconds(SettingsDefaults.EXHALE_SECONDS))
        assertEquals(SettingsDefaults.LOCK_SECONDS, SettingsRanges.lockSeconds(SettingsDefaults.LOCK_SECONDS))
        assertEquals(SettingsDefaults.SNOOZE_MINUTES, SettingsRanges.snoozeMinutes(SettingsDefaults.SNOOZE_MINUTES))
        assertEquals(SettingsDefaults.BLOCK_MINUTES, SettingsRanges.blockMinutes(SettingsDefaults.BLOCK_MINUTES))
    }
}

class AccentsTest {
    @Test fun namesAlignWithColors() {
        assertEquals(Accents.colors.size, Accents.names.size)
    }

    @Test fun defaultAccentIsBlueAndListedFirst() {
        assertTrue(Accents.DEFAULT in Accents.colors.indices)
        // The chosen default accent is light blue, and it's the first swatch shown.
        assertEquals(0, Accents.DEFAULT)
        assertEquals(0xFF4C8DFF.toInt(), Accents.colors[0])
        assertEquals("Blue", Accents.names[0])
    }
}

class CompactDurationTest {

    @Test
    fun `rounds up so the countdown never understates the time left`() {
        // 1s past the minute still reads as 2m: better to overstate than to claim less.
        assertEquals(CompactDuration.Parts(CompactDuration.Scale.MINUTES, 2), CompactDuration.of(61))
        assertEquals(CompactDuration.Parts(CompactDuration.Scale.HOURS, 2), CompactDuration.of(3601))
    }

    @Test
    fun `picks the largest unit that fits`() {
        assertEquals(CompactDuration.Scale.SECONDS, CompactDuration.of(59).scale)
        assertEquals(CompactDuration.Scale.MINUTES, CompactDuration.of(60).scale)
        assertEquals(CompactDuration.Scale.MINUTES, CompactDuration.of(3599).scale)
        assertEquals(CompactDuration.Scale.HOURS, CompactDuration.of(3600).scale)
    }

    @Test
    fun `exact boundaries do not round up a whole extra unit`() {
        assertEquals(CompactDuration.Parts(CompactDuration.Scale.MINUTES, 1), CompactDuration.of(60))
        assertEquals(CompactDuration.Parts(CompactDuration.Scale.HOURS, 1), CompactDuration.of(3600))
        assertEquals(CompactDuration.Parts(CompactDuration.Scale.MINUTES, 25), CompactDuration.of(1500))
    }

    @Test
    fun `clamps negatives rather than reporting a negative countdown`() {
        assertEquals(CompactDuration.Parts(CompactDuration.Scale.SECONDS, 0), CompactDuration.of(-5))
        assertEquals(CompactDuration.Parts(CompactDuration.Scale.SECONDS, 0), CompactDuration.of(0))
    }
}

class CloseSystemDialogsTest {
    @Test
    fun `homekey and recentapps close the overlay`() {
        assertTrue(CloseSystemDialogs.closesOverlayForReason("homekey"))
        assertTrue(CloseSystemDialogs.closesOverlayForReason("recentapps"))
    }

    @Test
    fun `an unrelated or missing reason leaves the overlay up`() {
        assertFalse(CloseSystemDialogs.closesOverlayForReason("lock"))
        assertFalse(CloseSystemDialogs.closesOverlayForReason("assist"))
        assertFalse(CloseSystemDialogs.closesOverlayForReason(null))
    }
}

class SessionRestoreTest {
    private val now = 1_000_000L

    @Test
    fun `no persisted timer means idle`() {
        assertEquals(SessionRestore.Decision.Idle, SessionRestore.decide(0L, 0L, now))
    }

    @Test
    fun `a deadline still ahead resumes the timer`() {
        val decision = SessionRestore.decide(now - 5_000L, now + 60_000L, now)
        assertEquals(SessionRestore.Decision.ResumeTimer(now - 5_000L, now + 60_000L), decision)
    }

    @Test
    fun `a deadline exactly now counts as expired, not resumed`() {
        // A late render must never arm a Chronometer-style countdown on zero or negative time.
        assertEquals(SessionRestore.Decision.TimerExpiredWhileDead, SessionRestore.decide(now - 60_000L, now, now))
    }

    @Test
    fun `a deadline that already passed while the process was dead is caught up, not resumed`() {
        val decision = SessionRestore.decide(now - 120_000L, now - 1_000L, now)
        assertEquals(SessionRestore.Decision.TimerExpiredWhileDead, decision)
    }

    @Test
    fun `a break deadline still ahead is still active`() {
        assertTrue(SessionRestore.breakStillActive(now + 1L, now))
        assertFalse(SessionRestore.breakStillActive(now, now))
        assertFalse(SessionRestore.breakStillActive(0L, now))
    }
}

class BreakPollingTest {
    @Test
    fun `the foreground app is queried only while the screen is on`() {
        // The foreground app cannot change while the screen is off, so skipping the query
        // there is free correctness, not just a battery optimization.
        assertTrue(BreakPolling.shouldQueryForeground(screenOn = true))
        assertFalse(BreakPolling.shouldQueryForeground(screenOn = false))
    }
}
