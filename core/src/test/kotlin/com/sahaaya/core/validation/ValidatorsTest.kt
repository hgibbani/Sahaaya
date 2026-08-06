package com.sahaaya.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `accepts a well formed email`() {
        assertNull(Validators.email("ibbani@example.com"))
    }

    @Test
    fun `rejects an email without a domain`() {
        assertNotNull(Validators.email("ibbani@"))
    }

    @Test
    fun `rejects a blank email`() {
        assertEquals("Email is required", Validators.email("   "))
    }

    @Test
    fun `accepts a password with letters and digits`() {
        assertNull(Validators.password("sahaaya2026"))
    }

    @Test
    fun `rejects a password that is too short`() {
        assertNotNull(Validators.password("abc123"))
    }

    @Test
    fun `rejects a password without a digit`() {
        assertEquals("Include at least one number", Validators.password("sahaayacare"))
    }

    @Test
    fun `rejects mismatched password confirmation`() {
        assertNotNull(Validators.confirmPassword("sahaaya2026", "sahaaya2027"))
    }

    @Test
    fun `accepts an indian mobile number with and without country code`() {
        assertNull(Validators.phoneNumber("9876543210"))
        assertNull(Validators.phoneNumber("+91 9876543210"))
    }

    @Test
    fun `rejects a mobile number starting with an invalid digit`() {
        assertNotNull(Validators.phoneNumber("1234567890"))
    }

    @Test
    fun `treats phone number as optional when not required`() {
        assertNull(Validators.phoneNumber("", required = false))
    }

    @Test
    fun `accepts a pairing code from the unambiguous alphabet`() {
        assertNull(Validators.pairingCode("K7M2PQ"))
    }

    @Test
    fun `rejects a pairing code containing ambiguous characters`() {
        // I, O, 0 and 1 are excluded because they are easily misread aloud.
        assertNotNull(Validators.pairingCode("KI0M2P"))
    }

    @Test
    fun `rejects a pairing code of the wrong length`() {
        assertNotNull(Validators.pairingCode("K7M2P"))
    }
}
