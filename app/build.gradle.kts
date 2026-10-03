import org.gradle.api.Project

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlinx.kover")
}

fun Project.gitOutput(vararg args: String): String? {
    return try {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (process.waitFor() == 0) output.ifEmpty { null } else null
    } catch (_: Exception) {
        null
    }
}

val latestTagRef = project.gitOutput("describe", "--tags", "--abbrev=0")
val latestTagName = latestTagRef?.removePrefix("v")
val commitCount = project.gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
val commitsSinceTag = latestTagRef?.let { project.gitOutput("rev-list", "--count", "$it..HEAD")?.toIntOrNull() }

val derivedVersionName = when {
    latestTagName == null -> "0.0.0-dev"
    commitsSinceTag == null || commitsSinceTag == 0 -> latestTagName
    else -> "$latestTagName-dev.$commitsSinceTag"
}

android {
    namespace = "com.astrovm.gripmaxxer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.astrovm.gripmaxxer"
        minSdk = 26
        targetSdk = 36
        versionCode = commitCount
        versionName = derivedVersionName

        // Phones are ARM. Skipping x86 drops about 40 MB of ML Kit native code.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kover {
    reports {
        filters {
            excludes {
                // Generated code: Room's DAO and database implementations, and BuildConfig.
                classes(
                    "com.astrovm.gripmaxxer.data.WorkoutDao_Impl*",
                    "com.astrovm.gripmaxxer.data.GripDatabase_Impl*",
                    "com.astrovm.gripmaxxer.BuildConfig",
                )
            }
        }
        variant("debug") {
            verify {
                rule {
                    minBound(100)
                }
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    testImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")

    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.mockk:mockk:1.14.11")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test>().configureEach {
    // Robolectric's Android 16 runtime reaches into JDK internals.
    jvmArgs(
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Keep local validation, CI and release builds on the same checks.
tasks.register("ci") {
    group = "verification"
    description = "Tests, 100% line coverage, lint, and debug and release APKs."
    dependsOn(
        "testDebugUnitTest",
        "koverLogDebug",
        "koverXmlReportDebug",
        "koverVerifyDebug",
        "lintDebug",
        "assembleDebug",
        "assembleRelease",
    )
}

tasks.named("check") {
    dependsOn("koverVerifyDebug")
}
