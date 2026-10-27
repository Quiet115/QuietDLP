import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.quietdlp.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quietdlp.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 14
        versionName = "0.2.8"

        // Solo arm64: cubre prácticamente todos los móviles actuales y reduce el tamaño.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Firma de las versiones que se reparten. Los datos (ruta y contraseñas de la clave) están en
    // keystore.properties, que NO se sube a git. Sin ese archivo, la versión de publicación sale sin firmar.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        // Imprescindible: Python/yt-dlp/ffmpeg son binarios que deben extraerse al disco
        // para poder ejecutarse. Sin esto la app instala bien pero no descarga nada.
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // Motor: yt-dlp + Python + ffmpeg empaquetados para Android (GPL-3.0)
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
}
