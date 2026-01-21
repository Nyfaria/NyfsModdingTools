// Version catalog project - no build configuration needed

plugins {
    `version-catalog`
    `maven-publish`
}

group = "com.nyfaria"
version = "1.0.2"

catalog {
    versionCatalog {
        from(files("gradle/libs.versions.toml"))
    }
}

val minecraftVersion: String by project

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["versionCatalog"])
            artifactId = "versions-catalog-$minecraftVersion"
        }
    }
}
