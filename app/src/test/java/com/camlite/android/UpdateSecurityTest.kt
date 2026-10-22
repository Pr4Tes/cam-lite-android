package com.camlite.android

import com.camlite.android.updates.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

class UpdateSecurityTest {
    companion object {
        private val keys=KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        private val now=Instant.parse("2026-10-21T12:00:00Z")
    }
    private fun json()=JSONObject().put("schema",1).put("app_id","com.camlite.android").put("version","1.5.0").put("version_code",6)
        .put("channel","stable").put("os","android").put("arch","universal").put("architectures",JSONArray(listOf("arm64-v8a","armeabi-v7a","x86","x86_64")))
        .put("package_type","android-apk").put("min_sdk",26).put("min_os_build",0).put("url","https://github.com/${UpdateSource.REPOSITORY}/releases/download/v1.5.0/CamLite-Android-v1.5.0.apk")
        .put("size",3).put("sha256",UpdateProtocol.hash(byteArrayOf(1,2,3))).put("published_at",now.minusSeconds(5).toString())
        .put("expires_at",now.plusSeconds(3600).toString()).put("notes","Notas fictícias; não publicar.").put("mandatory",false).put("key_id",UpdateSource.KEY_ID)
    private fun sign(raw:ByteArray)=Base64.getEncoder().encode(Signature.getInstance("SHA256withRSA").run { initSign(keys.private); update(raw); sign() })
    private fun verify(j:JSONObject,installed:Long=5):ReleaseManifest { val raw=j.toString().toByteArray(); return UpdateProtocol(keys.public).verify(raw,sign(raw),now,35,setOf("arm64-v8a"),installed) }
    private fun reject(action:()->Unit) { try { action(); fail("Conteúdo inseguro aceito") } catch (_: UpdateRejected) { } }
    @Test fun detectsNewEqualAndRejectsDowngradeAndExpiredMetadata() {
        assertEquals(6L,verify(json()).code)
        assertFalse(UpdatePolicy.shouldNotify(6,6,true,true,false,0,0))
        reject { verify(json(),7) }
        reject { verify(json().put("expires_at",now.minusSeconds(1).toString())) }
        reject { verify(json().put("app_id","outro.aplicativo")) }
        reject { verify(json().put("min_sdk",36)) }
        reject { verify(json().put("architectures",JSONArray(listOf("x86")))) }
    }
    @Test fun signatureVerifiedBeforeMalformedJsonAndTamperingRejected() {
        val raw=json().toString().toByteArray()
        val signature=sign(raw)
        raw[0]='!'.code.toByte()
        try { UpdateProtocol(keys.public).verify(raw,signature,now,35,setOf("arm64-v8a"),5); fail() }
        catch(e:UpdateRejected) { assertTrue(e.message!!.contains("Assinatura")) }
        reject { UpdateProtocol(keys.public).verify(byteArrayOf(123),byteArrayOf(1),now,35,setOf("arm64-v8a"),5) }
    }
    @Test fun automaticRateLimitSurvivesFailureAndManualIsIndependentOfGate() {
        assertTrue(UpdatePolicy.automaticDue(true,1000,1000,1,0,0,-1))
        assertFalse(UpdatePolicy.automaticDue(true,2000,2000,1,1000,1000,1))
        assertFalse(UpdatePolicy.automaticDue(true,1000000000,2000,1,1000,1000,1)) // wall clock jumped ahead
        assertTrue(UpdatePolicy.automaticDue(true,86401000,86401000,1,1000,1000,1))
        assertFalse(UpdatePolicy.automaticDue(false,999999999,999999999,1,0,0,-1))
        assertFalse(UpdatePolicy.automaticDue(true,500,100,2,1000,1000,1)) // reboot and wall clock backwards
    }
    @Test fun notificationDenialMonitorAndPostponeDoNotDisableUpdateAvailability() {
        val release=verify(json()); assertEquals(6L,release.code)
        assertFalse(UpdatePolicy.shouldNotify(6,5,true,false,false,0,0))
        assertFalse(UpdatePolicy.shouldNotify(6,5,true,true,true,0,0))
        assertFalse(UpdatePolicy.shouldNotify(6,5,true,true,false,6,0))
        assertFalse(UpdatePolicy.shouldNotify(6,5,true,true,false,0,6))
        assertTrue(UpdatePolicy.shouldNotify(7,5,true,true,false,6,6))
    }
    @Test fun httpsRedirectAndOfficialApkRulesRejectUnexpectedOrigins() {
        assertFalse(UpdateSource.approvedHttps("http://github.com/${UpdateSource.REPOSITORY}/releases/latest/download/version.json"))
        assertFalse(UpdateSource.approvedHttps("https://github.com/another/repository/releases/latest/download/version.json"))
        assertFalse(UpdateSource.approvedHttps("https://user:pass@github.com/${UpdateSource.REPOSITORY}/releases/latest/download/version.json"))
        assertFalse(UpdateSource.approvedHttps("https://github.com.evil.example/file"))
        assertTrue(UpdateSource.approvedHttps("https://release-assets.githubusercontent.com/path?token=public-signed-redirect"))
        reject { verify(json().put("url","https://example.com/app.apk")) }
    }
    @Test fun downloadValidatesHashSizeAndCleansInterruptedCancelledFiles() {
        val release=verify(json())
        fun run(input:InputStream,manifest:ReleaseManifest=release,cancel:AtomicBoolean=AtomicBoolean()) {
            val file=File.createTempFile("cam-update-",".part")
            try { input.use { VerifiedDownload.copy(it,file,manifest,cancel) {} }; assertEquals(3L,file.length()) }
            catch(e:Exception) { assertFalse(file.exists()); throw e } finally { file.delete() }
        }
        run(ByteArrayInputStream(byteArrayOf(1,2,3)))
        reject { run(ByteArrayInputStream(byteArrayOf(1,2,4))) }
        reject { run(ByteArrayInputStream(byteArrayOf(1,2))) }
        reject { run(ByteArrayInputStream(byteArrayOf(1,2,3,4))) }
        try { run(object:InputStream() { override fun read():Int=throw IOException("falha simulada") }); fail() } catch (_:IOException) { }
        try { run(ByteArrayInputStream(byteArrayOf(1,2,3)),cancel=AtomicBoolean(true)); fail() } catch (_:java.util.concurrent.CancellationException) { }
    }
    @Test fun apkIdentityRejectsOtherPackageSignerVersionSdkAndAbi() {
        val release=verify(json())
        fun identity(name:String="com.camlite.android",code:Long=6,signer:String=UpdateSource.APK_SIGNER,native:Set<String> =emptySet(),sdk:Int=35)=
            UpdateIdentity.validate(release,name,code,"1.5.0",26,setOf(signer),setOf(UpdateSource.APK_SIGNER),native,setOf("arm64-v8a"),sdk,5)
        identity()
        reject { identity(name="outro.app") }; reject { identity(signer="0".repeat(64)) }; reject { identity(code=5) }
        reject { identity(native=setOf("x86")) }; reject { identity(sdk=25) }
    }
}
