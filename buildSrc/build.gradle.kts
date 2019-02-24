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

import java.util.Properties
import java.io.FileInputStream

plugins {
    `kotlin-dsl`
}

run {
    val properties = Properties()
    FileInputStream(file("../gradle.properties")).use { input ->
        properties.load(input)
    }
    for(key in properties.stringPropertyNames()) {
        ext[key]=properties[key]
    }
}

val bintrayVersion: String by project
val kotlin_version: String by project

dependencies {
    compileOnly("com.jfrog.bintray.gradle:gradle-bintray-plugin:$bintrayVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
}

repositories {
    mavenLocal()
    jcenter()
}
