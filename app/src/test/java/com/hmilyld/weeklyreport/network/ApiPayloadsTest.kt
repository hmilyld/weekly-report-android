package com.hmilyld.weeklyreport.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiPayloadsTest {
    @Test
    fun `string detail is surfaced`() {
        assertEquals(
            "当前密码不正确",
            ApiPayloads.errorMessage("""{"detail":"当前密码不正确"}""", 402),
        )
    }

    @Test
    fun `validation detail list is joined`() {
        val body =
            """{"detail":[{"loc":["body","new_password"],"msg":"too short"},""" +
                """{"msg":"need a digit"}]}"""
        assertEquals("too short；need a digit", ApiPayloads.errorMessage(body, 422))
    }

    @Test
    fun `fallback messages are localized`() {
        assertEquals("登录状态已失效，请重新登录", ApiPayloads.errorMessage("", 401))
        assertEquals("尝试次数过多，请稍后再试", ApiPayloads.errorMessage(null, 429))
        assertTrue(ApiPayloads.errorMessage(null, 502).contains("502"))
    }

    @Test
    fun `decryption marker is detected at any depth`() {
        assertTrue(ApiPayloads.decryptionFailed("""{"reports":[{"_decryption_failed":true}]}"""))
        assertTrue(ApiPayloads.decryptionFailed("""{"_decryption_failed": true}"""))
        assertFalse(ApiPayloads.decryptionFailed("""{"reports":[{"_decryption_failed":false}]}"""))
        assertFalse(ApiPayloads.decryptionFailed("""{"reports":[]}"""))
    }

    @Test
    fun `api exception flags`() {
        val wrong = ApiException(402, "当前密码不正确")
        assertTrue(wrong.isWrongPassword)
        assertFalse(wrong.requiresLogin)

        val expired = ApiException(401, "expired", ApiErrorKind.UNAUTHORIZED)
        assertTrue(expired.requiresLogin)

        // Decryption is a server-side key problem; the token is still valid.
        val decrypt = ApiException(200, "lost key", ApiErrorKind.DECRYPTION)
        assertFalse(decrypt.requiresLogin)
    }

    @Test
    fun `409 encryption codes are classified`() {
        val keyMissing = """{"code":"encryption_key_missing","detail":"请先在网页端登录"}"""
        assertEquals("encryption_key_missing", ApiPayloads.errorCode(keyMissing))
        assertEquals(
            ApiErrorKind.ENCRYPTION_KEY_MISSING,
            ApiPayloads.errorKind(409, keyMissing),
        )

        val decryptFailed = """{"code":"decryption_failed","detail":"内容无法解密"}"""
        assertEquals("decryption_failed", ApiPayloads.errorCode(decryptFailed))
        assertEquals(ApiErrorKind.DECRYPTION, ApiPayloads.errorKind(409, decryptFailed))

        assertEquals(ApiErrorKind.HTTP, ApiPayloads.errorKind(409, """{"detail":"conflict"}"""))
        assertEquals(ApiErrorKind.UNAUTHORIZED, ApiPayloads.errorKind(401, ""))
    }

    @Test
    fun `decryption message prefers the server detail`() {
        assertEquals(
            "内容无法解密，请检查密钥",
            ApiPayloads.decryptionMessage("""{"detail":"内容无法解密，请检查密钥"}"""),
        )
        assertTrue(ApiPayloads.decryptionMessage(null).contains("无法解密"))
    }
}
