package com.studiodroid.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeValidatorTest {
    @Test fun acceptsMzHeader() = assertTrue(PeValidator.isPeExecutable(byteArrayOf(0x4D, 0x5A, 0x00)))
    @Test fun rejectsShortInput() = assertFalse(PeValidator.isPeExecutable(byteArrayOf(0x4D)))
    @Test fun rejectsWrongSignature() = assertFalse(PeValidator.isPeExecutable(byteArrayOf(0x50, 0x4B)))
}
