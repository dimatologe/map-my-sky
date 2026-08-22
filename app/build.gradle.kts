plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Zentrale Version: wird als App-Version UND im APK-Dateinamen verwendet (eine Quelle).
val appVersionName = "0.23.1"
val appVersionCode = 173

android {
    namespace = "com.codex.starmapper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.codex.starmapper"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "BUILD_ID", "\"2026-08-22-perf-fix1-fix2-173\"")

        vectorDrawables {
            useSupportLibrary = true
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
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// APK-Dateiname mit Version, z.B. SternbildMapper-0.11.1-79-debug.apk.
// Ueber den Basis-Namen (archivesName) statt der AGP-Variant-API -> stabil und immer verfuegbar.
// AGP haengt den Build-Typ automatisch an: <archivesName>-debug.apk / <archivesName>-release.apk.
configure<org.gradle.api.plugins.BasePluginExtension> {
    archivesName.set("MapMySky-$appVersionName-$appVersionCode")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
