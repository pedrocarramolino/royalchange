package com.royalchance.core.common.text

import kotlin.test.Test
import kotlin.test.assertEquals

class NumbersTest {

    @Test
    fun groupsThousandsWithDots() {
        assertEquals("0", formatGrouped(0))
        assertEquals("500", formatGrouped(500))
        assertEquals("1.700", formatGrouped(1_700))
        assertEquals("25.450", formatGrouped(25_450))
        assertEquals("100.000", formatGrouped(100_000))
        assertEquals("1.234.567", formatGrouped(1_234_567))
    }

    @Test
    fun negativeNumbersKeepTheirSign() {
        assertEquals("-500", formatGrouped(-500))
        assertEquals("-10.000", formatGrouped(-10_000))
        assertEquals("-9.223.372.036.854.775.808", formatGrouped(Long.MIN_VALUE))
    }
}
