import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val privateSigningFile = providers.gradleProperty("camLiteSigningProperties").orNull?.let { file(it) }
    ?: File(System.getProperty("user.home"), ".codex/private/cam-lite/signing.properties")
val privateSigning = Properties().apply { if (privateSigningFile.isFile) privateSigningFile.inputStream().use { load(it) } }
val permanentSigningStore = privateSigning.getProperty("storeFile")?.let { File(it) }
    ?: File(System.getProperty("user.home"), ".codex/private/cam-lite/cam-lite-original.keystore")
val verifyPermanentSigning = tasks.register("verifyPermanentSigning") {
    doLast {
        check(privateSigningFile.isFile && permanentSigningStore.isFile) { "Assinatura permanente indisponível. Restaure a chave original e o arquivo externo de assinatura; não gere uma chave nova." }
        val store = KeyStore.getInstance(permanentSigningStore, privateSigning.getProperty("storePassword").toCharArray())
        val certificate = store.getCertificate(privateSigning.getProperty("keyAlias")) ?: error("Certificado original não encontrado")
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }
        check(digest == "9e7287463942b2d3a9e836a6273811f8ba20dfe74ee35d9e86109385636d193d") { "Assinatura incompatível com os APKs 1.0/1.1. Build interrompido para proteger atualizações." }
    }
}
android {
    namespace = "com.camlite.android"
    compileSdk = 35
    defaultConfig { applicationId = "com.camlite.android"; minSdk = 26; targetSdk = 35; versionCode = 5; versionName = "1.4.0"; testInstrumentationRunner = "com.camlite.android.SmokeInstrumentation" }
    buildFeatures { buildConfig = true }
    signingConfigs.getByName("debug") {
        storeFile = permanentSigningStore
        storePassword = privateSigning.getProperty("storePassword")
        keyAlias = privateSigning.getProperty("keyAlias")
        keyPassword = privateSigning.getProperty("keyPassword")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { signingConfig = signingConfigs.getByName("debug"); isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifyPermanentSigning) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
}
