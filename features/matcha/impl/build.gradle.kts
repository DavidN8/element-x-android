import extension.setupDependencyInjection
import extension.testCommonDependencies

/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
plugins {
    id("io.element.android-compose-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.features.matcha.impl"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

setupDependencyInjection()

// MatchaFixturesTest runs the conformance fixtures shared with the web and iOS clients: a changed fixture runs the tests again.
tasks.withType<Test>().configureEach {
    inputs.dir(rootDir.resolve("docs/matcha/fixtures")).withPropertyName("matchaFixtures").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    api(projects.features.matcha.api)
    implementation(projects.appconfig)
    implementation(projects.libraries.core)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.dateformatter.api)
    implementation(projects.libraries.featureflag.api)
    implementation(projects.libraries.uiStrings)
    implementation(projects.services.appnavstate.api)
    implementation(projects.features.networkmonitor.api)
    implementation(projects.libraries.androidutils)
    // For the formatting buttons' rich_text_editor_* descriptions, shared with the chat composer.
    implementation(projects.libraries.textcomposer.impl)
    implementation(libs.serialization.json)

    testCommonDependencies(libs, true)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.previewutils)
    testImplementation(projects.libraries.featureflag.test)
    testImplementation(projects.libraries.dateformatter.test)
    testImplementation(projects.features.matcha.test)
    testImplementation(projects.services.appnavstate.test)
    testImplementation(projects.features.networkmonitor.test)
}
