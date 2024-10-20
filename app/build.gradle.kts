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

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "dev.nordix.zsi"
            artifactId = "zsi"
            version = getVersionFromProperties()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Zebra scanner integration library")
                description.set("Integrates Zebra scanner functionality")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set(System.getenv("CI_COMMIT_AUTHOR"))
                        name.set(System.getenv("CI_COMMIT_AUTHOR_NAME"))
                        email.set(System.getenv("CI_COMMIT_AUTHOR_EMAIL"))
                    }
                }
                scm {
                    connection.set("scm:git:${System.getenv("CI_REPOSITORY_URL")}")
                    developerConnection.set("scm:git:${System.getenv("CI_REPOSITORY_URL")}")
                    url.set(System.getenv("CI_PROJECT_URL"))
                }
            }
        }
    }

    repositories {
        maven {
            url = uri("http://nexus/repository/maven-releases/")
            credentials {
                username = System.getenv("NEXUS_USERNAME")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
    }

}

tasks.register("publishToNexus") {
    dependsOn("publish")
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

tasks.named("publishToNexus") {
    finalizedBy("incrementVersion", "createGitTag")
}

fun getVersionFromProperties(): String {
    val props = Properties()
    file("version.properties").inputStream().use { stream ->
        props.load(stream)
    }
    return props.getProperty("version") ?: "6.6.6"
}
