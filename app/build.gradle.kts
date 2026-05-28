plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.aura"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aura"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.fragment.ktx)
    implementation(libs.viewpager2)
    implementation(libs.swiperefresh)
    implementation(libs.recyclerview)

    // Scraping
    implementation(libs.jsoup)
    implementation(libs.okhttp)

    // Coroutines
    implementation(libs.coroutines.android)

    // Lifecycle & ViewModel
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.livedata)

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Image Loading
    implementation(libs.glide)
    kapt(libs.glide.compiler)

    // Video Player
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.media3.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // DataStore
    implementation(libs.datastore)

    // Shimmer
    implementation(libs.shimmer)

    // Paging
    implementation(libs.paging)

    // Start.io Ads
    implementation(libs.startapp.sdk)



    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.23")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23")
    }
}

