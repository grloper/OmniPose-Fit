// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Update AGP version to 8.2.0 which supports compileSdk 34
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
}

// Remove buildscript and allprojects blocks - these should go in settings.gradle.kts