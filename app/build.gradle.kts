plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.myvault.app"
    compileSdk = 36

    val supabaseUrl = providers.gradleProperty("MYVAULT_SUPABASE_URL").orElse("").get()
    val supabaseAnonKey = providers.gradleProperty("MYVAULT_SUPABASE_ANON_KEY").orElse("").get()
    val syncProxyUrl = providers.gradleProperty("MYVAULT_SYNC_PROXY_URL").orElse("").get()
    val syncProxyToken = providers.gradleProperty("MYVAULT_SYNC_PROXY_TOKEN").orElse("").get()
    val firebaseApiKey = providers.gradleProperty("MYVAULT_FIREBASE_API_KEY").orElse("").get()
    val firebaseAppId = providers.gradleProperty("MYVAULT_FIREBASE_APP_ID").orElse("").get()
    val firebaseProjectId = providers.gradleProperty("MYVAULT_FIREBASE_PROJECT_ID").orElse("").get()

    defaultConfig {
        applicationId = "com.myvault.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SYNC_PROXY_URL", "\"$syncProxyUrl\"")
        buildConfigField("String", "SYNC_PROXY_TOKEN", "\"$syncProxyToken\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation("io.github.ahmerafzal1:ahmer-pdfviewer:2.0.1")
    implementation("io.github.ahmerafzal1:ahmer-pdfium:1.9.1")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.hilt.android)
    implementation(libs.play.services.auth)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation("junit:junit:4.13.2")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
