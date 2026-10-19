import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")   // Kotlin 2.0+ Compose compiler
}

// App Services endpoint: read from local.properties (written by setup-capella.sh),
// falling back to a Gradle property or environment variable.
val cblProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cblProp(name: String): String =
    (cblProps.getProperty(name) ?: project.findProperty(name)?.toString()
        ?: System.getenv(name) ?: "").trim()

android {
    namespace = "com.couchbase.inspectionsapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.couchbase.inspectionsapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "APP_SERVICES_ENDPOINT_URL", "\"${cblProp("cbl.endpointUrl")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true   // REQUIRED under AGP 8+ for BuildConfig.APP_SERVICES_ENDPOINT_URL
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    // Couchbase Lite Enterprise Edition (custom Couchbase Maven repo, see settings.gradle.kts).
    implementation("com.couchbase.lite:couchbase-lite-android-ee-ktx:4.1.0")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")   // collectAsStateWithLifecycle()
    implementation("androidx.navigation:navigation-compose:2.8.0")
}
