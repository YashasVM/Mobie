pluginManagement {
    // LiteRT-LM 0.18.0 contains Kotlin 2.4 metadata. Keep the narrow, official
    // R8 override rather than migrating the application to AGP 9.
    // https://developer.android.com/build/kotlin-support
    buildscript {
        repositories {
            google()
            mavenCentral()
            // Official R8 stable archive; 9.1.29 is not mirrored on Google Maven yet.
            maven {
                url = uri("https://storage.googleapis.com/r8-releases/raw")
                content { includeModule("com.android.tools", "r8") }
            }
        }
        dependencies {
            classpath("com.android.tools:r8:9.1.29")
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Mobie"
include(":app")
