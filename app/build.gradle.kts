import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Версия: локально берётся из gradle.properties, в GitHub Actions — из тега (v1.2.3 → 1.2.3).
val appVersionName = (findProperty("versionName") as String?) ?: "0.1.0"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt()
    ?: appVersionName.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        .let { (it.getOrElse(0) { 0 } * 10000) + (it.getOrElse(1) { 0 } * 100) + it.getOrElse(2) { 0 } }
        .coerceAtLeast(1)

// Ключ подписи. Он один на все версии: без него телефон не примет обновление поверх старой версии.
// Локально: signing/keystore.properties (в git не попадает). В GitHub Actions: секреты репозитория.
val signingProps = Properties().apply {
    val f = rootProject.file("signing/keystore.properties")
    if (f.exists()) f.inputStream().use(::load)
}
fun signingValue(key: String, env: String): String? = signingProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "app.vestari"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.vestari"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        // Репозиторий GitHub, из релизов которого приложение берёт обновления (owner/repo).
        buildConfigField("String", "UPDATE_REPO", "\"${findProperty("vestari.updateRepo") ?: ""}\"")
    }

    signingConfigs {
        val storePath = signingValue("storeFile", "VESTARI_KEYSTORE")
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "VESTARI_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "VESTARI_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "VESTARI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/*.kotlin_module")
            pickFirsts += setOf("META-INF/mailcap", "META-INF/javamail.providers", "META-INF/javamail.default.providers", "META-INF/javamail.default.address.map", "META-INF/javamail.charset.map", "META-INF/mimetypes.default")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // IMAP/SMTP: официальный порт JavaMail для Android.
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    testImplementation("junit:junit:4.13.2")
}
