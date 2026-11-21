// App Android de Finso Empresarial (Kotlin + Jetpack Compose).
//
// Dos módulos:
//   :nucleo  Kotlin puro (sin Android): dinero, fechas, concepto SPEI, textos
//            firmados, modelos y cliente de la API. Se prueba en cualquier JVM.
//   :app     La app: pantallas en Compose, llave del teléfono y biometría.
//
// `-Pfinso.soloNucleo=true` arma solo :nucleo (sirve en máquinas sin el SDK
// de Android; el CI de GitHub arma todo).
pluginManagement {
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
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "finso-empresarial"
include(":nucleo")
if (providers.gradleProperty("finso.soloNucleo").orNull != "true") {
    include(":app")
}
