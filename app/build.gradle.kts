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

val isAssembling = gradle.startParameter.taskNames.any { it.contains("assemble", ignoreCase = true) }
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
    implementation(libs.androidx.foundation)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)
}
