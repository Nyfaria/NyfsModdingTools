plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("dev._100media.gradleutils") version "1.+"
}

group = "com.nyfaria"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}


val generateVersionIndex by tasks.registering {
    val versionsDir = file("versions")
    val outputFile = layout.buildDirectory.file("generated-resources/versions/index.txt")

    inputs.dir(versionsDir)
    outputs.file(outputFile)

    doLast {
        val outFile = outputFile.get().asFile
        outFile.parentFile.mkdirs()
        val versions = versionsDir.listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
        outFile.writeText(versions.joinToString("\n"))
    }
}

tasks.named<Copy>("processResources") {
    dependsOn(generateVersionIndex)
    from("versions") {
        into("versions")
    }
    from(layout.buildDirectory.dir("generated-resources"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

gradlePlugin {
    plugins {
        create("moddingTools") {
            id = "com.nyfaria.moddingtools"
            implementationClass = "com.nyfaria.moddingtools.NyfsModdingToolsPlugin"
        }
        create("modDependencies") {
            id = "com.nyfaria.moddingtools.dependencies"
            implementationClass = "com.nyfaria.moddingtools.ModDependencyPlugin"
        }
    }
}

publishing {
    repositories {
        maven(hundredMedia.getPublishing100MediaMaven())
    }
}
