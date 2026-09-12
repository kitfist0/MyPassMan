import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.jlleitschuh.ktlint)
}

// Read via the Provider API (not a plain File/InputStream read) so the Configuration
// Cache tracks local.properties as an input and correctly invalidates when it changes.
val localProperties =
    Properties().apply {
        providers
            .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
            .asText
            .orNull
            ?.let { load(it.byteInputStream()) }
    }

val releaseProperties =
    Properties().apply {
        providers
            .fileContents(rootProject.layout.projectDirectory.file("cert/release.properties"))
            .asText
            .orNull
            ?.let { load(it.byteInputStream()) }
    }

/** Shared output name for both the per-variant APK and its AAB counterpart, e.g. "MyPassMan-1.0-release.apk". */
fun artifactFileName(
    versionName: String?,
    variantName: String,
    extension: String,
): String = "MyPassMan-$versionName-$variantName.$extension"

android {
    namespace = "my.passman"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        applicationId = "my.passman"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "YANDEX_CLIENT_ID",
            "\"${localProperties.getProperty("YANDEX_CLIENT_ID", "")}\"",
        )
        buildConfigField(
            "String",
            "YANDEX_REDIRECT_URI",
            "\"${localProperties.getProperty("YANDEX_REDIRECT_URI", "")}\"",
        )
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("cert/release.keystore")
            storePassword = releaseProperties.getProperty("store_password")
            keyAlias = releaseProperties.getProperty("alias")
            keyPassword = releaseProperties.getProperty("key_password")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes +=
                setOf(
                    "META-INF/INDEX.LIST",
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md",
                )
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(artifactFileName(android.defaultConfig.versionName, variant.name, "apk"))
        }
    }
}

// variant.outputs (above) only renames APKs — bundleXxx's .aab has no equivalent
// "outputFileName" hook. AGP tasks further down the graph (e.g. the IDE bundle model
// listing) depend on that .aab staying at its original fixed path, so this adds a
// same-named copy alongside it rather than renaming/deleting the original.
// Runs in afterEvaluate: bundleDebug/bundleRelease aren't registered yet while
// androidComponents.onVariants above is still executing.
afterEvaluate {
    // Both captured into plain, config-cache-safe values before entering doLast below:
    // referencing `android` or the bare `layout` extension from inside a task action would
    // drag the whole Project into the task's captured state, which the Configuration Cache
    // refuses to serialize.
    val versionName = android.defaultConfig.versionName
    val buildDir = layout.buildDirectory
    listOf("debug", "release").forEach { variantName ->
        val capitalizedName = variantName.replaceFirstChar { it.uppercase() }
        val bundleDirProvider = buildDir.dir("outputs/bundle/$variantName")
        // Resolved to a plain String here, outside doLast: calling a script-level function
        // (like artifactFileName) from inside a task action captures the whole script object,
        // which the Configuration Cache refuses to serialize.
        val targetFileName = artifactFileName(versionName, variantName, "aab")
        tasks.named("bundle$capitalizedName").configure {
            doLast {
                val bundleDir = bundleDirProvider.get().asFile
                bundleDir
                    .listFiles { file -> file.extension == "aab" && !file.name.startsWith("MyPassMan-") }
                    ?.firstOrNull()
                    ?.copyTo(File(bundleDir, targetFileName), overwrite = true)
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.google.api.services.drive)
    implementation(libs.google.http.client.gson)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
}
