import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

android {
    namespace = "org.asistentepro.asistentepro"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "app.asistentepro.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 400000000
        versionName = "4.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    signingConfigs.getByName("debug") {
        System.getenv("ASISTENTE_KEYSTORE")?.let {
            storeFile = file(it)
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    // Compose's older transitive Espresso reflects InputManager.getInstance,
    // removed in Android 17. 3.7 uses the supported getSystemService path.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// The redistributable Spanish speech model is verified and bundled, never fetched
// by the installed app. No recordings or personal voice profiles are required.
val speechArchive = rootProject.layout.projectDirectory.file(".speech-cache/vosk-model-small-es-0.42.zip")
val prepareSpeechModel by tasks.registering {
    val destination = layout.buildDirectory.dir("generated/speechAssets")
    outputs.dir(destination)
    doLast {
        val archive = speechArchive.asFile
        if (!archive.exists()) {
            archive.parentFile.mkdirs()
            val temporary = File(archive.parentFile, archive.name + ".part")
            URI("https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip").toURL().openConnection().apply {
                connectTimeout = 30000; readTimeout = 60000
            }.getInputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
            check(temporary.renameTo(archive)) { "No se pudo guardar el modelo de voz" }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        archive.inputStream().use { input -> val buffer = ByteArray(65536); var n = input.read(buffer); while (n >= 0) { digest.update(buffer, 0, n); n = input.read(buffer) } }
        check(digest.digest().joinToString("") { "%02x".format(it) } == "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f") { "El modelo de voz no coincide con su SHA-256" }
        copy { from(zipTree(archive)); into(destination) }
    }
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/speechAssets"))
tasks.named("preBuild").configure { dependsOn(prepareSpeechModel) }
