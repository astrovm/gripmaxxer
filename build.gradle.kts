buildscript {
    dependencies {
        // Pin patched versions of transitive build-tool dependencies pulled in by the Android Gradle Plugin.
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.freemarker:freemarker:2.3.35")
            classpath("org.bitbucket.b_c:jose4j:0.9.7")
            classpath("org.jdom:jdom2:2.0.6.1")
            classpath("org.apache.commons:commons-lang3:3.21.0")
            // GHSA-xxph-c9ww-hj94: AGP 9.5.0 still pulls guava 33.4.0.
            classpath("com.google.guava:guava:33.7.2-jre")
        }
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.13" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.11" apply false
    id("pl.droidsonroids.pitest") version "0.3.0" apply false
}
