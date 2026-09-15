plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace="com.jejakteknisi.mesh"
    compileSdk=35
    defaultConfig { applicationId="com.jejakteknisi.mesh"; minSdk=26; targetSdk=35; versionCode=20; versionName="2.0" }
    buildTypes {
        getByName("release") {
            // Signed with the standard debug key so the generated release APK can be installed directly for testing.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies { implementation("androidx.core:core-ktx:1.15.0"); implementation("androidx.activity:activity-compose:1.10.1"); implementation("androidx.core:core:1.15.0"); implementation(platform("androidx.compose:compose-bom:2024.12.01")); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); implementation("androidx.compose.material3:material3"); implementation("org.java-websocket:Java-WebSocket:1.5.7"); debugImplementation("androidx.compose.ui:ui-tooling") }
