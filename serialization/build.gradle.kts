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
//    id("com.moowork.node") version "1.3.1"
    idea
}

val xmlutil_serial_version: String by project
val xmlutil_core_version: String by project
val xmlutil_versiondesc: String by project

base {
    archivesBaseName = "xmlutil-serialization"
    version = xmlutil_serial_version
}

val serializationVersion: String by project
val jupiterVersion: String by project

val kotlin_version: String by project
val androidAttribute = Attribute.of("net.devrieze.android", Boolean::class.javaObjectType)

val moduleName = "net.devrieze.xmlutil.serialization"


kotlin {
    targets {
        val testTask = tasks.create("test") {
            group = "verification"
        }
        val cleanTestTask = tasks.create("cleanTest") {
            group = "verification"
        }
        jvm {
            attributes {
                attribute(androidAttribute, false)
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
            }
            compilations.all {
                tasks.named<KotlinCompile>(compileKotlinTaskName) {
                    kotlinOptions {
                        jvmTarget = "1.8"
                    }
                }
                tasks.named<Test>("${target.name}Test") {
                    useJUnitPlatform()
                    testTask.dependsOn(this)
                }
                cleanTestTask.dependsOn(tasks.getByName("clean${target.name[0].toUpperCase()}${target.name.substring(1)}Test"))
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
                attribute(KotlinPlatformType.attribute, KotlinPlatformType.androidJvm)
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 6)
            }
            compilations.all {
                kotlinOptions.jvmTarget = "1.6"
                tasks.getByName<Test>("${target.name}Test") {
                    useJUnitPlatform ()
                    testTask.dependsOn(this)
                }
                cleanTestTask.dependsOn(tasks.getByName("clean${target.name[0].toUpperCase()}${target.name.substring(1)}Test"))
            }
        }
        js(BOTH) {
            browser()
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
            groupId = "net.devrieze.xmlserialization"
            val shortTarget = artifactId.substringAfter("serialization-")
            artifactId = "xmlutil-serialization-${shortTarget}"
            version = xmlutil_serial_version
        }
    }


    sourceSets {

        val commonMain by getting {
            dependencies {
                api(project(":core"))

                api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":serialutil"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

                implementation(kotlin("test"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val javaShared by creating {
            dependsOn(commonMain)
        }

        val javaSharedTest by creating {
            dependsOn(javaShared)
            dependsOn(commonTest)
        }

        val jvmMain by getting {
            dependsOn(javaShared)
        }

        val jvmTest by getting {
            dependsOn(javaSharedTest)
            dependsOn(jvmMain)

            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")

                implementation("net.bytebuddy:byte-buddy:1.10.10")
                implementation("org.assertj:assertj-core:3.16.1")
                implementation("org.xmlunit:xmlunit-core:2.7.0")
                implementation("org.xmlunit:xmlunit-assertj:2.7.0")

                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
                implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
                runtimeOnly("com.fasterxml.woodstox:woodstox-core:5.0.3")

            }
        }

        val androidMain by getting {
            dependsOn(javaShared)

            dependencies {
                compileOnly("net.sf.kxml:kxml2:2.3.0")
            }
        }

        val androidTest by getting {
            dependsOn(javaSharedTest)
            dependsOn(androidMain)

            dependencies {
                implementation(kotlin("test-junit5"))
                runtimeOnly("net.sf.kxml:kxml2:2.3.0")

                implementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
                implementation("org.xmlunit:xmlunit-core:2.6.0")
                implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")

                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
            }
        }

        val jsMain by getting {
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        all {
            languageSettings.apply {
                useExperimentalAnnotation("kotlin.RequiresOptIn")
            }
        }
    }

}

repositories {
    jcenter()
    maven { setUrl("https://dl.bintray.com/kotlin/kotlin-eap") }
}

publishing.publications.named<MavenPublication>("kotlinMultiplatform") {
    logger.lifecycle("Updating kotlinMultiplatform publication from $groupId:$artifactId to net.devrieze.xmlserialization:xmlutil-serialization")
    groupId = "net.devrieze.xmlserialization"
    artifactId = "xmlutil-serialization"
}

publishing.publications.getByName<MavenPublication>("metadata") {
    logger.lifecycle("Updating $name publication from $groupId:$artifactId to net.devrieze.xmlserialization:xmlutil-serialization-common")
    artifactId = "xmlutil-serialization-common"
}

tasks.named("check") {
    dependsOn(tasks.named("test"))
}

tasks.withType<Test> {
    logger.lifecycle("Enabling xml reports on task ${project.name}:${name}")
    reports {
        junitXml.isEnabled = true
    }
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
        name = "net.devrieze.xmlserialization:xmlutil-serialization"
        userOrg = "pdvrieze"
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/pdvrieze/xmlutil.git"

        version.apply {
            name = xmlutil_serial_version
            desc = xmlutil_versiondesc
            released = Date().toString()
            vcsTag = "v$xmlutil_serial_version"
        }
    })

}

fixBintrayModuleUpload()

idea {
    this.module.name = "xmlutil-serialization"
}
