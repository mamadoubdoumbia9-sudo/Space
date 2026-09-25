package com.whalert.app.domain.validator

import org.junit.Assert.*
import org.junit.Test

class PhoneValidatorTest {

    @Test
    fun testValidFrenchNumber() {
        val res = PhoneValidator.validate("+33612345678")
        assertTrue(res.isValid)
        assertEquals("+33612345678", res.e164Format)
        assertEquals("FR", res.countryCode)
    }

    @Test
    fun testValidUSNumber() {
        val res = PhoneValidator.validate("+14155552671")
        assertTrue(res.isValid)
        assertEquals("+14155552671", res.e164Format)
        assertEquals("US/CA", res.countryCode)
    }

    @Test
    fun testValidIvoryCoastNumber() {
        val res = PhoneValidator.validate("+2250707070707")
        assertTrue(res.isValid)
        assertEquals("+2250707070707", res.e164Format)
        assertEquals("CI", res.countryCode)
    }

    @Test
    fun testFrenchNationalFormatWithLeadingZero() {
        val res = PhoneValidator.validate("0612345678", defaultRegionCode = "FR")
        assertTrue(res.isValid)
        assertEquals("+33612345678", res.e164Format)
    }

    @Test
    fun testNumberWithSpacesAndHyphens() {
        val res = PhoneValidator.validate("+33 6 12-34 56 78")
        assertTrue(res.isValid)
        assertEquals("+33612345678", res.e164Format)
    }

    @Test
    fun testEmptyNumberFails() {
        val res = PhoneValidator.validate("")
        assertFalse(res.isValid)
        assertNotNull(res.errorMessage)
    }

    @Test
    fun testTooShortNumberFails() {
        val res = PhoneValidator.validate("+123")
        assertFalse(res.isValid)
    }

    @Test
    fun testAlphabetCharsFails() {
        val res = PhoneValidator.validate("+33612ABC78")
        assertFalse(res.isValid)
    }

    @Test
    fun testMaskPhoneNumber() {
        val masked = PhoneValidator.maskPhoneNumber("+33612345678")
        assertEquals("+33 •••••• 678", masked)
    }
}
