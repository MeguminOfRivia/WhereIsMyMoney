import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * 正式版签名配置。
 *
 * 密钥和密码放在仓库外的 .toolchain/keystore/（不进 git），
 * 只有本机能签出正式包；换机器时要记得把这个目录一起备份，
 * 否则以后就没法给已安装的用户推送升级了。
 */
val keystorePropsFile = rootProject.file("../.toolchain/keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val canSignRelease = keystorePropsFile.exists()

android {
    namespace = "com.friday.wimm"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.friday.wimm"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    buildFeatures {
        compose = true
        // 用于 AppLog：只有 debug 包才输出日志，release 包完全静默
        buildConfig = true
    }

tasks.register("testClasses") {
    description = "Placeholder for test classes task"
    group = "verification"
}
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // Navigation 导航
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Lifecycle ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // 图表：本项目一直用 Canvas 自绘，MPAndroidChart 从未被引用，已移除（省约 1MB）
    
    // Excel 解析库 (Apache POI)
    implementation("org.apache.poi:poi:5.2.5") {
        exclude(group = "javax.xml.stream", module = "stax-api")
    }
    implementation("org.apache.poi:poi-ooxml:5.2.5") {
        exclude(group = "javax.xml.stream", module = "stax-api")
        exclude(group = "stax", module = "stax-api")
    }
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}