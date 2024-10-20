import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    `maven-publish`
}

android {
    namespace = "dev.nordix.zsi"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
        version = getVersionFromProperties()
    }

    buildOutputs.all {
        val variantOutputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        val variantName: String = variantOutputImpl.name
        val outputFileName = buildString {
            val name = listOfNotNull(
                project.name,
                if (variantName == "release") null else variantName,
                project.version
            )
                .joinToString("-")
            append(name)
            append(".aar")
        }
        variantOutputImpl.outputFileName = outputFileName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}




kotlin {
    sourceSets.all {
        languageSettings.enableLanguageFeature("ExplicitBackingFields")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.timber)
    implementation(libs.bundles.kstatemachine)
    coreLibraryDesugaring(libs.desugar)
}

tasks.register("incrementVersion") {
    doLast {
        val props = Properties()
        val versionFile = file("version.properties")
        props.load(versionFile.inputStream())

        val versionParts = props.getProperty("version").split(".").map { it.toInt() }
        val newPatchVersion = versionParts[2] + 1
        val newVersion = "${versionParts[0]}.${versionParts[1]}.$newPatchVersion"

        props.setProperty("version", newVersion)
        versionFile.writeText("version=$newVersion\n")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "dev.nordix"
            artifactId = project.name as String
            version = project.version as String

            pom {
                name.set("ZSI")
                description.set("Zebra scanner integration library")
                url.set("https://nordix.dev")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("dev.nordix")
                        name.set("Valerii")
                        email.set("badtri.orchestra@gmail.com")
                    }
                }
            }
            afterEvaluate {
                from(components["release"])
            }
        }
    }

    repositories {
        maven {
            url = uri("${System.getenv("NEXUS_URL")}repository/maven-releases/")
            println(url.toString())
            credentials {
                username = System.getenv("NEXUS_USER")
                password = System.getenv("NEXUS_PASSWORD")
                println(username.toString())
                println(password.toString())
            }
        }
    }
}


fun getVersionFromProperties(): String {
    val props = Properties()
    file("version.properties").inputStream().use { stream ->
        props.load(stream)
    }
    return props.getProperty("version") ?: "6.6.6"
}
