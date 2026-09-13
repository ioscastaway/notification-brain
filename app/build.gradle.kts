import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// The app knows which revision of itself it is running. Stage 3 of the Evolving App series
// (self-diagnosis, self-PR) needs this to talk about the right version of its own source.
// providers.exec keeps this configuration-cache friendly; a non-git checkout yields "unknown".
val gitSha: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }

// ANTHROPIC_API_KEY comes from local.properties (git-ignored) and is exposed via BuildConfig.
// Never commit a key. An empty value is allowed so the project still builds without one; the
// Rules tab then explains that typed rules need a key.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val anthropicApiKey: String = localProps.getProperty("ANTHROPIC_API_KEY") ?: System.getenv("ANTHROPIC_API_KEY") ?: ""

android {
    namespace = "com.ioscastaway.notificationbrain"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ioscastaway.notificationbrain"
        // 30 = Android 11: ApplicationExitInfo (crash/ANR history) starts here.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GIT_SHA", "\"${gitSha.get()}\"")
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"$anthropicApiKey\"")
        buildConfigField("String", "CLAUDE_MODEL", "\"claude-opus-5\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // The Anthropic Java SDK pulls in Jackson + OkHttp; these META-INF entries collide on Android.
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties", "META-INF/versions/9/module-info.class",
                "META-INF/*.kotlin_module",
            )
        }
    }

    sourceSets {
        // docs/ is bundled as assets so the running app carries its own architecture notes.
        // This is the seed of the knowledge base the later Evolving App stages read.
        getByName("main").assets.srcDir(rootProject.file("docs"))
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // Official Anthropic SDK (Java, used from Kotlin). Only the rule compiler touches it.
    implementation(libs.anthropic.java)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
