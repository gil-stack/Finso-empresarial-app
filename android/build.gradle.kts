// Los plugins van en el classpath de la raíz para que Kotlin y el plugin de
// Android compartan cargador de clases. El de Android solo se baja si se arma :app.
buildscript {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.gradle.kotlin)
        classpath(libs.gradle.kotlin.serialization)
        classpath(libs.gradle.kotlin.compose)
        if (providers.gradleProperty("finso.soloNucleo").orNull != "true") {
            classpath(libs.gradle.agp)
        }
    }
}
