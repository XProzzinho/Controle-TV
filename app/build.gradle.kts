plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.geanalves.controletv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.geanalves.controletv"
        minSdk = 26
        targetSdk = 34
        // Cada compilação no GitHub ganha um número maior, para o celular aceitar a atualização.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    signingConfigs {
        // Chave fixa para que as atualizações instalem por cima da versão anterior.
        create("fixa") {
            storeFile = file("controle-tv.jks")
            storePassword = "controletv"
            keyAlias = "controle"
            keyPassword = "controletv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixa")
        }
        debug {
            signingConfig = signingConfigs.getByName("fixa")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/**")
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Fala com a TV pelo Wi-Fi usando a depuração ADB (igual ao computador faria)
    implementation("dev.mobile:dadb:1.2.10")
}
