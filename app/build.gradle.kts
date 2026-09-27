import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
/*
 * Release signing (see docs/RELEASE.md). No key or password ever lives in this repository.
 * The signing properties file is looked up in this order:
 *   1. the path in the MEDITAKIP_SIGNING environment variable
 *   2. %USERPROFILE%\AndroidKeys\meditakip\keystore.properties   (created by tools\new-release-key.ps1)
 *   3. keystore.properties in the repository root (git-ignored; legacy)
 * `storeFile` may be absolute or relative to the properties file.
 */
val signingFile: File? = listOfNotNull(
    System.getenv("MEDITAKIP_SIGNING")?.let(::File),
    File(System.getProperty("user.home"), "AndroidKeys/meditakip/keystore.properties"),
    rootProject.file("keystore.properties"),
).firstOrNull { it.isFile }
val releaseSigning = Properties().apply { signingFile?.inputStream()?.use { load(it) } }
fun signingValue(key: String): String = releaseSigning.getProperty(key)?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Signing setting missing: '$key' is not set in $signingFile. See docs/RELEASE.md.")

android {
    namespace = "com.levanyon.meditakip"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.levanyon.meditakip"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "1.2"
        testInstrumentationRunner = "com.levanyon.meditakip.data.ReportPdfDeviceTest"
        manifestPlaceholders["appLabel"] = "MediTakip"
    }
    buildFeatures { compose = true }
    bundle { language { enableSplit = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (signingFile != null) create("release") {
            val path = File(signingValue("storeFile"))
            storeFile = if (path.isAbsolute) path else signingFile.parentFile.resolve(path)
            storePassword = signingValue("storePassword")
            keyAlias = signingValue("keyAlias")
            keyPassword = signingValue("keyPassword")
        }
    }
    buildTypes {
        release {
            // R8 stays off until a minified APK has been run on a real device (DECISIONS D6.2).
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingFile != null) signingConfig = signingConfigs.getByName("release")
        }
        // Debug installs as a separate app, so a test build can never replace (or touch the data of)
        // the real MediTakip on the phone.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "MediTakip (debug)"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // Conscrypt's Windows native loader is locale-sensitive (Turkish dotted I).
        unitTests.all { it.systemProperty("user.language", "en"); it.systemProperty("user.country", "US") }
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Never produce an unsigned (uninstallable) or accidentally debug-signed release APK: stop early instead.
val verifyReleaseSigning by tasks.registering {
    doLast {
        if (signingFile == null) throw GradleException(
            "No release signing key found. Restore the existing signing configuration " +
            "(or set the MEDITAKIP_SIGNING environment variable). See docs/RELEASE.md."
        )
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseSigning) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
