import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
// 署名設定がないまま release を作ると未署名の AAB/APK になる(Play にアップロードできない)ので警告する
if (!keystorePropsFile.exists() && gradle.startParameter.taskNames.any { it.contains("Release") }) {
    logger.warn("WARNING: keystore.properties がないため release は未署名になります(docs/RELEASE.md 参照)")
}

android {
    namespace = "io.github.hatake716.animalplanet"
    compileSdk = 36

    defaultConfig {
        // Play ストアの URL に露出し、公開後は変更できない。第三者の商標(Animal Planet)を含めないよう
        // リポジトリ名とは別の中立な名前にしている(コードのパッケージ名 namespace は変更不要)。
        applicationId = "io.github.hatake716.endangeredglobe"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
    }

    // 地球テクスチャ・写真(JPEG)は既に圧縮済みなので aapt の再圧縮を避ける
    androidResources {
        noCompress += listOf("jpg", "json")
    }

    if (keystorePropsFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
}
