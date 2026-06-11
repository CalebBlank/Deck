import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Read version.properties and, if an assemble task was requested, increment versionCode now
// (configuration-time increment keeps defaultConfig.versionCode and the APK filename in sync)
val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}

val isAssembling = gradle.startParameter.taskNames.any {
    it.contains("assemble", ignoreCase = true) || it.contains("install", ignoreCase = true)
}
val currentVersionCode: Int = run {
    val stored = (versionProps.getProperty("versionCode") ?: "3").toInt()
    if (isAssembling) {
        val next = stored + 1
        versionProps["versionCode"] = next.toString()
        versionPropsFile.outputStream().use { versionProps.store(it, null) }
        next
    } else {
        stored
    }
}
val currentVersionName: String = versionProps.getProperty("versionName") ?: "0.2.1"

android {
    namespace = "com.hermes.deck"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hermes.deck"
        minSdk = 26
        targetSdk = 35
        versionCode = currentVersionCode
        versionName = currentVersionName
        // Single ABI for the dev device — keeps the ONNX Runtime native libs from bloating the apk.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    // Keep the .onnx embedding model uncompressed in the apk.
    androidResources {
        noCompress += "onnx"
    }
    // JavaMail (android-mail) ships META-INF license/notice files that collide on merge.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/NOTICE", "META-INF/LICENSE",
                "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST"
            )
        }
    }

    // Rename APK output to deck-vN-debug.apk / deck-vN-release.apk
    @Suppress("UnstableApiUsage")
    applicationVariants.all {
        outputs.all {
            val out = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            out.outputFileName = "deck-v${versionCode}-${buildType.name}.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation("com.materialkolor:material-kolor:2.0.0")
    implementation(libs.androidx.foundation)
    implementation(libs.kotlinx.coroutines.android)
    // MediaBrowserCompat / MediaControllerCompat — connect to other apps' media libraries (Symfonium).
    implementation("androidx.media:media:1.7.0")
    // On-device sentence embeddings (all-MiniLM-L6-v2) for the search "smart ordering" reranker.
    // 1.20+ ships 16 KB-page-aligned native libs (required by newer Android / 16 KB-page devices).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.26.0")
    // Gmail IMAP search (app-password auth) — android-mail is the Android-optimized JavaMail.
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    // On-device Gemini Nano (ML Kit GenAI Prompt API) — query-intent classifier for smarter ranking.
    // Runs in Android's AICore system service; model is downloaded on demand. Beta.
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")
    // MediaPipe LLM Inference — fallback query-intent classifier for devices where AICore won't serve
    // Gemini Nano (rooted/beta). Runs a small Qwen2.5-0.5B .task model downloaded on demand.
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    debugImplementation(libs.androidx.ui.tooling)
}
