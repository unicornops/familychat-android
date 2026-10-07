// Copyright 2026 Unicorn Operations Ltd.
//
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
// Please see LICENSE files in the repository root for full details.

// Release workflow only (#7): applies the CycloneDX plugin to :app to list the dependencies of the F-Droid release
// build, without adding the plugin to the app's own build.
initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.cyclonedx:cyclonedx-gradle-plugin:3.5.0")
    }
}

allprojects {
    if (path == ":app") {
        apply<org.cyclonedx.gradle.CyclonedxPlugin>()
        tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
            includeConfigs.set(listOf("fdroidReleaseRuntimeClasspath"))
            componentName.set("familychat-android")
            jsonOutput.set(layout.buildDirectory.file("reports/cyclonedx/familychat-fdroid-release.cdx.json"))
            xmlOutput.unsetConvention()
        }
    }
}
