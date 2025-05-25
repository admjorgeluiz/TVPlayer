plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")

    // SR_NOTE: Se for usar o glide-compiler, adicione o plugin kotlin-kapt aqui
    // alias(libs.plugins.kotlin.kapt) // Supondo que você defina kotlin-kapt no [plugins] do TOML
}

android {
    namespace = "com.jorgenascimento.tvplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jorgenascimento.tvplayer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.kotlin.stdlib) // Alias: kotlin-stdlib

    implementation(libs.androidx.core.ktx) // Alias: androidx-core-ktx

    implementation(libs.androidx.appcompat) // Alias: androidx-appcompat
    implementation(libs.google.material)    // SR_CORRECTION: Alias correto é googleMaterial (do google-material no TOML)
    implementation(libs.androidx.activity.ktx) // SR_CORRECTION: Alias correto é androidxActivityKtx (do androidx-activity-ktx no TOML)
    implementation(libs.androidx.constraintlayout) // Alias: androidx-constraintlayout
    implementation(libs.androidx.recyclerview)   // Alias: androidx-recyclerview

    implementation(libs.libvlc.all)      // SR_CORRECTION: Alias correto é libvlcAll (do libvlc-all no TOML)

    implementation(libs.gson)                 // Alias: gson
    implementation(libs.play.services.ads)  // SR_CORRECTION: Alias correto é playServicesAds (do play-services-ads no TOML)

    implementation(libs.glide)                // Alias: glide
    // SR_NOTE: Se usar o glide-compiler, descomente a linha abaixo e adicione o plugin kapt
    // kapt(libs.glide.compiler)          // Supondo um alias glide-compiler no TOML

    testImplementation(libs.junit)                              // Alias: junit
    androidTestImplementation(libs.androidx.junit.ext)        // SR_CORRECTION: Alias correto é androidxJunitExt (do androidx-junit-ext no TOML)
    androidTestImplementation(libs.androidx.espresso.core)    // SR_CORRECTION: Alias correto é androidxEspressoCore (do androidx-espresso-core no TOML)
}

