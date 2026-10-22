import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}

android {
    namespace = "com.github.chsssssss.eonje"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.github.chsssssss.eonje"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "KAKAO_REST_API_KEY",
            "\"${localProperties.getProperty("KAKAO_REST_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "KAKAO_NATIVE_APP_KEY",
            "\"${localProperties.getProperty("KAKAO_NATIVE_APP_KEY", "")}\""
        )
        // Firebase AI Logic(Gemini 클라우드 폴백)용 — google-services.json 없이 FirebaseOptions로 직접 초기화한다.
        // Firebase 프로젝트 설정 전에는 비워둬도 빌드는 되고, 캡션 파싱은 온디바이스(ML Kit GenAI)만 시도한다.
        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            "\"${localProperties.getProperty("FIREBASE_PROJECT_ID", "")}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_APPLICATION_ID",
            "\"${localProperties.getProperty("FIREBASE_APPLICATION_ID", "")}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            "\"${localProperties.getProperty("FIREBASE_API_KEY", "")}\""
        )
        // Firebase Analytics는 FirebaseOptions/FirebaseApp을 전혀 보지 않고 오직 `google_app_id` 문자열 리소스만
        // 읽어서 전송 대상 프로젝트를 정한다(play-services-measurement 23.2.0 바이트코드로 확인). google-services
        // 플러그인이 하던 일을 대신해 wherewegoing(Blaze) 앱 ID를 넣는다 — 기본 FirebaseApp이 Gemini 프로젝트여도
        // Analytics는 이 값만 따라가므로 Gemini 쪽엔 붙지 않는다. 값이 없으면 SDK가 스스로 비활성화된다.
        // 이 리소스가 생기면 FirebaseInitProvider가 이를 근거로 기본 앱을 자동 초기화해버리므로 매니페스트에서
        // FirebaseInitProvider를 제거해뒀다(AndroidManifest.xml 참고).
        localProperties.getProperty("FIREBASE_APPLICATION_ID", "").takeIf { it.isNotBlank() }?.let {
            resValue("string", "google_app_id", it)
        }
        // Firebase AI Logic(Gemini) 전용 프로젝트 — 위 FIREBASE_* 와 분리된 별도 프로젝트.
        buildConfigField(
            "String",
            "FIREBASE_GEMINI_PROJECT_ID",
            "\"${localProperties.getProperty("FIREBASE_GEMINI_PROJECT_ID", "")}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_GEMINI_APPLICATION_ID",
            "\"${localProperties.getProperty("FIREBASE_GEMINI_APPLICATION_ID", "")}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_GEMINI_API_KEY",
            "\"${localProperties.getProperty("FIREBASE_GEMINI_API_KEY", "")}\""
        )
    }

    // 릴리스 서명 키스토어는 리포 밖(~/.android/)에 두고 local.properties로 경로·비밀번호를 읽는다 — 키스토어 자체와
    // local.properties 둘 다 git에 안 올라간다. local.properties에 항목이 없으면(예: 새로 클론한 환경) 서명 설정을
    // 아예 안 만들어서 release 이외 태스크(compile, lint 등)는 그대로 동작하고, assembleRelease/bundleRelease만
    // "키스토어를 못 찾았다"는 명확한 에러로 실패한다.
    val releaseKeystorePath = localProperties.getProperty("RELEASE_KEYSTORE_PATH", "")
    val hasReleaseSigning = releaseKeystorePath.isNotBlank() && file(releaseKeystorePath).exists()

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD", "")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
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
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

// Room 스키마를 JSON으로 내보내 git에 남긴다 — 다음 스키마 변경 때 마이그레이션 작성·검증의 기준이 된다.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.kakao.maps)
    implementation(libs.play.services.location)
    implementation(libs.coil.compose)
    implementation(libs.androidx.glance.appwidget)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.playintegrity)
    // 디버그 프로바이더는 릴리스에 포함하지 않는다 — src/debug, src/release의 appCheckProviderFactory() 참고.
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.analytics)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.mlkit.genai.prompt)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}