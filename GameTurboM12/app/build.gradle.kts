plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.gameturbo.m12"
    compileSdk = 33
    defaultConfig {
        applicationId = "com.gameturbo.m12"
        minSdk = 29          // Galaxy M12 ships with Android 10
        targetSdk = 33
        versionCode = 1; versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
