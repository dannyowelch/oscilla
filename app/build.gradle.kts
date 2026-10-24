import com.android.build.api.dsl.ApplicationExtension
import com.dannyowelch.oscilla.release.ReleaseVersion
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val gitTag = providers.gradleProperty("gitTag").orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?: System.getenv("GIT_TAG")?.trim()?.takeIf { it.isNotEmpty() }
val releaseVersion = gitTag?.let(ReleaseVersion::parse)

val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")?.trim()?.takeIf { it.isNotEmpty() }
val envKeystorePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
val envKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
val envKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")
val releaseSigningReady = keystorePath != null &&
    !envKeystorePassword.isNullOrEmpty() &&
    !envKeyAlias.isNullOrEmpty() &&
    !envKeyPassword.isNullOrEmpty()

configure<ApplicationExtension> {
    namespace = "com.dannyowelch.oscilla"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.dannyowelch.oscilla"
        minSdk = 29
        targetSdk {
            version = release(37)
        }
        versionCode = releaseVersion?.versionCode ?: 2_000
        versionName = releaseVersion?.versionName ?: "0.2.0"
    }

    if (releaseSigningReady) {
        val store = file(keystorePath!!)
        signingConfigs {
            create("release") {
                storeFile = store
                storePassword = envKeystorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    sourceSets.named("test") {
        kotlin.directories += layout.projectDirectory.dir("../buildSrc/src/main/kotlin").asFile.absolutePath
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
}

tasks.configureEach {
    if (name == "assembleRelease" || name == "packageRelease") {
        doFirst {
            if (!releaseSigningReady) {
                throw GradleException(
                    "Release APKs must be signed. Set SIGNING_KEYSTORE_PATH, " +
                        "SIGNING_KEYSTORE_PASSWORD, SIGNING_KEY_ALIAS, and SIGNING_KEY_PASSWORD. " +
                        "See README.md. Do not commit the keystore.",
                )
            }
        }
    }
}

tasks.register("printReleaseVersion") {
    val tag = providers.gradleProperty("gitTag")
    doLast {
        val parsed = ReleaseVersion.parse(
            tag.orNull ?: error("Pass -PgitTag=v1.2.3"),
        )
        println("version_name=${parsed.versionName}")
        println("version_code=${parsed.versionCode}")
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
