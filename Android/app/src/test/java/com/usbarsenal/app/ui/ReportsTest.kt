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
    fun altGrIsStagedBeforeAndAfterKey() {
        val seq = keyStrokeReports(MOD_ALTGR, 0x14)
        assertEquals(4, seq.size)
        assertArrayEquals(keyReport(MOD_ALTGR), seq[0])
        assertArrayEquals(keyReport(MOD_ALTGR, 0x14), seq[1])
        assertArrayEquals(keyReport(MOD_ALTGR), seq[2])
        assertArrayEquals(keyReport(), seq[3])
        val bracket = keyStrokeReports(MOD_ALTGR, 0x25)
        assertEquals(4, bracket.size)
        assertArrayEquals(keyReport(MOD_ALTGR, 0x25), bracket[1])
    }

    @Test
    fun unmodifiedAndShiftKeysKeepExistingCadence() {
        val simple = keyStrokeReports(0, 0x04)
        assertEquals(2, simple.size)
        assertArrayEquals(keyReport(0, 0x04), simple[0])
        assertArrayEquals(keyReport(), simple[1])
        val shift = keyStrokeReports(MOD_SHIFT, 0x04)
        assertEquals(2, shift.size)
        assertArrayEquals(keyReport(MOD_SHIFT, 0x04), shift[0])
        assertArrayEquals(keyReport(), shift[1])
    }

    @Test
    fun reportDwellCoversHighSpeedWindowsAndConfiguredIntervals() {
        // With the Pixel's high-speed gadget and automatic interval, the old
        // two-poll calculation produced ~2 ms. Require a conservative 12 ms.
        assertEquals(12L, keyboardReportPaceMs(4, false))
        assertEquals(12L, keyboardReportPaceMs(4, true))
        assertEquals(12L, keyboardReportPaceMs(1, false))
        assertEquals(12L, keyboardReportPaceMs(1, true))
        // Explicit slower intervals must not be shortened.
        assertEquals(32L, keyboardReportPaceMs(8, false))
        assertEquals(16L, keyboardReportPaceMs(8, true))
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
