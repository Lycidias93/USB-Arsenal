package com.usbarsenal.app.ui

import com.usbarsenal.app.XmlStrings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The reports match hid.c's descriptors, and typed text maps to the keys a US keyboard uses
class ReportsTest {
    // The strings these check are read from res/values, as the app reads them
    init { XmlStrings.install() }

    @Test
    fun mouse() {
        assertArrayEquals(byteArrayOf(1, 10, -2, 0, 0), mouseReport(MOUSE_LEFT, 10, -2))
        // A signed byte each: a long drag is cut to what one report can carry
        assertArrayEquals(byteArrayOf(0, 127, -127, 1, -1), mouseReport(0, 500, -500, 1, -1))
    }

    @Test
    fun keyboard() {
        assertArrayEquals(byteArrayOf(MOD_CTRL.toByte(), 0, 0x06, 0, 0, 0, 0, 0), keyReport(MOD_CTRL, 0x06))
        assertArrayEquals(ByteArray(8), keyReport())
    }

    @Test
    fun altGrReportsExposeModifierAndRelease() {
        val reports = keyStrokeReports(MOD_ALTGR, 0x14)
        assertEquals(4, reports.size)
        assertArrayEquals(keyReport(MOD_ALTGR), reports[0])
        assertArrayEquals(keyReport(MOD_ALTGR, 0x14), reports[1])
        assertArrayEquals(keyReport(MOD_ALTGR), reports[2])
        assertArrayEquals(keyReport(), reports[3])
        val simple = keyStrokeReports(0, 0x04)
        assertEquals(2, simple.size)
        assertArrayEquals(keyReport(0, 0x04), simple[0])
        assertArrayEquals(keyReport(), simple[1])
        val shifted = keyStrokeReports(MOD_SHIFT, 0x04)
        assertEquals(4, shifted.size)
        assertArrayEquals(keyReport(MOD_SHIFT), shifted[0])
        assertArrayEquals(keyReport(MOD_SHIFT, 0x04), shifted[1])
        assertArrayEquals(keyReport(MOD_SHIFT), shifted[2])
        assertArrayEquals(keyReport(), shifted[3])
        // Non-Shift shortcuts keep the original two-report behavior.
        val control = keyStrokeReports(MOD_CTRL, 0x06)
        assertEquals(2, control.size)
        assertArrayEquals(keyReport(MOD_CTRL, 0x06), control[0])
        assertArrayEquals(keyReport(), control[1])
    }


    @Test
    fun longMixedCaseTextHasReliableShiftEdges() {
        // Regression for case loss during long TYPE input; use only synthetic text.
        val text = "AbCd EfGh ÄäÖöÜü YyZz 123 !? @{}"
        val keys = text.mapNotNull { keyFor(it, german = true) }
        assertEquals(text.length, keys.size)
        var upper = 0
        var ordinary = 0
        keys.forEach { (mods, code) ->
            val stroke = keyStrokeReports(mods, code)
            if (mods and (MOD_SHIFT or MOD_ALTGR) != 0) {
                assertEquals(4, stroke.size)
                assertArrayEquals(keyReport(mods), stroke[0])
                assertArrayEquals(keyReport(mods, code), stroke[1])
                assertArrayEquals(keyReport(mods), stroke[2])
                assertArrayEquals(keyReport(), stroke[3])
                upper++
            } else {
                assertEquals(2, stroke.size)
                assertArrayEquals(keyReport(mods, code), stroke[0])
                assertArrayEquals(keyReport(), stroke[1])
                ordinary++
            }
        }
        org.junit.Assert.assertTrue(upper >= 10)
        org.junit.Assert.assertTrue(ordinary >= 10)
        // The longer Shift stroke still fits the human interval without reducing 12-ms dwell.
        assertEquals(74L, humanTailPaceMs(4, 12L, 110L))
    }

    @Test
    fun reportPacingPreservesWindowsMinimum() {
        assertEquals(12L, keyboardReportPaceMs(4, false))
        assertEquals(12L, keyboardReportPaceMs(4, true))
        assertEquals(12L, keyboardReportPaceMs(1, false))
        assertEquals(12L, keyboardReportPaceMs(1, true))
        assertEquals(32L, keyboardReportPaceMs(8, false))
        assertEquals(16L, keyboardReportPaceMs(8, true))
    }

    @Test
    fun fasterHumanSpeedIsBoundedAndVariable() {
        val random = kotlin.random.Random(42)
        val delays = (1..512).map { humanTypingIntervalMs(random) }
        org.junit.Assert.assertTrue(delays.all { it in 110L..170L })
        org.junit.Assert.assertTrue(delays.distinct().size > 1)
        org.junit.Assert.assertTrue(delays.all { humanTailPaceMs(4, 12L, it) >= 12L })
    }

    @Test
    fun humanFastIsVariableAndPreservesShiftTiming() {
        val random = kotlin.random.Random(24)
        val intervals = (1..512).map { humanTypingIntervalMs(random, fast = true) }
        org.junit.Assert.assertTrue(intervals.all { it in 65L..95L })
        org.junit.Assert.assertTrue(intervals.distinct().size > 1)
        // Four reports at a verified 12 ms, including Shift and AltGr, retain full dwell.
        org.junit.Assert.assertTrue(intervals.all { humanTailPaceMs(4, 12L, it) >= 12L })
        assertEquals(29L, humanTailPaceMs(4, 12L, 65L))
        assertEquals(32L, humanTailPaceMs(4, 32L, 65L))
    }

    @Test
    fun defaultsFollowGermanLocaleOnlyUntilExplicitlySelected() {
        assertEquals(true, defaultsToGerman("de"))
        assertEquals(true, defaultsToGerman("DE"))
        assertEquals(false, defaultsToGerman("en"))
        assertEquals(false, defaultsToGerman("fr"))
        assertEquals(HUMAN_FAST, defaultHumanMode(defaultsToGerman("de")))
        assertEquals(HUMAN_OFF, defaultHumanMode(defaultsToGerman("en")))
        // Values explicitly stored in SharedPreferences take precedence;
        // do not reset a user's saved US selection or HUMAN OFF on launch.
    }

    @Test
    fun humanModeCycleKeepsSafeAsFallback() {
        assertEquals(HUMAN_FAST, nextHumanMode(HUMAN_OFF))
        assertEquals(HUMAN_SAFE, nextHumanMode(HUMAN_FAST))
        assertEquals(HUMAN_OFF, nextHumanMode(HUMAN_SAFE))
        assertEquals(HUMAN_OFF, nextHumanMode(999))
    }

    @Test
    fun humanPacingTargetsFullStrokeNotIndividualReports() {
        // Regular character: 12 ms after key-down, 188 ms after all-up -> 200 ms.
        assertEquals(188L, humanTailPaceMs(2, 12L, 200L))
        // AltGr: three early reports at 12 ms; extra time after final all-up.
        assertEquals(164L, humanTailPaceMs(4, 12L, 200L))
        assertEquals(168L, humanTailPaceMs(2, 12L, 180L))
        assertEquals(214L, humanTailPaceMs(4, 12L, 250L))
        // A physically slower interval cannot be shortened by human mode.
        assertEquals(32L, humanTailPaceMs(4, 32L, 80L))
        assertEquals(12L, humanTailPaceMs(2, 12L, 0L))
    }

    @Test
    fun typing() {
        assertEquals(0 to 0x04, keyFor('a'))
        assertEquals(MOD_SHIFT to 0x1D, keyFor('Z'))
        assertEquals(0 to 0x27, keyFor('0'))
        assertEquals(0 to 0x1E, keyFor('1'))
        assertEquals(MOD_SHIFT to 0x1F, keyFor('@'))
        assertEquals(MOD_SHIFT to 0x27, keyFor(')'))
        assertEquals(0 to 0x38, keyFor('/'))
        assertEquals(MOD_SHIFT to 0x38, keyFor('?'))
        assertEquals(MOD_SHIFT to 0x34, keyFor('"'))
        assertEquals(0 to 0x28, keyFor('\n'))
        assertNull(keyFor('é'))
    }

    @Test
    fun germanQwertz() {
        assertEquals(0 to 0x1D, keyFor('y', true))
        assertEquals(0 to 0x1C, keyFor('z', true))
        assertEquals(MOD_SHIFT to 0x1D, keyFor('Y', true))
        assertEquals(0 to 0x34, keyFor('ä', true))
        assertEquals(MOD_SHIFT to 0x33, keyFor('Ö', true))
        assertEquals(0 to 0x2D, keyFor('ß', true))
        assertEquals(MOD_ALTGR to 0x08, keyFor('€', true))
        assertEquals(MOD_ALTGR to 0x14, keyFor('@', true))
        assertEquals(MOD_SHIFT to 0x24, keyFor('/', true))
        assertEquals(MOD_ALTGR to 0x2D, keyFor('\\', true))
        assertEquals(0 to 0x64, keyFor('<', true))
        assertNull(keyFor('é', true))
    }

    @Test
    fun namedKeys() {
        assertEquals(0x3A, NAMED_KEYS["F1"])
        assertEquals(0x45, NAMED_KEYS["F12"])
        assertEquals(0x52, NAMED_KEYS["UP"])
        // Every key the Keys tab shows has a code or is a modifier
        (TABLE_NAMES + (1..12).map { "F$it" } + listOf("UP", "DOWN", "LEFT", "RIGHT")).forEach {
            assert(it in NAMED_KEYS || it in MODIFIERS) { it }
        }
    }

    // ENTER twice is one wide key; the gaps beside the up arrow stay two, so it sits in the middle
    @Test
    fun keyRows() {
        assertEquals(listOf("ENTER" to 2f, "PGUP" to 1f, "PGDN" to 1f), runs(listOf("ENTER", "ENTER", "PGUP", "PGDN")))
        assertEquals(listOf("" to 1f, "UP" to 1f, "" to 1f), runs(listOf("", "UP", "")))
    }

    @Test
    fun interval() {
        assertEquals("125 us", periodText(1))
        assertEquals("1 ms", periodText(4))
        assertEquals("512 ms", periodText(13))
        assertEquals("4.096 s", periodText(16))
        // The setup's 0 is the kernel's default, which the steppers show as 4
        assertEquals(DEFAULT_INTERVAL, HidSetup.interval)
    }
}
