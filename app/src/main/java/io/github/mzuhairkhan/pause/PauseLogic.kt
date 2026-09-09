package io.github.mzuhairkhan.pause

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Time formatting, hourglass fill remap, bubble placement and settings clamping, kept free
 * of [android.content.Context] and every framework type so they unit-test on a plain JVM.
 */

/** Formats a remaining duration as `m:ss`, or `h:mm:ss` once it reaches an hour. */
object TimeFormat {
    fun remainingLong(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.US, "%d:%02d", m, sec)
        }
    }
}

/**
 * Hourglass fill remap. [progress] is the fraction of time remaining, squeezed into
 * [[END_FILL], [START_FILL]] so the glyph never reads full or empty; the surface tracks
 * the square root of the volume for the conical taper.
 */
object HourglassMath {
    const val START_FILL = 0.80f
    const val END_FILL = 0.06f

    fun fill(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return END_FILL + (START_FILL - END_FILL) * p
    }

    fun surface(progress: Float): Float = sqrt(fill(progress))
}

/**
 * Converts the bubble's stored fractional position (0..1 of the draggable area) to screen
 * pixels. A fraction rather than pixels keeps it at the same relative spot across rotations.
 */
object BubblePosition {
    fun toPixels(fraction: Float, max: Int): Int =
        (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max.coerceAtLeast(0))

    fun toFraction(pixel: Int, max: Int): Float =
        (pixel.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)
}

/**
 * Bounds for the user-tunable settings: clamps values read back from storage, so a corrupt
 * pref can't feed an animation or alarm, and bounds the setup steppers.
 */
object SettingsRanges {
    const val BREATH_MIN_SECONDS = 1
    const val BREATH_MAX_SECONDS = 20
    const val LOCK_MIN_SECONDS = 0
    const val LOCK_MAX_SECONDS = 60
    const val BLOCK_MIN_MINUTES = 1
    const val BLOCK_MAX_MINUTES = 120
    const val SNOOZE_MIN_MINUTES = 1
    const val SNOOZE_MAX_MINUTES = 60
    const val THEME_MIN = 0
    const val THEME_MAX = 2

    fun breathSeconds(value: Int): Int = value.coerceIn(BREATH_MIN_SECONDS, BREATH_MAX_SECONDS)
    fun lockSeconds(value: Int): Int = value.coerceIn(LOCK_MIN_SECONDS, LOCK_MAX_SECONDS)
    fun blockMinutes(value: Int): Int = value.coerceIn(BLOCK_MIN_MINUTES, BLOCK_MAX_MINUTES)
    fun snoozeMinutes(value: Int): Int = value.coerceIn(SNOOZE_MIN_MINUTES, SNOOZE_MAX_MINUTES)
    fun themeMode(value: Int): Int = value.coerceIn(THEME_MIN, THEME_MAX)
    fun fraction(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
}

/**
 * First-run defaults, kept beside the [SettingsRanges] that bound them so both unit-test
 * directly. [SettingsStore] reads these as its SharedPreferences fallbacks.
 */
object SettingsDefaults {
    const val SHOW_COUNTDOWN = false
    const val BREATHING_ENABLED = true
    const val INHALE_SECONDS = 4
    const val HOLD_SECONDS = 7
    const val EXHALE_SECONDS = 8
    const val LOCK_SECONDS = 30
    const val SNOOZE_MINUTES = 5
    const val BLOCK_MINUTES = 30
}

/**
 * Bubble geometry as fractions of the screen's shorter side. [sizeFraction] sizes window
 * and glyph; [edgeFraction] is the snapped margin, moving it inward without resizing.
 */
data class BubbleMetrics(val sizeFraction: Float, val edgeFraction: Float)

/**
 * Presets aligning the bubble with an app's action rail, calibrated to the like/comment
 * icons in 1080x2340 screenshots. 0 = Instagram (default), 1 = TikTok, 2 = Shorts,
 * 3 = Custom. Tune via the in-app preview.
 */
object BubblePresets {
    const val INSTAGRAM = 0
    const val TIKTOK = 1
    const val SHORTS = 2
    const val CUSTOM = 3

    const val SIZE_MIN = 0.10f
    const val SIZE_MAX = 0.22f
    const val EDGE_MIN = 0.0f
    const val EDGE_MAX = 0.060f

    // Calibrated to each app's action-rail icon on a 1080px / 360dp screen (fraction = dp / 360):
    // Instagram 44dp/8dp, TikTok 48dp/7dp, Shorts 40dp/10dp.
    private val TABLE = mapOf(
        INSTAGRAM to BubbleMetrics(0.122f, 0.022f),
        TIKTOK to BubbleMetrics(0.134f, 0.019f),
        SHORTS to BubbleMetrics(0.111f, 0.029f),
    )

    /** Default custom values start where the Instagram preset sits. */
    val DEFAULT_CUSTOM: BubbleMetrics = TABLE.getValue(INSTAGRAM)

    /** Resolves metrics for [preset], using [customSize]/[customEdge] (clamped) when Custom. */
    fun metrics(preset: Int, customSize: Float, customEdge: Float): BubbleMetrics = when (preset) {
        CUSTOM -> BubbleMetrics(
            customSize.coerceIn(SIZE_MIN, SIZE_MAX),
            customEdge.coerceIn(EDGE_MIN, EDGE_MAX)
        )
        else -> TABLE[preset] ?: TABLE.getValue(INSTAGRAM)
    }
}

/**
 * Buckets a remaining duration into the largest whole unit that fits, rounding *up* so a
 * countdown never claims less time than is left.
 *
 * Pure because two surfaces render it and must never disagree: the bubble countdown and
 * the promoted notification's status-bar chip, once separate copies of this arithmetic.
 * Returns the scale, not a string, so the caller picks the localized unit — Finnish
 * abbreviates hours as "t", not "h".
 */
object CompactDuration {
    enum class Scale { HOURS, MINUTES, SECONDS }

    data class Parts(val scale: Scale, val value: Int)

    fun of(totalSeconds: Int): Parts {
        val s = totalSeconds.coerceAtLeast(0)
        return when {
            s >= 3600 -> Parts(Scale.HOURS, (s + 3599) / 3600)
            s >= 60 -> Parts(Scale.MINUTES, (s + 59) / 60)
            else -> Parts(Scale.SECONDS, s)
        }
    }
}

/**
 * Decides whether an `ACTION_CLOSE_SYSTEM_DIALOGS` broadcast means the user left for
 * another screen (HOME, recent apps) rather than the other things it fires for — the
 * notification shade, the power menu, the assistant — where the wind-down should stay.
 *
 * `"reason"` is the de facto extra key for this, not a public SDK constant. A missing
 * extra is treated as "don't close": staying up is the safer failure.
 */
object CloseSystemDialogs {
    fun closesOverlayForReason(reason: String?): Boolean =
        reason == "homekey" || reason == "recentapps"
}

/** A persisted read of [PauseState] at one instant — the timer and break deadlines on disk. */
data class PauseSnapshot(
    val timerStartMillis: Long,
    val timerEndMillis: Long,
    val breakUntilMillis: Long
)

/**
 * Decides what a just-(re)started [OverlayService] should do with whatever [PauseState] has on
 * disk. Exists because a null `Intent` in `onStartCommand` is ambiguous on its own -- it means
 * "Android restarted us after a kill", and the three outcomes below are the only sane responses:
 * resume a timer that's still ahead of it, catch one up if its deadline passed while the process
 * was dead, or do nothing if there wasn't one. Kept pure so the decision is unit-tested without
 * a Service.
 */
object SessionRestore {
    sealed interface Decision {
        /** No timer was persisted, or it was already cleared. */
        data object Idle : Decision

        /** The persisted deadline is still ahead; resume ticking toward it. */
        data class ResumeTimer(val startMillis: Long, val endMillis: Long) : Decision

        /** The persisted deadline already passed while the process was dead; catch up now. */
        data object TimerExpiredWhileDead : Decision
    }

    fun decide(persistedStart: Long, persistedEnd: Long, now: Long): Decision = when {
        persistedEnd <= 0L -> Decision.Idle
        persistedEnd > now -> Decision.ResumeTimer(persistedStart, persistedEnd)
        else -> Decision.TimerExpiredWhileDead
    }

    /** Whether a persisted break deadline is still ahead, so its cover should resume. */
    fun breakStillActive(persistedBreakUntil: Long, now: Long): Boolean =
        persistedBreakUntil > now
}

/**
 * Gates the "Stop for now" break's per-second foreground-app poll on screen state. The
 * foreground app cannot change while the screen is off, so querying it then is wasted battery
 * for no correctness benefit -- skipping it is free, not a trade-off.
 */
object BreakPolling {
    fun shouldQueryForeground(screenOn: Boolean): Boolean = screenOn
}
