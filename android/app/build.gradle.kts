import java.util.Properties
import java.io.FileInputStream
import com.android.build.gradle.internal.api.ApkVariantOutputImpl

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val keystorePropsFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropsFile.exists()) {
        load(FileInputStream(keystorePropsFile))
    }
}

android {
    namespace = "de.astubenbord.paperless_mobile"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        // Required for flutter_local_notifications
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "de.astubenbord.paperless_mobile"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        // Required for flutter_local_notifications
        multiDexEnabled = true
        // Optional ABI filter: -PndkAbiFilters=arm64-v8a (comma-separated).
        // The vendored OpenCV AAR (T-03 crop) ships .so files for EVERY ABI and
        // ndk.abiFilters does NOT filter AAR-provided libraries — they leak into
        // every APK (x86_64 alone = 75 MB). So we strip unwanted ABIs from the
        // final APK via packaging.jniLibs.excludes. CI uses this to keep the
        // arm64 build ~45 MB like stock paperless-mobile.
        val abi = project.findProperty("ndkAbiFilters")?.toString()
        if (abi != null && abi.isNotBlank()) {
            ndk {
                abiFilters.addAll(abi.split(",").map { it.trim() })
            }
            val allowed = abi.split(",").map { it.trim() }.toSet()
            val unwanted = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                .filter { it !in allowed }
            if (unwanted.isNotEmpty()) {
                packaging {
                    jniLibs {
                        unwanted.forEach { excludes += "lib/$it/*" }
                    }
                }
            }
        }
    }

    signingConfigs {
        create("release") {
            val alias = keystoreProperties.getProperty("keyAlias")
            val keyPass = keystoreProperties.getProperty("keyPassword")
            val storePath = keystoreProperties.getProperty("storeFile")
            val storePass = keystoreProperties.getProperty("storePassword")

            if (alias != null) keyAlias = alias
            if (keyPass != null) keyPassword = keyPass
            if (storePath != null) storeFile = file(storePath)
            if (storePass != null) storePassword = storePass
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}

flutter {
    source = "../.."
}

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)
android.applicationVariants.configureEach {
    val variant = this
    variant.outputs.forEach { output ->
        val abiVersionCode = abiCodes[output.filters.find { it.filterType == "ABI" }?.identifier]
        if (abiVersionCode != null) {
            (output as ApkVariantOutputImpl).versionCodeOverride = variant.versionCode * 10 + abiVersionCode
        }
    }
}