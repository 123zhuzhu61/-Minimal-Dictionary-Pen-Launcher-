plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.dictpenlauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.dictpenlauncher"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "3.0"

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
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    
    // 拼音转换库
    implementation("com.belerweb:pinyin4j:2.5.1")
}