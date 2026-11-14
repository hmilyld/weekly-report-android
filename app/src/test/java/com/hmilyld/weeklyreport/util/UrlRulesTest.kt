package com.hmilyld.weeklyreport.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRulesTest {
    private fun ok(
        raw: String,
        allowCleartext: Boolean = false,
    ): String {
        val result = UrlRules.validateAndNormalize(raw, allowCleartext)
        assertTrue("expected success for $raw but was $result", result is UrlRules.Result.Ok)
        return (result as UrlRules.Result.Ok).baseUrl
    }

    private fun error(
        raw: String,
        allowCleartext: Boolean = false,
    ): String {
        val result = UrlRules.validateAndNormalize(raw, allowCleartext)
        assertTrue("expected error for $raw but was $result", result is UrlRules.Result.Error)
        return (result as UrlRules.Result.Error).message
    }

    @Test
    fun `https address is normalized to api v1`() {
        assertEquals("https://example.com:18001/api/v1/", ok("https://example.com:18001"))
        assertEquals("https://example.com/api/v1/", ok("https://example.com/"))
        assertEquals("https://example.com/api/v1/", ok("https://example.com/api/v1"))
    }

    @Test
    fun `release refuses cleartext http`() {
        assertTrue(error("http://192.168.1.5:18001").contains("HTTPS"))
        assertEquals(
            "http://192.168.1.5:18001/api/v1/",
            ok("192.168.1.5:18001", allowCleartext = true),
        )
    }

    @Test
    fun `scheme-less input defaults by build type`() {
        assertEquals("https://report.local/api/v1/", ok("report.local"))
        assertEquals("http://report.local/api/v1/", ok("report.local", allowCleartext = true))
    }

    @Test
    fun `subpath deployments keep their prefix`() {
        assertEquals("https://host/tools/report/api/v1/", ok("https://host/tools/report"))
    }

    @Test
    fun `query and fragment are rejected`() {
        assertTrue(error("https://host/?x=1").contains("查询"))
        assertTrue(error("https://host/#x").contains("查询"))
    }

    @Test
    fun `blank and unsupported schemes are rejected`() {
        assertEquals("请先填写服务器地址", error("   "))
        assertTrue(error("ftp://host").contains("http"))
    }

    @Test
    fun `display address strips api suffix`() {
        assertEquals("https://host:18001", UrlRules.displayAddress("https://host:18001/api/v1/"))
        assertEquals("https://host", UrlRules.displayAddress("https://host/api/v1"))
    }
}
