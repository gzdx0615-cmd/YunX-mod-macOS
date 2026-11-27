package com.yunx.app.data.security

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class MacCredentialCompatibilityTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun requireMac() = assumeTrue(System.getProperty("os.name").lowercase().contains("mac"))

    @Test fun readsOldMacNormalKeyAndCanRewrapWithStableHardwareIdentity() {
        requireMac()
        val original = ByteArray(32) { it.toByte() }
        val salt = ByteArray(16) { (it + 8).toByte() }
        val iv = ByteArray(12) { (it + 4).toByte() }
        val fingerprint = "yunx-desktop|${System.getProperty("os.name")}|${System.getProperty("os.arch")}|${System.getenv("COMPUTERNAME").orEmpty()}-${System.getProperty("user.name")}"
        val wrappingKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(fingerprint.toCharArray(), salt, 210_000, 256)).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(128, iv))
        val b64 = Base64.getEncoder()
        val file = File(tmp.root, "legacy.key")
        file.writeText("yunx:key:mk:v1:${b64.encodeToString(salt)}:${b64.encodeToString(iv)}:${b64.encodeToString(cipher.doFinal(original))}")
        assertArrayEquals(original, DbKeyStore.loadOrCreate(file, DbEncryptionLevel.NORMAL))
        DbKeyStore.switchLevel(file, DbEncryptionLevel.NORMAL, DbEncryptionLevel.NORMAL)
        assertTrue(file.readText().startsWith("yunx:key:mk:mac:v2:"))
        assertArrayEquals(original, DbKeyStore.loadOrCreate(file, DbEncryptionLevel.NORMAL))
    }

    @Test fun keychainRoundTripPreservesCredentialKey() {
        requireMac()
        val file = File(tmp.root, "credential.key")
        val original = DbKeyStore.loadOrCreate(file, DbEncryptionLevel.LOOSE)
        var reference: String? = null
        try {
            DbKeyStore.switchLevel(file, DbEncryptionLevel.LOOSE, DbEncryptionLevel.STRICT)
            assertTrue(file.readText().startsWith("yunx:key:keychain:v1:"))
            reference = file.readText().removePrefix("yunx:key:keychain:v1:")
            assertArrayEquals(original, DbKeyStore.loadOrCreate(file, DbEncryptionLevel.STRICT))
            DbKeyStore.switchLevel(file, DbEncryptionLevel.STRICT, DbEncryptionLevel.LOOSE)
            assertArrayEquals(original, file.readBytes())
        } finally {
            reference?.let { ProcessBuilder("/usr/bin/security", "delete-generic-password", "-s", "YunX-OS.database-key", "-a", it).start().waitFor() }
        }
    }
}
