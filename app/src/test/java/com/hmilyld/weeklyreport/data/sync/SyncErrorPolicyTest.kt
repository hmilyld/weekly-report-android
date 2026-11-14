package com.hmilyld.weeklyreport.data.sync

import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncErrorPolicyTest {
    @Test
    fun `401 and decryption stop for login`() {
        assertEquals(
            SyncErrorPolicy.Action.NEEDS_LOGIN,
            SyncErrorPolicy.action(ApiException(401, "expired", ApiErrorKind.UNAUTHORIZED)),
        )
        assertEquals(
            SyncErrorPolicy.Action.NEEDS_LOGIN,
            SyncErrorPolicy.action(ApiException(200, "lost key", ApiErrorKind.DECRYPTION)),
        )
    }

    @Test
    fun `network and server errors retry later`() {
        assertEquals(
            SyncErrorPolicy.Action.RETRY_LATER,
            SyncErrorPolicy.action(ApiException(null, "offline", ApiErrorKind.NETWORK)),
        )
        assertEquals(
            SyncErrorPolicy.Action.RETRY_LATER,
            SyncErrorPolicy.action(ApiException(503, "down", ApiErrorKind.HTTP)),
        )
        assertEquals(
            SyncErrorPolicy.Action.RETRY_LATER,
            SyncErrorPolicy.action(ApiException(429, "slow down", ApiErrorKind.HTTP)),
        )
    }

    @Test
    fun `permanent request errors fail only that item`() {
        assertEquals(
            SyncErrorPolicy.Action.FAIL_ITEM,
            SyncErrorPolicy.action(ApiException(400, "deadline in past", ApiErrorKind.HTTP)),
        )
        assertEquals(
            SyncErrorPolicy.Action.FAIL_ITEM,
            SyncErrorPolicy.action(ApiException(422, "invalid", ApiErrorKind.HTTP)),
        )
        assertEquals(
            SyncErrorPolicy.Action.FAIL_ITEM,
            SyncErrorPolicy.action(ApiException(null, "bad json", ApiErrorKind.PARSE)),
        )
    }
}
