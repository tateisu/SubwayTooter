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
        google()
        mavenCentral()
        // alexzhirkevich/custom-qr-generator
        maven("https://jitpack.io")
    }
}

rootProject.name = "SubwayTooter"
// base utilities
include(":base")

// old libraries
include(":anko")
include(":colorpicker")

// apng decoder
include(":apng")
include(":apng_android")
include(":sample_apng")

// emoji images
include(":emoji")

// compose icons
include(":icon_material_symbols")

// main app
include(":app")
