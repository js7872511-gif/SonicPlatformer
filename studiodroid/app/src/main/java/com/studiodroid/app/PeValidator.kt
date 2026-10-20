package com.studiodroid.app

object PeValidator {
    fun isPeExecutable(header: ByteArray): Boolean =
        header.size >= 2 && header[0] == 0x4D.toByte() && header[1] == 0x5A.toByte()
}
