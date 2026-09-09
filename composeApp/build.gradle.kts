import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

configurations.configureEach {
    resolutionStrategy.force(
        "androidx.compose.animation:animation:1.10.5",
        "androidx.compose.animation:animation-core:1.10.5",
        "androidx.compose.foundation:foundation:1.10.5",
        "androidx.compose.foundation:foundation-layout:1.10.5",
        "androidx.compose.runtime:runtime:1.10.5",
        "androidx.compose.runtime:runtime-saveable:1.10.5",
        "androidx.compose.ui:ui:1.10.5",
        "androidx.compose.ui:ui-geometry:1.10.5",
        "androidx.compose.ui:ui-graphics:1.10.5",
        "androidx.compose.ui:ui-test:1.10.5",
        "androidx.compose.ui:ui-test-junit4:1.10.5",
        "androidx.compose.ui:ui-text:1.10.5",
        "androidx.compose.ui:ui-tooling:1.10.5",
        "androidx.compose.ui:ui-tooling-data:1.10.5",
        "androidx.compose.ui:ui-tooling-preview:1.10.5",
        "androidx.compose.ui:ui-unit:1.10.5",
        "androidx.compose.ui:ui-util:1.10.5",
    )
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.material3)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.preference.ktx)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.guava)
            implementation(libs.androidx.sqlite)
            implementation(libs.sqlcipher.android)
        }
        commonMain.dependencies {
            implementation(projects.preferences)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.sqldelight.runtime)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.moko.biometry)
            implementation(libs.moko.biometry.compose)
            implementation(libs.diglol.crypto.cipher)
            implementation(libs.diglol.crypto.kdf)
            implementation(libs.diglol.crypto.mac)
            implementation(libs.diglol.crypto.random)
            implementation(libs.coil.compose)
            implementation(libs.qr.kit)
            implementation(libs.filekit)
            implementation(libs.signum.supreme)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }

        // Tests
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.boxy.authenticator"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.boxy.authenticator"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 23
        versionName = "4.2.1"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

sqldelight {
    databases {
        create("TokenDatabase") {
            packageName.set("com.boxy.authenticator.db")
        }
    }
}

dependencies {
    debugImplementation(compose.uiTooling)

    constraints {
        listOf(
            libs.androidx.compose.animation,
            libs.androidx.compose.animation.core,
            libs.androidx.compose.foundation,
            libs.androidx.compose.foundation.layout,
            libs.androidx.compose.runtime.saveable,
            libs.androidx.compose.ui,
            libs.androidx.compose.ui.graphics,
            libs.androidx.compose.ui.test,
            libs.androidx.compose.ui.test.junit4,
            libs.androidx.compose.ui.text,
            libs.androidx.compose.ui.tooling,
            libs.androidx.compose.ui.tooling.data,
        ).forEach { implementation(it) }
    }
}
