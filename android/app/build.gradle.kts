plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Identidad: vive en android/gradle.properties (un solo lugar).
val appId = providers.gradleProperty("finso.appId").get()
val appNombre = providers.gradleProperty("finso.appNombre").get()
val appVersion = providers.gradleProperty("finso.version").get()
// El CI pone el número de corrida: -Pfinso.build=123.
val appBuild = providers.gradleProperty("finso.build").orNull?.toIntOrNull() ?: 1
// API: simulada en debug y real en release, salvo que se pida otra con -Pfinso.api=…
val apiForzada = providers.gradleProperty("finso.api").orNull
val apiUrl = providers.gradleProperty("finso.apiUrl").orNull ?: "https://empresas.finsocial.app/movil/v1"
// Para las capturas de tienda se quita el bloqueo de capturas: -Pfinso.capturas=true.
val modoCapturas = providers.gradleProperty("finso.capturas").orNull == "true"
// Emulador del CI sin huella: llave en software, solo con datos simulados.
val huellaSimulada = providers.gradleProperty("finso.huellaSimulada").orNull == "true"

android {
    namespace = "mx.finsocial.empresarial"
    compileSdk = 36

    defaultConfig {
        applicationId = appId
        minSdk = 28
        targetSdk = 36
        versionCode = appBuild
        versionName = appVersion
        resValue("string", "app_nombre", appNombre)
        buildConfigField("String", "FINSO_API_URL", "\"$apiUrl\"")
        buildConfigField("boolean", "MODO_CAPTURAS", modoCapturas.toString())
        buildConfigField("boolean", "HUELLA_SIMULADA", huellaSimulada.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // La llave de subida llega del CI por variables de entorno (secretos del
        // repo). Sin ellas, release se firma con la de depuración para poder probar.
        create("subida") {
            val ruta = System.getenv("FINSO_KEYSTORE_RUTA")
            if (ruta != null) {
                storeFile = file(ruta)
                storePassword = System.getenv("FINSO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FINSO_KEY_ALIAS")
                keyPassword = System.getenv("FINSO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "FINSO_API", "\"${apiForzada ?: "simulada"}\"")
        }
        release {
            buildConfigField("String", "FINSO_API", "\"${apiForzada ?: "real"}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("FINSO_KEYSTORE_RUTA") != null) {
                signingConfigs.getByName("subida")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Roborazzi guarda las capturas de pantallas en build/capturas.
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "3g"
            }
        }
    }

    // Los pasos del recorrido se comparten entre las pruebas de pantallas
    // (Robolectric) y el recorrido en emulador.
    sourceSets {
        getByName("test").java.srcDir("src/compartido/kotlin")
        getByName("androidTest").java.srcDir("src/compartido/kotlin")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":nucleo"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.biometric)
    implementation(libs.fragment)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    // Recorrido en emulador (GitHub Actions).
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
