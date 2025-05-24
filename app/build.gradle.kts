plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

}

android {
    namespace = "com.jorgenascimento.tvplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tvplayer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation (libs.androidx.core.ktx.v190)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.core.ktx.v1160)
    implementation(libs.androidx.appcompat.v170)
    implementation(libs.material.v1120)
    implementation(libs.androidx.activity.v1101)
    implementation(libs.androidx.constraintlayout.v221)
    implementation(libs.libvlc.all)
    implementation(libs.gson)
    implementation(libs.libvlc.all.v346)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v121)
    androidTestImplementation(libs.androidx.espresso.core.v361)
    implementation (libs.play.services.ads)
}
