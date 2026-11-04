plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.woody.booklauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.woody.booklauncher"
        minSdk = 28
        targetSdk = 34
        versionCode = 210
        versionName = "2.1.0"
    }

    buildTypes {
        release {
            // 쓰지 않는 코드·리소스를 빼서 APK 를 줄이고 로딩을 빠르게 한다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 포크는 디버그 키로 서명한다(나중에 배포용 키를 만들면 여기를 바꾼다).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 빌드 결과 APK 이름에 앱 이름과 버전을 넣는다. 예: BookLauncher-1.5.0-release.apk
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "BookLauncher-${versionName}-${buildType.name}.apk"
        }
    }

    // 쓰지 않는 Kotlin 메타 정보(리플렉션용)는 APK 에 넣지 않는다.
    packaging {
        resources.excludes += setOf("kotlin/**", "kotlin-tooling-metadata.json")
    }

    lint {
        disable += "QueryAllPackagesPermission"
    }

    // 단위 테스트(app/src/test): 회차 계산은 JVM 에서, DB 마이그레이션은 Robolectric(안드로이드 SQLite)으로 기기 없이 돌린다.
    // ./gradlew testDebugUnitTest
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

// 이 포크는 새 패키지(com.woody.booklauncher)로 시작한다. 릴리스도 디버그 키로 서명한다.
// 나중에 배포용 키를 만들면 signingConfigs 에 릴리스 키를 넣고 release 에 지정한다.

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// 릴리스는 Kotlin 의 널 검사 호출(매개변수 이름 문자열과 함께 모든 공개 함수 앞에 들어간다)을 빼서 dex 를 줄이고 호출마다 드는 검사를 없앤다.
// 디버그는 그대로 두어 널이 들어온 자리를 바로 알 수 있게 한다.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("Release")) {
        compilerOptions.freeCompilerArgs.addAll("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
    }
}
