/*
 * Copyright (c) 2021.
 *
 * This file is part of xmlutil.
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

@file:OptIn(ExperimentalSerializationApi::class)

package nl.adaptivity.xml.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.*
import kotlin.test.*

fun String.normalizeXml() = replace(" />", "/>")
    .replace("\r\n", "\n")
    .replace("&gt;", ">")

fun JsonBuilder.defaultJsonTestConfiguration() {
    isLenient = true
    allowSpecialFloatingPointValues = true
    encodeDefaults = true
}


class TestCommon {

    @Test
    fun testEmitXmlDeclFull() {
        val expectedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <prefix:model xmlns:prefix="namespace" version="0.0.1" anAttribute="attrValue">
                <prefix:anElement>elementValue</prefix:anElement>
                <prefix:aBlankElement/>
            </prefix:model>""".trimIndent()

        val model = SampleModel1("0.0.1", "attrValue", "elementValue")

        val format = XML {
            xmlDeclMode = XmlDeclMode.Charset
            indentString = "    "
        }

        val serializedModel = format.encodeToString(SampleModel1.serializer(), model).normalizeXml().replace('\'', '"')

        assertEquals(expectedXml, serializedModel)
    }

    @Test
    fun testEmitXmlDeclMinimal() {
        val expectedXml = """
            <?xml version="1.0"?>
            <prefix:model xmlns:prefix="namespace" version="0.0.1" anAttribute="attrValue">
            <!--i--><prefix:anElement>elementValue</prefix:anElement>
            <!--i--><prefix:aBlankElement/>
            </prefix:model>""".trimIndent()

        val model = SampleModel1("0.0.1", "attrValue", "elementValue")

        val format = XML {
            xmlDeclMode = XmlDeclMode.Minimal
            indentString = "<!--i-->"
        }

        val serializedModel = format.encodeToString(SampleModel1.serializer(), model).normalizeXml().replace('\'', '"')

        assertEquals(expectedXml, serializedModel)
    }

    @Test
    fun deserialize_mixed_content_from_xml() {
        val contentText = "<tag>some text <b>some bold text<i>some bold italic text</i></b></tag>"
        val expectedObj = Tag(listOf("some text ", B("some bold text", I("some bold italic text"))))

        val xml = XML(Tag.module) {
            autoPolymorphic = true
        }
        val deserialized = xml.decodeFromString(Tag.serializer(), contentText)

        assertEquals(expectedObj, deserialized)
    }


    @Test
    fun serializeIntList() {
        val data = IntList(listOf(1,2,3,4))
        val expectedXml = "<IntList><values>1</values><values>2</values><values>3</values><values>4</values></IntList>"
        val xml = XML
        val serializer = IntList.serializer()
        val serialized = xml.encodeToString(serializer, data)
        assertEquals(expectedXml, serialized)

        val deserialized = xml.decodeFromString(serializer, serialized)
        assertEquals(data, deserialized)
    }

    @Test
    fun serialize_mixed_content_to_xml() {
        val contentText = "<tag>some text <b>some bold text<i>some bold italic text</i></b></tag>"
        val expectedObj = Tag(listOf("some text ", B("some bold text", I("some bold italic text"))))

        val xml = XML(Tag.module) {
            indentString = ""
            autoPolymorphic = true
        }

        val serialized = xml.encodeToString(Tag.serializer(), expectedObj)
        assertEquals(contentText, serialized)
    }

    @Serializable
    data class IntList(val values: List<Int>)

    @Serializable
    @XmlSerialName("model", "namespace", "prefix")
    data class SampleModel1(
        val version: String,
        val anAttribute: String,
        @XmlElement(true)
        val anElement: String,
        val aBlankElement: Unit? = Unit
                           )

    @Serializable
    @SerialName("b")
    internal data class B(
        @XmlValue(true)
        val data: List<@Polymorphic Any>
                         ) {
        constructor(vararg data: Any): this(data.toList())

    }

    @Serializable
    @SerialName("tag")
    internal data class Tag(
        @XmlValue(true)
        val data: List<@Polymorphic Any>
                           ) {

        constructor(vararg data: Any): this(data.toList())

        companion object {
            val module = SerializersModule {
                polymorphic(Any::class) {
                    subclass(String::class)
                    subclass(B::class)
                    subclass(I::class)
                }
            }
        }

    }

    @Serializable
    @SerialName("i")
    internal data class I(
        @XmlValue(true)
        val data: List<@Polymorphic Any>
                         ) {
        constructor(vararg data: Any): this(data.toList())

    }

}
