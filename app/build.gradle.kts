plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ateliermareebasse.cartographie"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.ateliermareebasse.cartographie"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-ch1"
        resourceConfigurations += listOf("fr", "en")
    }
    sourceSets {
        getByName("main") {
            // Le cœur du jeu (sans dépendance Android) vit dans core/src ; l'application dans app/src/main/kotlin.
            kotlin.srcDirs("src/main/kotlin", "../core/src")
            assets.srcDirs("src/main/assets")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { applicationIdSuffix = ".debug" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/*.kotlin_module", "kotlin/**") }
    androidResources { noCompress += listOf("ogg", "jpg", "png", "ttf") }
    bundle { language { enableSplit = false } }
}

dependencies {
    // Aucune dépendance : Kotlin stdlib uniquement (fournie par le plugin).
}
