import org.gradle.api.Project

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlinx.kover")
    id("pl.droidsonroids.pitest")
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

    implementation("androidx.datastore:datastore-preferences:1.3.0")
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
    pitest(project(":pitest-changed-lines"))
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Robolectric's Android 16 runtime reaches into JDK internals.
val robolectricJvmArgs = listOf(
    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
)

tasks.withType<Test>().configureEach {
    jvmArgs(robolectricJvmArgs)
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
        ":pitest-changed-lines:test",
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

// Mutation testing: scripts/mutants.py runs it on the lines a branch changed, or on one part of all
// of the app's code, through these properties. Only the unit tests, which run on Robolectric.
pitest {
    pitestVersion.set("1.30.1")
    targetClasses.set(providers.gradleProperty("mutateClasses").map { it.split(",") }.orElse(listOf("com.astrovm.gripmaxxer.*")))
    providers.gradleProperty("mutateTests").orNull?.let { targetTests.set(it.split(",")) }
    providers.gradleProperty("mutateLines").orNull?.let { features.set(listOf("+CHANGED(file[$it])")) }
    // Changed lines that give PIT nothing to change, like comments and imports, aren't a failure.
    failWhenNoMutations.set(false)
    threads.set(2)
    // A fresh test process takes some seconds to start Robolectric, more than PIT allows a test by default.
    timeoutConstInMillis.set(30_000)
    outputFormats.set(listOf("XML", "HTML"))
    timestampedReports.set(false)
    verbose.set(providers.gradleProperty("mutateVerbose").map { it.toBoolean() }.orElse(false))
    // Each mutant's classes are loaded again, and their adapters and stubs fill the JIT's small code
    // heap for them long before the Java heap fills, which PIT reports as a MEMORY_ERROR. One code
    // cache, unsplit, gives them the room.
    jvmArgs.set(robolectricJvmArgs + listOf("-Xmx3g", "-XX:-SegmentedCodeCache", "-XX:ReservedCodeCacheSize=512m"))
}

// After the plugin's own setup, which gives the tests only what they compile against.
afterEvaluate {
    tasks.withType<pl.droidsonroids.gradle.pitest.PitestTask>().configureEach {
        // What the unit tests run with, like the app's resources, which Robolectric reads. The app's own
        // classes come from the folders PIT mutates, not the jar the tests get, or PIT finds nothing to mutate.
        additionalClasspath.setFrom(
            mutableCodePaths,
            tasks.named<Test>("testDebugUnitTest").map { test -> test.classpath.filter { "runtime_app_classes_jar" !in it.path } },
        )
    }
}

// The plugin adds the old libraries Android once shipped, for tests run against android.jar. Ours run on
// Robolectric with the classpath above, so they're never used, and they set off security alerts.
configurations.matching { it.name == "pitestTestCompile" }.configureEach { withDependencies { clear() } }
