package com.camlite.android

import android.app.Instrumentation
import android.os.Bundle
import com.camlite.android.updates.UpdateProtocol
import org.json.JSONObject
import java.time.Instant

class UpdateCompatibilityInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val old = context.assets.open("legacy-version.json").use { it.readBytes() }
            val sig = context.assets.open("legacy-version.json.sig").use { it.readBytes() }
            val stamp = JSONObject(String(old,Charsets.UTF_8)).getString("published_at")
            val legacyDateAccepted = try { Instant.parse(stamp); true } catch (_: Exception) { false }
            val protocol = UpdateProtocol(UpdateProtocol.publicKey(targetContext.resources.openRawResource(R.raw.update_public_key).bufferedReader().use { it.readText() }))
            val release = protocol.verify(old,sig,Instant.now(),android.os.Build.VERSION.SDK_INT,setOf("x86_64"),6)
            check(release.code > 6)
            check(UpdateProtocol.parseTimestamp(stamp).toString().endsWith("Z"))
            val utc = UpdateProtocol.parseTimestamp(stamp).toString()
            check(Instant.parse(utc) == release.published)
            finish(android.app.Activity.RESULT_OK, Bundle().apply { putString("result", "PASS Android API ${android.os.Build.VERSION.SDK_INT}: legacyInstantAccepted=$legacyDateAccepted; authenticated=${release.version}; canonicalUtcAccepted=true") })
        } catch (e: Exception) {
            finish(android.app.Activity.RESULT_CANCELED,Bundle().apply { putString("result","FAIL ${e.javaClass.simpleName}: ${e.message}") })
        }
    }
}
