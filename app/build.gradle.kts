plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

// Gate on NEW violations only: both tools check against a committed baseline
// (regenerate with `./gradlew detektBaseline ktlintGenerateBaseline` after an
// intentional cleanup, never to paper over new findings).
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("detekt.yml"))
    baseline = file("detekt-baseline.xml")
    // Plugin default is `src/main/{java,kotlin}` + `src/test/{java,kotlin}` only —
    // it silently skips src/androidTest, which is where this app's instrumented
    // flow tests (the destructive-delete safety net) live. Add it explicitly so
    // detekt actually analyses that source set instead of reporting a false-clean
    // build.
    source.setFrom(
        "src/main/java",
        "src/main/kotlin",
        "src/test/java",
        "src/test/kotlin",
        "src/androidTest/java",
        "src/androidTest/kotlin",
    )
}

ktlint {
    android.set(true)
    baseline.set(file("ktlint-baseline.xml"))
}

android {
    namespace = "dev.scuttle.inventory"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.scuttle.inventory"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.1.10"
        testInstrumentationRunner = "dev.scuttle.inventory.HiltTestRunner"
        // Disable Espresso's InputManager.getInstance() call, removed in Android 16 (API 36).

        // Base URL of the inventory API. Override per build type / flavor as needed;
        // the trailing slash is required by Retrofit.
        buildConfigField("String", "BASE_URL", "\"https://inventory.scuttle.dev/api/v1/\"")
        buildConfigField(
            "String",
            "GOOGLE_CLIENT_ID",
            "\"758637503304-np301l9sc7saepermpm2so9kcghet5k6.apps.googleusercontent.com\"",
        ) // Web OAuth client ID — used with Google Sign-In SDK

        // Live updates (Q-3): Reverb websocket endpoint. The app key is a public
        // identifier (channel security lives in the Sanctum-gated auth endpoint);
        // it must match REVERB_APP_KEY in the server's .env.
        buildConfigField("String", "REVERB_HOST", "\"inventory.scuttle.dev\"")
        buildConfigField("String", "REVERB_APP_KEY", "\"inventory-live\"")
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = file("${System.getProperty("user.home")}/.android/inventory-debug.jks")
            storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "inventoryDebug"
            keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "inventory-debug"
            keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "inventoryDebug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Skeleton/early stage: keep lint non-fatal so CI gates on compilation + tests.
    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // DI
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")

    // Secure token storage
    implementation("androidx.security:security-crypto:1.1.0")

    // Google Sign-In via Jetpack Credential Manager (replaces the deprecated
    // com.google.android.gms:play-services-auth GoogleSignIn API). credentials-play-services-auth
    // provides the Play Services backend; googleid supplies GetGoogleIdOption + GoogleIdTokenCredential.
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    // Barcode scanning (Phase 2): CameraX preview + ML Kit on-device barcode model
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Live updates (Q-3): Reverb speaks the Pusher protocol, so the stock
    // Pusher client subscribes to the private household channels.
    implementation("com.pusher:pusher-java-client:2.4.4")

    // QR generation for household invites (pure-Java, no Android deps)
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    // Instrumented / UI tests
    androidTestImplementation(composeBom)
    // Exclude espresso-core from ui-test-junit4: it calls InputManager.getInstance()
    // which was removed in Android 16 (API 36). Compose semantics-based idle sync
    // still works without it.
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.60.1")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.60.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
}
