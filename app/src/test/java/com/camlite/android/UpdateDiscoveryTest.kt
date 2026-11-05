package com.camlite.android

import com.camlite.android.updates.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.net.URL
import java.io.File
import java.io.ByteArrayInputStream
import java.security.cert.Certificate
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

class UpdateDiscoveryTest {
    private class Connection(url: String, val status: Int, val location: String? = null) : HttpsURLConnection(URL(url)) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getResponseCode() = status
        override fun getHeaderField(name: String): String? = if(name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream("fresh".toByteArray())
    }
    @Test fun refreshBypassesCachesOnEveryRedirectAndUsesDistinctQuery() {
        val a=UpdateSource.metadataUrls(); val b=UpdateSource.metadataUrls()
        assertNotEquals(a.first,b.first)
        assertEquals(URL(a.first).query,URL(a.second).query)
        assertEquals("/${UpdateSource.REPOSITORY}/releases/latest/download/version.json.sig",URL(a.second).path)
        val opened=mutableListOf<Connection>()
        val transport=UpdateTransport { url -> Connection(url,if(opened.isEmpty()) 302 else 200,
            "https://release-assets.githubusercontent.com/file").also { opened.add(it) } }
        assertEquals("fresh",String(transport.read(a.first,100)))
        assertEquals(2,opened.size)
        opened.forEach { assertFalse(it.useCaches); assertEquals("no-cache, no-store, max-age=0",it.getRequestProperty("Cache-Control")) }
    }
    @Test fun failureOrMissingResultNeverClaimsUpToDate() {
        assertTrue(UpdateCheckPresentation.summary(false,true,null,6).contains("Não foi possível confirmar"))
        assertTrue(UpdateCheckPresentation.summary(false,false,null,6).contains("Ainda não verificada"))
        assertTrue(UpdateCheckPresentation.summary(true,false,null,6).contains("Consultando"))
    }
    @Test fun realPublishedSignatureDetectsUpdateForPreviousInstalledVersion() {
        val directory=System.getenv("CAMLITE_PUBLIC_TEST_DIR")
        assumeTrue(directory!=null)
        val folder=File(directory!!)
        val protocol=UpdateProtocol(UpdateProtocol.publicKey(File(folder,"public.pem").readText()))
        val release=protocol.verify(File(folder,"version.json").readBytes(),File(folder,"version.json.sig").readBytes(),
            Instant.now(),35,setOf("arm64-v8a"),5)
        assertTrue(release.code>5)
        assertTrue(UpdateCheckPresentation.summary(false,false,release,5).contains(release.version))
        assertTrue(UpdateCheckPresentation.summary(false,false,release,release.code).contains("já usa"))
        assertFalse(UpdateCheckPresentation.summary(false,true,release,5).contains("já usa"))
    }
    @Test fun liveGithubDiscoveryUsesProductionTransportAndPublicKey() {
        val directory=System.getenv("CAMLITE_PUBLIC_TEST_DIR")
        assumeTrue(directory!=null)
        val urls=UpdateSource.metadataUrls()
        val transport=UpdateTransport()
        val protocol=UpdateProtocol(UpdateProtocol.publicKey(File(directory!!,"public.pem").readText()))
        val release=protocol.verify(transport.read(urls.first,65536),transport.read(urls.second,4096),
            Instant.now(),35,setOf("armeabi-v7a"),5)
        assertTrue("Public release must be newer than 1.4.0/code5",release.code>5)
    }
}
