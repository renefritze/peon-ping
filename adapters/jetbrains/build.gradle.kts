import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.peonping"
version = "0.2.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2024.1")
    type.set("IC")
    plugins.set(emptyList())
}

kotlin {
    val currentJava = JavaVersion.current().majorVersion.toIntOrNull() ?: 17
    // Use an installed JDK when possible (e.g., JDK 22), but keep bytecode target at 17.
    jvmToolchain(maxOf(17, currentJava))
}

tasks {
    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("253.*")
    }

    withType<JavaCompile>().configureEach {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.release.set(17)
    }

    withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "17"
    }
}
