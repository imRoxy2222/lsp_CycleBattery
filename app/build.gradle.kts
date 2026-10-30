import java.io.File
import java.util.Properties
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 签名配置（keystore.properties 在项目根目录，与 keystore/ 一起提交到私有仓库）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/**
 * versionCode 数据源：当前分支提交 commit 数（git rev-list --count HEAD）。
 * 用 ValueSource 包装以兼容配置缓存（值变化时 Gradle 会自动失效缓存）。
 * git 不可用（未初始化仓库/无 git 命令）时回退为 1，保证任何环境都能构建。
 */
interface CommitCountParameters : ValueSourceParameters {
    @get:Input
    val projectDir: Property<String>
}

abstract class CommitCountValueSource : ValueSource<Int, CommitCountParameters> {
    override fun obtain(): Int = try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(File(parameters.projectDir.get()))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output.toIntOrNull() ?: 1
    } catch (_: Throwable) {
        1
    }
}

val commitCount: Int = providers.of(CommitCountValueSource::class.java) {
    parameters.projectDir.set(rootDir.absolutePath)
}.get()

android {
    namespace = "io.github.imroxy2222.lsp_coloros17cyclebattery"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.imroxy2222.lsp_coloros17cyclebattery"
        minSdk = 35
        targetSdk = 37
        versionCode = commitCount
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("module") {
            if (keystoreProps.getProperty("storeFile") != null) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            if (keystoreProps.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("module")
            }
        }
        debug {
            if (keystoreProps.getProperty("storeFile") != null) {
                // Debug 也用同一签名，避免与 Release 安装包签名冲突
                signingConfig = signingConfigs.getByName("module")
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