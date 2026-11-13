package com.anitail.innertube

import com.metrolist.innertubex.InnerTube as InnerTubeX
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class InnerTubeXExtractionTest {
    @Test
    fun testCipherAndExtractorInitialization() = runBlocking {
        val client = HttpClient(OkHttp)
        val innerTubeX = InnerTubeX(client)
        val logger = InnerTubeLogger { event -> println(event.message) }
        val configRepo = object : PlayerConfigRepository {
            override val enabled = false
            override val sourceUrl = ""
            override val defaultSourceUrl = ""
            override var cachedJson = ""
            override var cachedAtMs = 0L
            override var cachedSourceUrl = ""
            override var cachedEtag = ""
        }
        val remoteStore = RemotePlayerConfigStore(client, configRepo, logger)
        val cipherService = YouTubeCipherService(client, remoteStore, logger)
        val parser = YtConfigParserImpl(client, innerTubeX, remoteStore, logger)
        val extractor = InnerTubeExtractor(
            configParser = parser,
            cipherService = cipherService,
            innerTube = innerTubeX,
            tokenProvider = null,
            logger = logger,
        )
        assertNotNull(extractor)
        cipherService.dispose()
        client.close()
    }
}

