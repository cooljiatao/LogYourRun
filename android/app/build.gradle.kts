plugins { alias(libs.plugins.android.application) }

android {
  namespace = "com.jiatao.logyourrun"
  compileSdk = 37
  defaultConfig {
    applicationId = "com.jiatao.logyourrun"
    minSdk = 24
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
  }

  buildFeatures {
    buildConfig = true
  }

  flavorDimensions += "version"
  productFlavors{
    create("pvz"){
      dimension = "version"
      applicationId = "com.jiatao.logyourrun.pvz"
      buildConfigField("String", "API_URL", "\"file:///android_asset/PlantsVSZombies/LogYourRun-PVZ.html\"")
      manifestPlaceholders["APP_NAME"] = "Run Plans"
    }
    create("mc"){
      dimension = "version"
      applicationId = "com.jiatao.logyourrun"
      buildConfigField("String", "API_URL", "\"file:///android_asset/LogYourRun-v6.html\"")
      manifestPlaceholders["APP_NAME"] = "Run Craft"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin { jvmToolchain(17) }

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.kotlinx.coroutines.android)
}
