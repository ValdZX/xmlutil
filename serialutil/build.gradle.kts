/*
 * Copyright (c) 2018.
 *
 * This file is part of XmlUtil.
 *
 * This file is licenced to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You should have received a copy of the license with the source distribution.
 * Alternatively, you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

@file:Suppress("PropertyName")

import com.jfrog.bintray.gradle.BintrayExtension
import net.devrieze.gradle.ext.fixBintrayModuleUpload
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("maven-publish")
    id("com.jfrog.bintray")
    idea
}

val xmlutil_serial_version: String by project
val xmlutil_core_version: String by project
val xmlutil_util_version: String by project
val xmlutil_versiondesc: String by project

base {
    archivesBaseName = "serialutil"
    version = xmlutil_util_version
}

val serializationVersion: String by project

val kotlin_version: String by project

val androidAttribute = Attribute.of("net.devrieze.android", Boolean::class.javaObjectType)

val moduleName = "net.devrieze.serialutil"

kotlin {
    targets {
        jvm {
            attributes {
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                attribute(androidAttribute, false)
            }
            compilations.all {
                tasks.named<KotlinCompile>(compileKotlinTaskName) {
                    kotlinOptions {
                        jvmTarget = "1.8"
                    }
                }
                tasks.named<Jar>("jvmJar") {
                    manifest {
                        attributes("Automatic-Module-Name" to moduleName)
                    }
                }
            }
        }
        jvm("android") {
            attributes {
                attribute(androidAttribute, true)
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 6)
                attribute(KotlinPlatformType.attribute, KotlinPlatformType.androidJvm)
            }
            compilations.all {
                kotlinOptions {
                    jvmTarget = "1.6"
                }
            }
        }
        js(BOTH) {
            browser()
            nodejs()
            compilations.all {
                kotlinOptions {
                    sourceMap = true
                    sourceMapEmbedSources = "always"
                    suppressWarnings = false
                    verbose = true
                    metaInfo = true
                    moduleKind = "umd"
                    main = "call"
                }
            }
        }

    }
    targets.forEach { target ->
        target.compilations.all {
            kotlinOptions {
                languageVersion = "1.4"
                apiVersion = "1.4"
            }
        }
        target.mavenPublication {
            groupId = "net.devrieze.serialutil"
            version = xmlutil_util_version
        }
    }

    @Suppress("UNUSED_VARIABLE")
    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
            }
        }
        val javaShared by creating {
            dependsOn(commonMain)
        }

        val jvmMain by getting {
            dependsOn(javaShared)
        }

        val androidMain by getting {
            dependsOn(javaShared)
        }

        all {
            languageSettings.apply {
                useExperimentalAnnotation("kotlin.RequiresOptIn")
            }
        }

    }

}

components.forEach { component ->

    logger.lifecycle("Found component ${component.name} of type: ${component.javaClass} (isAdhoc:${component is AdhocComponentWithVariants})")
}

repositories {
    jcenter()
    maven { setUrl("https://dl.bintray.com/kotlin/kotlin-eap") }
}

publishing.publications.getByName<MavenPublication>("kotlinMultiplatform") {
    logger.lifecycle("Updating kotlinMultiplatform publication from $groupId:$artifactId to net.devrieze.serialutil:serialutil")
    groupId = "net.devrieze.serialutil"
    artifactId = "serialutil"
}

publishing.publications.getByName<MavenPublication>("metadata") {
    logger.lifecycle("Updating $name publication from $groupId:$artifactId to net.devrieze.serialutil:serialutil-common")
    artifactId = "serialutil-common"
}

extensions.configure<BintrayExtension>("bintray") {
    if (rootProject.hasProperty("bintrayUser")) {
        user = rootProject.property("bintrayUser") as String?
        key = rootProject.property("bintrayApiKey") as String?
    }

    val pubs = publishing.publications
//        .filter { it.name != "metadata" }
        .map { it.name }
        .apply { forEach { logger.lifecycle("Registering publication \"$it\" to Bintray") } }
        .toTypedArray()


    setPublications(*pubs)

    pkg(closureOf<BintrayExtension.PackageConfig> {
        repo = "maven"
        name = "net.devrieze.serialutil:serialutil"
        userOrg = "pdvrieze"
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/pdvrieze/xmlutil.git"

        version.apply {
            name = xmlutil_util_version
            desc = xmlutil_versiondesc
            released = Date().toString()
            vcsTag = "v$xmlutil_serial_version"
        }
    })

}

fixBintrayModuleUpload()

idea {
    module {
        name = "xmlutil-serialutil"
    }
}
