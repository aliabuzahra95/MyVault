import java.util.Properties

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
    compileSdkExtension = 19

    val localPropertiesFile = rootProject.file("local.properties")
    val localProperties = Properties().apply {
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }

    val supabaseUrl = providers.gradleProperty("MYVAULT_SUPABASE_URL").orElse("").get()
    val supabaseAnonKey = providers.gradleProperty("MYVAULT_SUPABASE_ANON_KEY").orElse("").get()
    val syncProxyUrl = providers.gradleProperty("MYVAULT_SYNC_PROXY_URL").orElse("").get()
    val syncProxyToken = providers.gradleProperty("MYVAULT_SYNC_PROXY_TOKEN").orElse("").get()
    fun String.escapedForBuildConfig(): String = replace("\\", "\\\\").replace("\"", "\\\"")

    val firebaseApiKey = providers.gradleProperty("MYVAULT_FIREBASE_API_KEY").orElse("").get()
    val firebaseAppId = providers.gradleProperty("MYVAULT_FIREBASE_APP_ID").orElse("").get()
    val firebaseProjectId = providers.gradleProperty("MYVAULT_FIREBASE_PROJECT_ID").orElse("").get()
    val localOpenAiApiKey = localProperties.getProperty("MYVAULT_OPENAI_API_KEY").orEmpty().trim()
    val openAiApiKey = localOpenAiApiKey.ifBlank {
        providers.environmentVariable("OPENAI_API_KEY")
            .orElse(providers.gradleProperty("MYVAULT_OPENAI_API_KEY"))
            .orElse("")
            .get()
            .trim()
    }
    val localGeminiApiKey = localProperties.getProperty("MYVAULT_GEMINI_API_KEY").orEmpty().trim()
    val geminiApiKey = localGeminiApiKey.ifBlank {
        providers.environmentVariable("GEMINI_API_KEY")
            .orElse(providers.gradleProperty("MYVAULT_GEMINI_API_KEY"))
            .orElse("")
            .get()
            .trim()
    }
    val localKimiApiKey = localProperties.getProperty("MYVAULT_KIMI_API_KEY").orEmpty().trim()
    val kimiApiKey = localKimiApiKey.ifBlank {
        providers.environmentVariable("KIMI_API_KEY")
            .orElse(providers.environmentVariable("MOONSHOT_API_KEY"))
            .orElse(providers.gradleProperty("MYVAULT_KIMI_API_KEY"))
            .orElse("")
            .get()
            .trim()
    }
    val openAiTranscribeModel = localProperties.getProperty("MYVAULT_OPENAI_TRANSCRIBE_MODEL").orEmpty().trim().ifBlank {
        providers.gradleProperty("MYVAULT_OPENAI_TRANSCRIBE_MODEL")
            .orElse("gpt-4o-transcribe")
            .get()
            .trim()
    }
    val noteFormattingKimiFastModel = providers.gradleProperty("MYVAULT_KIMI_FAST_MODEL").orElse("kimi-k2.6").get().trim()
    val noteFormattingKimiSmartModel = providers.gradleProperty("MYVAULT_KIMI_SMART_MODEL").orElse("kimi-k2.6").get().trim()
    val localGoogleSpeechAccessToken = localProperties.getProperty("MYVAULT_GOOGLE_SPEECH_ACCESS_TOKEN").orEmpty().trim()
    val googleSpeechAccessToken = localGoogleSpeechAccessToken.ifBlank {
        providers.environmentVariable("GOOGLE_SPEECH_ACCESS_TOKEN")
            .orElse(providers.gradleProperty("MYVAULT_GOOGLE_SPEECH_ACCESS_TOKEN"))
            .orElse("")
            .get()
            .trim()
    }
    val googleSpeechProjectId = localProperties.getProperty("MYVAULT_GOOGLE_SPEECH_PROJECT_ID").orEmpty().trim().ifBlank {
        providers.environmentVariable("GOOGLE_CLOUD_PROJECT")
            .orElse(providers.gradleProperty("MYVAULT_GOOGLE_SPEECH_PROJECT_ID"))
            .orElse("")
            .get()
            .trim()
    }
    val googleSpeechLocation = localProperties.getProperty("MYVAULT_GOOGLE_SPEECH_LOCATION").orEmpty().trim().ifBlank {
        providers.gradleProperty("MYVAULT_GOOGLE_SPEECH_LOCATION")
            .orElse("us")
            .get()
            .trim()
    }
    val googleSpeechModel = localProperties.getProperty("MYVAULT_GOOGLE_SPEECH_MODEL").orEmpty().trim().ifBlank {
        providers.gradleProperty("MYVAULT_GOOGLE_SPEECH_MODEL")
            .orElse("chirp_3")
            .get()
            .trim()
    }
    val googleSpeechServiceAccountJsonBase64 =
        localProperties.getProperty("MYVAULT_GOOGLE_SPEECH_SERVICE_ACCOUNT_JSON_BASE64").orEmpty().trim().ifBlank {
            providers.environmentVariable("GOOGLE_SPEECH_SERVICE_ACCOUNT_JSON_BASE64")
                .orElse(providers.gradleProperty("MYVAULT_GOOGLE_SPEECH_SERVICE_ACCOUNT_JSON_BASE64"))
                .orElse("")
                .get()
                .trim()
        }

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
        buildConfigField("String", "OPENAI_API_KEY", "\"${openAiApiKey.escapedForBuildConfig()}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey.escapedForBuildConfig()}\"")
        buildConfigField("String", "KIMI_API_KEY", "\"${kimiApiKey.escapedForBuildConfig()}\"")
        buildConfigField("String", "OPENAI_TRANSCRIBE_MODEL", "\"${openAiTranscribeModel.escapedForBuildConfig()}\"")
        buildConfigField("String", "NOTE_FORMATTING_KIMI_FAST_MODEL", "\"${noteFormattingKimiFastModel.escapedForBuildConfig()}\"")
        buildConfigField("String", "NOTE_FORMATTING_KIMI_SMART_MODEL", "\"${noteFormattingKimiSmartModel.escapedForBuildConfig()}\"")
        buildConfigField("String", "GOOGLE_SPEECH_ACCESS_TOKEN", "\"${googleSpeechAccessToken.escapedForBuildConfig()}\"")
        buildConfigField("String", "GOOGLE_SPEECH_SERVICE_ACCOUNT_JSON_BASE64", "\"${googleSpeechServiceAccountJsonBase64.escapedForBuildConfig()}\"")
        buildConfigField("String", "GOOGLE_SPEECH_PROJECT_ID", "\"${googleSpeechProjectId.escapedForBuildConfig()}\"")
        buildConfigField("String", "GOOGLE_SPEECH_LOCATION", "\"${googleSpeechLocation.escapedForBuildConfig()}\"")
        buildConfigField("String", "GOOGLE_SPEECH_MODEL", "\"${googleSpeechModel.escapedForBuildConfig()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    kotlin {
        jvmToolchain(21)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.pdf.viewer.fragment)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.hilt.android)
    implementation(libs.play.services.auth)
    implementation(libs.azure.speech)
    implementation(libs.pdfbox.android)
    implementation(libs.material)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260522")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
