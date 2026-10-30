plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.imroxy2222.lsp_coloros17cyclebattery"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.imroxy2222.lsp_coloros17cyclebattery"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        jniLibs {
            // DexKit 要求 so 以解压形式存在，Xposed 环境才能 System.load
            useLegacyPackaging = true
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // libxposed API 102：运行时由框架提供，compileOnly
    compileOnly(libs.libxposed.api)
    // 模块 App 与框架通信（RemotePreferences 配置同步）
    implementation(libs.libxposed.service)
    // 混淆场景下按结构搜索宿主类
    implementation(libs.dexkit)
    // 宿主 SystemUI 类的编译期桩，不打入 APK
    compileOnly(project(":stub"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}