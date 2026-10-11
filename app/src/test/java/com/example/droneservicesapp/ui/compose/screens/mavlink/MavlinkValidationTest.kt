package com.example.droneservicesapp.ui.compose.screens.mavlink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MavlinkValidationTest {
    @Test
    fun ipv4ValidationAcceptsOnlyFourDecimalOctets() {
        assertTrue(isValidIpv4("192.168.4.1"))
        assertTrue(isValidIpv4("0.0.0.0"))
        assertTrue(isValidIpv4("255.255.255.255"))
        assertFalse(isValidIpv4("192.168.4"))
        assertFalse(isValidIpv4("192.168.4.256"))
        assertFalse(isValidIpv4("192.168.04.one"))
        assertFalse(isValidIpv4(" 192.168.4.1 "))
    }
}
