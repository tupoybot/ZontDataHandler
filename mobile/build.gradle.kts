import java.io.File
import java.util.Properties

data class ReleaseSigningConfig(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun loadReleaseSigningConfig(): ReleaseSigningConfig? {
    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(keystoreProperties::load)
    }

    fun readValue(propertyName: String, envName: String): String? {
        return System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }
            ?: keystoreProperties.getProperty(propertyName)?.trim()?.takeIf { it.isNotEmpty() }
    }

    val storePath = readValue("storeFile", "ZONT_RELEASE_STORE_FILE")
    val storePassword = readValue("storePassword", "ZONT_RELEASE_STORE_PASSWORD")
    val keyAlias = readValue("keyAlias", "ZONT_RELEASE_KEY_ALIAS")
    val keyPassword = readValue("keyPassword", "ZONT_RELEASE_KEY_PASSWORD")
    val resolvedValues = listOf(storePath, storePassword, keyAlias, keyPassword)

    if (resolvedValues.all { it == null }) {
        return null
    }
    if (resolvedValues.any { it.isNullOrBlank() }) {
        logger.warn("Release signing config is incomplete. Building unsigned mobile release APK.")
        return null
    }

    val resolvedStoreFile = File(storePath!!).let { candidate ->
        if (candidate.isAbsolute) {
            candidate
        } else {
            rootProject.file(storePath)
        }
    }
    if (!resolvedStoreFile.isFile) {
        logger.warn(
            "Release signing config was provided, but the keystore file was not found at ${resolvedStoreFile.path}. Building unsigned mobile release APK.",
        )
        return null
    }

    return ReleaseSigningConfig(
        storeFile = resolvedStoreFile,
        storePassword = storePassword!!,
        keyAlias = keyAlias!!,
        keyPassword = keyPassword!!,
    )
}

val releaseSigning = loadReleaseSigningConfig()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.botkin.zontdatahandler.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.botkin.zontdatahandler"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2"
    }

    signingConfigs {
        releaseSigning?.let { config ->
            create("externalRelease") {
                storeFile = config.storeFile
                storePassword = config.storePassword
                keyAlias = config.keyAlias
                keyPassword = config.keyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("externalRelease")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.wearable)

    testImplementation(kotlin("test"))
}
