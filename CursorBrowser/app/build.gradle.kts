plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 33
    
    defaultConfig {
        applicationId = "com.example.cursorbrowser"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    
    namespace = "com.example.cursorbrowser"
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")
    implementation("androidx.activity:activity-compose:1.6.1")
    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.compiler:compiler:1.4.8")
    implementation("androidx.webkit:webkit:1.6.0")
}