package com.yunx.app.data.security

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.IOException
import java.util.UUID

/** Stores credential keys through Security.framework; no secret is put in a command line or log. */
internal object MacKeychain {
    private interface Security : Library {
        fun SecKeychainAddGenericPassword(keychain: Pointer?, serviceLength: Int, service: ByteArray,
            accountLength: Int, account: ByteArray, passwordLength: Int, password: ByteArray,
            item: PointerByReference?): Int
        fun SecKeychainFindGenericPassword(keychain: Pointer?, serviceLength: Int, service: ByteArray,
            accountLength: Int, account: ByteArray, length: IntByReference,
            password: PointerByReference, item: PointerByReference?): Int
        fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer?): Int
    }
    private val api by lazy {
        try { Native.load("/System/Library/Frameworks/Security.framework/Security", Security::class.java) }
        catch (e: LinkageError) { throw IOException("无法加载 macOS 钥匙串接口，原密钥保持不变", e) }
    }
    private val service = "YunX-OS.database-key".toByteArray(Charsets.UTF_8)

    fun store(key: ByteArray): String {
        val id = UUID.randomUUID().toString()
        val account = id.toByteArray(Charsets.UTF_8)
        val status = api.SecKeychainAddGenericPassword(null, service.size, service, account.size, account,
            key.size, key, null)
        if (status != 0) throw IOException("钥匙串写入失败（$status），原密钥保持不变")
        return id
    }
    fun load(id: String): ByteArray {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) { "钥匙串引用格式无效" }
        val account = id.toByteArray(Charsets.UTF_8)
        val length = IntByReference()
        val data = PointerByReference()
        val status = api.SecKeychainFindGenericPassword(null, service.size, service, account.size, account,
            length, data, null)
        if (status != 0) throw IOException("钥匙串不可用（$status），请解锁钥匙串后重试；不会更换密钥")
        return try {
            if (length.value != 32) throw IOException("钥匙串密钥长度无效")
            data.value.getByteArray(0, length.value)
        } finally { api.SecKeychainItemFreeContent(null, data.value) }
    }
}
