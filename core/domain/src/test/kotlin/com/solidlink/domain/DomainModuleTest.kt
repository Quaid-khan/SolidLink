package com.solidlink.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainModuleTest {
    @Test
    fun domainModuleIsPureKotlin() {
        assertEquals("SolidLink domain", DomainModule.name)
    }
}
