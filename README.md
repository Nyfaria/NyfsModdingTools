# NyfsVersionsCatalog
Versions Catalog for Nyfaria's Mods

## Overview
This repository provides a centralized Gradle version catalog for managing dependencies across Nyfaria's mods. Using Gradle's version catalog feature provides type-safe dependency management, consistent versioning, and easier maintenance.

## Usage

### 1. Add the Version Catalog to Your Project

In your `settings.gradle` or `settings.gradle.kts`, reference this catalog:

#### Option A: From a Local Copy
```groovy
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            from(files("path/to/gradle/libs.versions.toml"))
        }
    }
}
```

#### Option B: From GitHub (recommended for published catalogs)
```groovy
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            from("com.github.nyfaria:versions-catalog:1.0.0")
        }
    }
}
```

### 2. Use Dependencies in Your Build Scripts

Once configured, you can reference dependencies using type-safe accessors:

#### In `build.gradle`:
```groovy
dependencies {
    // Reference individual libraries
    implementation libs.some.library
    
    // Reference bundles
    implementation libs.bundles.common
}

plugins {
    // Reference plugins
    alias(libs.plugins.forge.gradle)
}
```

#### In `build.gradle.kts`:
```kotlin
dependencies {
    // Reference individual libraries
    implementation(libs.some.library)
    
    // Reference bundles
    implementation(libs.bundles.common)
}

plugins {
    // Reference plugins
    alias(libs.plugins.forge.gradle)
}
```

### 3. Defining Dependencies

Edit `gradle/libs.versions.toml` to add your dependencies:

```toml
[versions]
minecraft = "1.20.1"
forge = "47.1.0"

[libraries]
minecraft-forge = { group = "net.minecraftforge", name = "forge", version.ref = "forge" }

[bundles]
common = ["minecraft-forge"]

[plugins]
forge-gradle = { id = "net.minecraftforge.gradle", version = "6.0.+" }
```

## Documentation

For more information on Gradle version catalogs, see:
- [Official Gradle Documentation](https://docs.gradle.org/current/userguide/version_catalogs.html)
- [Migrate to Version Catalogs](https://developer.android.com/build/migrate-to-catalogs)
