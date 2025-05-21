plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.app300"
    compileSdk = 34



    defaultConfig {
        applicationId = "com.example.app300"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "开发者：译岸"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
}

dependencies {
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation ("com.google.android.material:material:1.5.0")
    implementation ("com.google.android.material:material:1.5.0")
    implementation(libs.material)
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}