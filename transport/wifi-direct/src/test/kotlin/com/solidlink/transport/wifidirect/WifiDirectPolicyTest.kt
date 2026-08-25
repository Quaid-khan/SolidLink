package com.solidlink.transport.wifidirect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectPolicyTest {
    @Test
    fun directIsUsableOnlyWhenHardwareAndPermissionsArePresent() {
        assertTrue(WifiDirectAvailability(true, true).usable)
        assertFalse(WifiDirectAvailability(false, true).usable)
        assertFalse(WifiDirectAvailability(true, false).usable)
    }
}
