package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackProgressCoordinator
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackProgressStore
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.sync.DesktopSync
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import com.lucasserafin94.iptvburo.sync.SyncClient
import com.lucasserafin94.iptvburo.sync.SyncProver
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertTrue
import okhttp3.OkHttpClient

/**
 * A computer that already joined a household builds its app state without crashing.
 *
 * The init block started the sync loop before the properties the loop uses were declared, and
 * Kotlin initialises in textual order: every launch after joining threw while building the state.
 */
class SyncStartupTest {
    private object FakeProver : SyncProver {
        override val deviceId: String = "ABCD-EFGH-JKLM"

        override fun proof(nonce: String): String = "signed-$nonce"
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `a household member starts without crashing`() {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        val syncNode = node.node("sync").apply { putBoolean("member", true) }
        val temporary = Files.createTempDirectory("iptvburo-sync-startup")
        // Nothing listens there: the first exchange, five seconds in, fails quietly.
        val sync = DesktopSync(SyncClient(OkHttpClient(), "http://127.0.0.1:9/", FakeProver), syncNode)
        val repository = SessionXtreamRepository(XtreamClient(httpClient = OkHttpClient()))
        val state =
            DesktopAppState(
                localRepository = InMemoryCatalogRepository(),
                xtreamRepository = repository,
                rememberedXtreamStore = RememberedXtreamStore(temporary.resolve("remembered.dpapi")),
                userStore = DesktopUserStore(node),
                sync = sync,
                playbackProgressCoordinator =
                    DesktopPlaybackProgressCoordinator(DesktopPlaybackProgressStore(node.node("progress"))),
            )
        try {
            assertTrue(state.syncUi.member)
        } finally {
            state.dispose()
            repository.clear()
            node.removeNode()
            temporary.deleteRecursively()
        }
    }
}
