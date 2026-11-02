import java.util.Properties
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.woody.pebbledesk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.woody.pebbledesk"
        minSdk = 28
        targetSdk = 34
        versionCode = 34
        versionName = "1.7.3"
    }

    buildTypes {
        release {
            // 쓰지 않는 코드·리소스를 빼서 APK 를 줄이고 로딩을 빠르게 한다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 여기서는 디버그 키로 서명하고, 릴리스 키가 있으면 아래 releaseKeys 로 다시 서명한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 빌드 결과 APK 이름에 앱 이름과 버전을 넣는다. 예: PebbleDesk-1.5.0-release.apk
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "PebbleDesk-${versionName}-${buildType.name}.apk"
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

// 릴리스 키로 다시 서명: 1.4.2 까지는 디버그 키(~/.android/debug.keystore)로 서명했다.
// 이미 깐 사람이 덮어 설치할 수 있게 디버그 키 → 릴리스 키 교체 이력(lineage)을 APK 서명 v3 에 넣는다(minSdk 28 이라 모든 기기가 v3 를 본다).
// 서명에는 릴리스 키만 쓴다(디버그 키는 이력을 만들 때 한 번 썼다).
// 키 폴더(기본 ~/Dev/pebble-desk-keys, -Ppebbledesk.keys=… 로 바꿈)의 keystore.properties·pebbledesk.lineage 를 쓴다. 키 폴더가 없으면
// 디버그 빌드는 디버그 키 그대로, 릴리스 빌드는 실패한다(새 키로 넘어간 사람이 깔 수 없는 APK 를 만들지 않게. -Ppebbledesk.debugSignedRelease=true 면 만든다).
// 디버그 빌드도 같은 키로 서명해야 릴리스 위에 덮어 깔린다(이력에서 예전 키로 되돌리기를 막아 둠).
// APK 를 만드는 package 태스크 끝에서 서명해 assemble·install·Android Studio 실행이 모두 같은 APK 를 쓴다.
val releaseKeys = file(
    providers.gradleProperty("pebbledesk.keys").orNull ?: "${System.getProperty("user.home")}/Dev/pebble-desk-keys"
)
val releaseKeystore = File(releaseKeys, "keystore.properties")
val releaseLineage = File(releaseKeys, "pebbledesk.lineage")
// 이력의 두 인증서(공개 값): 1.4.2 까지 쓴 디버그 키 → 그 뒤의 릴리스 키. 서명한 APK 가 이대로인지 확인한다.
val debugCert = "2c2aea6ddc98d9b70a00ba14c69f60d89e0e37a650fdc0d88956d16f42c7ed8c"
val releaseCert = "cd2473b12d597045716ad39553f81a1205b25ee7cb6c6386c0bddc1db6dcfcdf"
if (releaseKeystore.isFile) {
    val props = Properties().apply { releaseKeystore.inputStream().use { load(it) } }
    fun prop(name: String): String = requireNotNull(props.getProperty(name)) { "keystore.properties 에 $name 이 없다" }
    // 상대 경로는 키 폴더 기준
    val storeFile = releaseKeys.resolve(prop("storeFile"))
    val apksignerPath = androidComponents.sdkComponents.sdkDirectory.map {
        it.file("build-tools/${android.buildToolsVersion}/apksigner").asFile
    }

    /** apksigner 를 돌려 출력(오류 포함)을 돌려준다. 실패하거나 2분이 넘으면 빌드 실패(데몬에서 돌아 콘솔에 보이지 않으므로 출력을 메시지에 넣는다. 비밀번호는 출력하지 않는다). */
    fun apksigner(workDir: File, vararg args: String): String {
        val log = File(workDir, "apksigner.log")
        val process = ProcessBuilder(apksignerPath.get().path, *args)
            .redirectErrorStream(true).redirectOutput(log)
            // 입력을 기다리지 않게(비밀번호를 물으면 바로 끝난다)
            .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
            .apply {
                // apksigner 는 셸 스크립트라 PATH 에서 java 를 찾는다: Gradle 이 도는 JDK 를 쓴다.
                val javaHome = System.getProperty("java.home")
                environment()["JAVA_HOME"] = javaHome
                environment()["PATH"] = "$javaHome/bin:" + environment()["PATH"]
                environment()["PEBBLE_STORE_PASS"] = prop("storePassword")
                environment()["PEBBLE_KEY_PASS"] = prop("keyPassword")
            }.start()
        val done = process.waitFor(2, TimeUnit.MINUTES)
        if (!done) process.destroyForcibly()
        val output = log.readText()
        check(done && process.exitValue() == 0) { "apksigner ${args[0]} 실패${if (done) "" else "(2분 넘김)"}\n$output" }
        return output
    }

    for (type in listOf("debug", "release")) {
        tasks.matching { it.name == "package${type.replaceFirstChar(Char::uppercase)}" }.configureEach {
            // 키·이력을 바꾸면 다시 만들어 서명한다.
            inputs.files(releaseKeystore, releaseLineage, storeFile).withPropertyName("releaseKeys")
            doLast {
                val dir = layout.buildDirectory.dir("outputs/apk/$type").get().asFile
                val apks = dir.listFiles { f -> f.extension == "apk" }.orEmpty()
                check(apks.isNotEmpty()) { "$dir 에 APK 가 없어 다시 서명하지 못했다" }
                for (apk in apks) {
                    apksigner(
                        temporaryDir, "sign",
                        "--ks", storeFile.path, "--ks-key-alias", prop("keyAlias"),
                        "--ks-pass", "env:PEBBLE_STORE_PASS", "--key-pass", "env:PEBBLE_KEY_PASS",
                        "--lineage", releaseLineage.path,
                        "--rotation-min-sdk-version", "28",
                        // v1·v2 는 이력의 가장 오래된 키(디버그 키)로만 서명할 수 있어 끄고 v3 만 둔다(안드로이드 9+ 는 v3 만으로 설치된다).
                        "--v1-signing-enabled", "false", "--v2-signing-enabled", "false",
                        "--v3-signing-enabled", "true", "--v4-signing-enabled", "false",
                        apk.path,
                    )
                    // 예전 서명으로 만든 v4 파일이 남지 않게
                    File("${apk.path}.idsig").delete()
                    // 서명 결과 확인: v3 만, 릴리스 키, 이력은 디버그 키 → 릴리스 키(데이터 이어 받기 허용·되돌리기 막음)
                    val verify = apksigner(temporaryDir, "verify", "-v", "--print-certs", apk.path)
                    val lineage = apksigner(temporaryDir, "lineage", "--in", apk.path, "--print-certs", "-v")
                    fun values(pattern: String, text: String = lineage) = Regex(pattern).findAll(text).map { it.groupValues[1] }.toList()
                    val schemes = listOf("v1" to "false", "v2" to "false", "v3" to "true").filter { (v, want) ->
                        values("Verified using $v scheme \\([^)]*\\): (\\w+)", verify) != listOf(want)
                    }
                    check(schemes.isEmpty()) { "${apk.name} 서명 방식이 예상과 다르다(${schemes.joinToString { it.first }})\n$verify" }
                    // 서명한 키(apksigner 판마다 `Signer #1`·`V3.0 Signer` 로 다르게 적는다)
                    check(values("certificate SHA-256 digest: (\\w+)", verify).let { it.isNotEmpty() && it.all { c -> c == releaseCert } }) {
                        "${apk.name} 이 릴리스 키로 서명되지 않았다\n$verify"
                    }
                    check(values("lineage certificate SHA-256 digest: (\\w+)") == listOf(debugCert, releaseCert)) { "${apk.name} 교체 이력이 예상과 다르다\n$lineage" }
                    check(values("Has installed data capability\\s*: (\\w+)") == listOf("true", "true")) { "${apk.name} 이력이 데이터 이어 받기를 막는다\n$lineage" }
                    check(values("Has rollback capability\\s*: (\\w+)") == listOf("false", "false")) { "${apk.name} 이력이 예전 키로 되돌리기를 허용한다\n$lineage" }
                }
            }
        }
    }
} else if (providers.gradleProperty("pebbledesk.debugSignedRelease").orNull != "true") {
    tasks.matching { it.name == "packageRelease" }.configureEach {
        doFirst {
            throw GradleException(
                "릴리스 키가 없다($releaseKeystore). 1.4.2 다음 판을 깐 사람은 디버그 키로 서명한 APK 를 깔 수 없다. " +
                    "디버그 키로 만들려면 -Ppebbledesk.debugSignedRelease=true"
            )
        }
    }
}

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
