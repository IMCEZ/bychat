package com.bychat.app

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Security {
    fun newSalt(): String = Base64.encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)

    fun passwordHash(password: CharArray, salt: String): String {
        val spec = PBEKeySpec(password, Base64.decode(salt, Base64.NO_WRAP), 120_000, 256)
        return try {
            Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }

    fun equals(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
