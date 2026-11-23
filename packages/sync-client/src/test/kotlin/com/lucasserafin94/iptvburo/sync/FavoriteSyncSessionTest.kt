package com.lucasserafin94.iptvburo.sync

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteSyncSessionTest {
    private object FakeProver : SyncProver {
        override val deviceId: String = "ABCD-EFGH-JKLM"

        override fun proof(nonce: String): String = "signed-$nonce"
    }

    private class MemoryStore : SyncStateStore {
        override var isMember: Boolean = true
        private var ledger: String? = null

        override fun readLedger(): String? = ledger

        override fun writeLedger(encoded: String) {
            ledger = encoded
        }
    }

    private fun page(revision: Long) =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"ok":true,"revision":$revision,"hasMore":false,"records":[]}""")

    private fun pushedCount(server: MockWebServer): Int =
        JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject.getAsJsonArray("records").size()

    @Test
    fun `changes past a page that failed are pushed on the next exchange`() {
        val server = MockWebServer().apply { start() }
        try {
            val session = FavoriteSyncSession(SyncClient(OkHttpClient(), server.url("/").toString(), FakeProver), MemoryStore()) { 1_000L }
            val favourites =
                (1..250).map { n -> FavoriteSync.Local("Ana", "movie:title-$n:2020", "Title $n", 2020, "movie") }

            // The first 200 go through; the page with the other 50 fails.
            server.enqueue(page(1))
            server.enqueue(MockResponse().setResponseCode(500))
            assertTrue(session.exchange(favourites, listOf("Ana")) is SyncResult.Failure)
            assertEquals(200, pushedCount(server))
            assertEquals(50, pushedCount(server))

            // Next time, the 50 the server never accepted are offered again — and only those.
            server.enqueue(page(2))
            assertTrue(session.exchange(favourites, listOf("Ana")) is SyncResult.Success)
            assertEquals("changes past the failed page were lost", 50, pushedCount(server))
        } finally {
            server.shutdown()
        }
    }
}
