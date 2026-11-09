plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Every CI run gets a higher versionCode, so each APK installs over the previous one.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val iteration = providers.gradleProperty("fa.iteration").get()
val runBase = providers.gradleProperty("fa.runBase").orNull?.toIntOrNull() ?: 0
val codeBase = providers.gradleProperty("fa.codeBase").orNull?.toIntOrNull() ?: 0

// CI decodes the key from the SIGNING_KEYSTORE_B64 secret. It is never committed (see .gitignore).
// Without it, builds fall back to the debug key: they still install, but not over a properly signed one.
val keystore = rootProject.file("signing/famobile.p12")
val keystorePassword: String? = System.getenv("SIGNING_PASSWORD")
val hasReleaseKey = keystore.exists() && !keystorePassword.isNullOrEmpty()

android {
    namespace = "com.kambesembi.famobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kambesembi.famobile"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber + codeBase
        versionName = "0.$iteration.${(buildNumber - runBase).coerceAtLeast(0)}"
        // Libraries bring dozens of languages; keep the app's own.
        resourceConfigurations += listOf("en", "de", "ru")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("fixed") {
                storeFile = keystore
                storeType = "pkcs12"
                storePassword = keystorePassword
                keyAlias = "famobile"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.findByName("fixed") ?: signingConfigs.getByName("debug")
        getByName("debug") {
            signingConfig = signing
            manifestPlaceholders["loginExported"] = "true"
        }
        getByName("release") {
            manifestPlaceholders["loginExported"] = "false"
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signing
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Saved FA pages: unit tests parse them, and debug builds bundle them for demo mode.
    sourceSets {
        getByName("test").resources.srcDir(rootProject.file("fixtures"))
        getByName("debug").assets.srcDir(rootProject.file("fixtures"))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    testImplementation("junit:junit:4.13.2")
    // The real org.json: Android's is only a stub in unit tests.
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
