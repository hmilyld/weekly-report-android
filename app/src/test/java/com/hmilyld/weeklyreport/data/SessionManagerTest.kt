package com.hmilyld.weeklyreport.data

import com.hmilyld.weeklyreport.data.model.ApiTokenCreatedDto
import com.hmilyld.weeklyreport.data.model.TokenResponse
import com.hmilyld.weeklyreport.network.ApiCredential
import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException
import com.hmilyld.weeklyreport.network.AuthKind
import com.hmilyld.weeklyreport.network.SessionApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private val settings = FakeSettings()
    private val store = FakeTokenStore()
    private val api = FakeApi()
    private val session = SessionManager(settings, store, api)

    @Test
    fun `login stores a long lived token`() =
        runTest {
            settings.tokenId = 99
            settings.tokenServer = settings.server

            session.login("demo", "password")

            assertTrue(session.state.value is SessionState.LoggedIn)
            assertEquals(AuthKind.API_TOKEN, store.kind)
            assertEquals("api-token-1", store.token)
            assertEquals(ApiCredential("api-token-1", AuthKind.API_TOKEN), api.credential)
            assertEquals(1, settings.tokenId)
            assertEquals(settings.server, settings.tokenServer)
            assertEquals("demo", settings.lastUsername.value)
            assertTrue(api.deleted.contains(Triple("jwt-1", 99, settings.server)))
        }

    @Test
    fun `login falls back to jwt when token creation fails`() =
        runTest {
            api.failCreate = true

            session.login("demo", "password")

            assertTrue(session.state.value is SessionState.LoggedIn)
            assertEquals(AuthKind.JWT, store.kind)
            assertEquals("jwt-1", store.token)
            assertEquals(ApiCredential("jwt-1", AuthKind.JWT), api.credential)
        }

    @Test
    fun `upgrade exchanges a legacy jwt`() =
        runTest {
            store.token = "legacy-jwt"
            store.kind = AuthKind.JWT
            val session = SessionManager(settings, store, api)

            session.upgradeLegacyJwt()

            assertEquals(AuthKind.API_TOKEN, store.kind)
            assertEquals("api-token-1", store.token)
            assertEquals(ApiCredential("api-token-1", AuthKind.API_TOKEN), api.credential)
            assertTrue(session.state.value is SessionState.LoggedIn)
        }

    @Test
    fun `logout during upgrade discards the new token`() =
        runTest {
            store.token = "legacy-jwt"
            store.kind = AuthKind.JWT
            val session = SessionManager(settings, store, api)
            val gate = CompletableDeferred<Unit>()
            api.createGate = gate
            val job = launch { session.upgradeLegacyJwt() }
            runCurrent()

            session.logout()
            gate.complete(Unit)
            job.join()

            assertTrue(session.state.value is SessionState.LoggedOut)
            assertNull(store.token)
            assertNull(api.credential)
            assertTrue(api.deleted.contains(Triple("legacy-jwt", 1, settings.server)))
        }

    @Test
    fun `server change during upgrade discards and keeps the old server for delete`() =
        runTest {
            store.token = "legacy-jwt"
            store.kind = AuthKind.JWT
            val session = SessionManager(settings, store, api)
            val oldServer = settings.server
            val gate = CompletableDeferred<Unit>()
            api.createGate = gate
            val job = launch { session.upgradeLegacyJwt() }
            runCurrent()

            settings.server = "https://new.example/api/v1/"
            gate.complete(Unit)
            job.join()

            assertEquals(AuthKind.JWT, store.kind)
            assertEquals("legacy-jwt", store.token)
            assertEquals(ApiCredential("legacy-jwt", AuthKind.JWT), api.credential)
            assertEquals(oldServer, api.createdServers.single())
            assertTrue(api.deleted.contains(Triple("legacy-jwt", 1, oldServer)))
            assertFalse(api.deleted.any { it.third == settings.server })
        }

    @Test
    fun `change password keeps the credential in sync`() =
        runTest {
            store.token = "legacy-jwt"
            store.kind = AuthKind.JWT
            val session = SessionManager(settings, store, api)

            session.changePassword("old", "new")

            assertEquals(AuthKind.JWT, store.kind)
            assertEquals("jwt-2", store.token)
            assertEquals(ApiCredential("jwt-2", AuthKind.JWT), api.credential)
        }

    @Test
    fun `lock clears the credential and unlock restores it`() =
        runTest {
            session.login("demo", "password")

            session.lock()
            assertNull(api.credential)
            assertTrue(session.state.value is SessionState.Locked)

            session.unlock()
            assertEquals(ApiCredential("api-token-1", AuthKind.API_TOKEN), api.credential)
            assertTrue(session.state.value is SessionState.LoggedIn)
        }

    @Test
    fun `auth problem clears the session`() =
        runTest {
            session.login("demo", "password")

            api.problemListener(ApiCredential("api-token-1", AuthKind.API_TOKEN))

            assertTrue(session.state.value is SessionState.LoggedOut)
            assertNull(store.token)
            assertNull(api.credential)
            assertNotNull(settings.problem)
        }

    @Test
    fun `stale 401 does not clear a newer credential`() =
        runTest {
            store.token = "legacy-jwt"
            store.kind = AuthKind.JWT
            val session = SessionManager(settings, store, api)
            session.upgradeLegacyJwt()

            api.problemListener(ApiCredential("legacy-jwt", AuthKind.JWT))

            assertTrue(session.state.value is SessionState.LoggedIn)
            assertEquals(ApiCredential("api-token-1", AuthKind.API_TOKEN), api.credential)
            assertNull(settings.problem)
        }

    @Test
    fun `401 while logged out is ignored`() =
        runTest {
            api.problemListener(ApiCredential("old-token", AuthKind.JWT))

            assertTrue(session.state.value is SessionState.LoggedOut)
            assertNull(settings.problem)
        }
}

private class FakeSettings : SessionSettings {
    override val lastUsername = MutableStateFlow("")
    override val biometricEnabled = MutableStateFlow(false)
    var server = "https://example.test/api/v1/"
    var problem: String? = null
    var tokenId: Int? = null
    var tokenServer: String? = null

    override fun serverBaseValue(): String = server

    override fun setLastUsername(username: String) {
        lastUsername.value = username
    }

    override fun lastLoginProblem(): String = problem.orEmpty()

    override fun setLoginProblem(message: String?) {
        problem = message
    }

    override fun apiTokenId(): Int? = tokenId

    override fun setApiTokenId(id: Int?) {
        tokenId = id
    }

    override fun apiTokenServer(): String? = tokenServer

    override fun setApiTokenServer(server: String?) {
        tokenServer = server
    }
}

private class FakeTokenStore : SessionTokenStore {
    var token: String? = null
    var kind: AuthKind = AuthKind.JWT

    override fun readToken(): String? = token

    override fun readKind(): AuthKind = kind

    override fun saveToken(
        token: String,
        kind: AuthKind,
    ) {
        this.token = token
        this.kind = kind
    }

    override fun clear() {
        token = null
        kind = AuthKind.JWT
    }
}

private class FakeApi : SessionApi {
    override var credential: ApiCredential? = null
    override var problemListener: (ApiCredential?) -> Unit = {}

    var jwt = "jwt-1"
    var token = "api-token-1"
    var tokenId = 1
    var failCreate = false
    var createGate: CompletableDeferred<Unit>? = null
    val createdServers = mutableListOf<String>()
    val deleted = mutableListOf<Triple<String, Int, String>>()

    override suspend fun login(
        username: String,
        password: String,
    ): TokenResponse = TokenResponse(jwt)

    override suspend fun createApiToken(
        jwt: String,
        name: String,
        baseUrl: String,
    ): ApiTokenCreatedDto {
        createdServers += baseUrl
        createGate?.await()
        if (failCreate) throw ApiException(500, "fail", ApiErrorKind.HTTP)
        return ApiTokenCreatedDto(tokenId, name, token)
    }

    override suspend fun deleteApiToken(
        jwt: String,
        id: Int,
        baseUrl: String,
    ) {
        deleted += Triple(jwt, id, baseUrl)
    }

    override suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
    ): TokenResponse = TokenResponse("jwt-2")

    override suspend fun logout() = Unit
}
