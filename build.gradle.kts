buildscript {
    dependencies {
        // AGP 9 uses built-in Kotlin; this pins the stable Kotlin Gradle plugin version.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
}
