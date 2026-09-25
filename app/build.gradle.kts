import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val localSigning = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
android {
    namespace = "com.levanyon.meditakip"
    compileSdk = 35
    defaultConfig { applicationId = "com.levanyon.meditakip"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "1.1" }
    buildFeatures { compose = true }
    bundle { language { enableSplit = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (localSigning.isNotEmpty()) create("localRelease") {
            storeFile = rootProject.file(requireNotNull(localSigning.getProperty("storeFile")))
            storePassword = requireNotNull(localSigning.getProperty("storePassword"))
            keyAlias = requireNotNull(localSigning.getProperty("keyAlias"))
            keyPassword = requireNotNull(localSigning.getProperty("keyPassword"))
        }
    }
    buildTypes { release { isMinifyEnabled = false; if (localSigning.isNotEmpty()) signingConfig = signingConfigs.getByName("localRelease") } }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // Conscrypt's Windows native loader is locale-sensitive (Turkish dotted I).
        unitTests.all { it.systemProperty("user.language", "en"); it.systemProperty("user.country", "US") }
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
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
