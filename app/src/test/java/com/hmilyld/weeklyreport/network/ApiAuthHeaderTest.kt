package com.hmilyld.weeklyreport.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiAuthHeaderTest {
    @Test
    fun `jwt uses authorization`() {
        assertEquals(
            "Authorization" to "Bearer jwt",
            authHeaderFor(ApiCredential("jwt", AuthKind.JWT)),
        )
    }

    @Test
    fun `api token uses x-api-token`() {
        assertEquals(
            "X-API-Token" to "api-token",
            authHeaderFor(ApiCredential("api-token", AuthKind.API_TOKEN)),
        )
    }

    @Test
    fun `no credential has no header`() {
        assertNull(authHeaderFor(null))
    }
}
