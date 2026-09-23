plugins {
    id("com.android.application")
}

val signingStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE").orNull
val signingStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull

android {
    namespace = "com.usb.smartcontrol"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.usb.smartcontrol"
        minSdk = 26
        targetSdk = 37
        versionCode = 360
        versionName = "3.6.0"
    }

    signingConfigs {
        if (signingStoreFile != null) {
            create("stable") {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            if (signingStoreFile != null) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
}
