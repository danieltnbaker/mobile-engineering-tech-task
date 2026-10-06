package com.userhub.data

import com.userhub.data.remote.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the P0-2 fix. The access token is now injected rather than hardcoded, and the client is
 * safe-by-default:
 *  - a provided token is sent as a Bearer header, so write endpoints still work when configured;
 *  - an absent token sends no Authorization header, so the app degrades to read-only instead of
 *    relying on a credential baked into source (the previous behaviour).
 *
 * The "no credential in source" guarantee itself is enforced by the CI secret scan (see
 * ENGINEERING_PLAN.md); these tests pin the client's runtime contract that the fix depends on.
 */
class HttpClientFactoryTest {

    @Test
    fun `an injected token is sent as a bearer authorization header`() = runTest {
        var seenAuth: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers["Authorization"]
            respond(content = "[]", status = HttpStatusCode.OK)
        }
        val client = createHttpClient(engine, authToken = "injected-token")

        client.get("https://example.test/")

        assertEquals("Bearer injected-token", seenAuth)
    }

    @Test
    fun `no authorization header is sent when the token is absent`() = runTest {
        var seenAuth: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers["Authorization"]
            respond(content = "[]", status = HttpStatusCode.OK)
        }
        val client = createHttpClient(engine, authToken = "")

        client.get("https://example.test/")

        assertNull(seenAuth)
    }
}
