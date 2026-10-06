plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A chave de assinatura chega pelo segredo LEITOR_KEYSTORE do GitHub (nunca fica no repositório).
// Assinar sempre com a mesma chave é o que permite instalar versões novas por cima da antiga.
val chave = file("leitor.jks")
val numero = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "app.rodagem.leitor"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.rodagem.leitor"
        minSdk = 26
        targetSdk = 34
        versionCode = numero
        versionName = "0.1.$numero"
    }

    signingConfigs {
        create("leitor") {
            if (chave.exists()) {
                storeFile = chave
                storePassword = "rodagemleitor"
                keyAlias = "leitor"
                keyPassword = "rodagemleitor"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (chave.exists()) signingConfigs.getByName("leitor") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
}
