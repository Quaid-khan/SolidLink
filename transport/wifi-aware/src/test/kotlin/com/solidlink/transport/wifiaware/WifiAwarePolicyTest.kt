package com.solidlink.transport.wifiaware

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiAwarePolicyTest {
    @Test
    fun awareIsUsableOnlyWhenAllGatesAreOpen() {
        assertTrue(WifiAwareAvailability(true, true, true).usable)
        assertFalse(WifiAwareAvailability(false, true, true).usable)
        assertFalse(WifiAwareAvailability(true, false, true).usable)
        assertFalse(WifiAwareAvailability(true, true, false).usable)
    }
}
