/*
 * Copyright (c) 2019.
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XMLConstants
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.XmlEvent
import nl.adaptivity.xmlutil.serialization.*
import nl.adaptivity.xmlutil.serialization.XML.Companion.decodeFromString
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy.XmlEncodeDefault
import nl.adaptivity.xmlutil.util.CompactFragment
import kotlin.test.*

private fun String.normalize() = replace(" />", "/>")
    .replace("\r\n", "\n")
    .replace("&gt;", ">")
    .replace(" ?>", "?>")

fun JsonBuilder.defaultJsonTestConfiguration() {
    isLenient = true
    allowSpecialFloatingPointValues = true
    encodeDefaults = true
}

class TestCommon {

    abstract class TestBase<T> constructor(
        val value: T,
        val serializer: KSerializer<T>,
        val serializersModule: SerializersModule = EmptySerializersModule,
        protected val baseXmlFormat: XML = XML(serializersModule) {
            policy = DefaultXmlSerializationPolicy(pedantic = true, encodeDefault = XmlEncodeDefault.ANNOTATED)
        },
        private val baseJsonFormat: Json = Json{
            defaultJsonTestConfiguration()
            this.serializersModule = serializersModule
        }
                                                                                      ) {
        abstract val expectedXML: String
        abstract val expectedJson: String

        fun serializeXml(): String = baseXmlFormat.encodeToString(serializer, value).normalize()

        fun serializeJson(): String = baseJsonFormat.encodeToString(serializer, value)

        @Test
        open fun testSerializeXml() {
            assertEquals(expectedXML, serializeXml())
        }

        @Test
        open fun testDeserializeXml() {
            assertEquals(value, baseXmlFormat.decodeFromString(serializer, expectedXML))
        }

        @Test
        open fun testSerializeJson() {
            assertEquals(expectedJson, serializeJson())
        }

        @Test
        open fun testDeserializeJson() {
            assertEquals(value, baseJsonFormat.decodeFromString(serializer, expectedJson))
        }

    }

    abstract class TestPolymorphicBase<T>(
        value: T,
        serializer: KSerializer<T>,
        serializersModule: SerializersModule,
        baseJsonFormat: Json = Json{
            defaultJsonTestConfiguration()
            this.serializersModule = serializersModule
        }
                                         ) :
        TestBase<T>(value, serializer, serializersModule, XML(serializersModule) { autoPolymorphic = true }, baseJsonFormat) {

        abstract val expectedNonAutoPolymorphicXML: String

        @Test
        fun nonAutoPolymorphic_serialization_should_work() {
            val serialized =
                XML(serializersModule = serializersModule) { autoPolymorphic = false }.encodeToString(serializer, value)
                    .normalize()
            assertEquals(expectedNonAutoPolymorphicXML, serialized)
        }

        @Test
        fun nonAutoPolymorphic_deserialization_should_work() {
            val actualValue = XML(serializersModule = serializersModule) { autoPolymorphic = false }
                .decodeFromString(serializer, expectedNonAutoPolymorphicXML)

            assertEquals(value, actualValue)
        }

    }

    class SimpleDataTest : TestBase<Address>(
        Address("10", "Downing Street", "London"),
        Address.serializer()
                                            ) {
        override val expectedXML: String =
            "<address houseNumber=\"10\" street=\"Downing Street\" city=\"London\" status=\"VALID\"/>"

        override val expectedJson: String =
            "{\"houseNumber\":\"10\",\"street\":\"Downing Street\",\"city\":\"London\",\"status\":\"VALID\"}"

        val unknownValues
            get() =
                "<address xml:lang=\"en\" houseNumber=\"10\" street=\"Downing Street\" city=\"London\" status=\"VALID\"/>"

        @Test
        fun deserialize_with_unused_attributes() {
            val e = assertFailsWith<UnknownXmlFieldException> {
                decodeFromString(serializer, unknownValues)
            }

            val expectedMsgStart = "Could not find a field for name {http://www.w3.org/XML/1998/namespace}lang\n" +
                    "  candidates: houseNumber, street, city, status at position "
            val msgSubstring = e.message?.let { it.substring(0, minOf(it.length, expectedMsgStart.length)) }

            assertEquals(expectedMsgStart, msgSubstring)
        }


        @Test
        fun deserialize_with_unused_attributes_and_custom_handler() {
            var ignoredName: QName? = null
            var ignoredKind: InputKind? = null
            val xml = XML {
                unknownChildHandler = { _, inputKind, name, _ ->
                    ignoredName = name
                    ignoredKind = inputKind
                }
            }
            assertEquals(value, xml.decodeFromString(serializer, unknownValues))
            assertEquals(QName(XMLConstants.XML_NS_URI, "lang", "xml"), ignoredName)
            assertEquals(InputKind.Attribute, ignoredKind)
        }

    }

    class ValueContainerTest : TestBase<ValueContainer>(
        ValueContainer("<foo&bar>"),
        ValueContainer.serializer()
                                                       ) {
        override val expectedXML: String = "<valueContainer>&lt;foo&amp;bar></valueContainer>"
        override val expectedJson: String = "{\"content\":\"<foo&bar>\"}"

        @Test
        fun testAlternativeXml() {
            val alternativeXml = "<valueContainer><![CDATA[<foo&]]>bar&gt;</valueContainer>"
            assertEquals(value, baseXmlFormat.decodeFromString(serializer, alternativeXml))
        }

        @Test
        fun testAlternativeXml2() {
            val alternativeXml = "<valueContainer>&lt;foo&amp;bar&gt;</valueContainer>"
            assertEquals(value, baseXmlFormat.decodeFromString(serializer, alternativeXml))
        }

    }

    /**
     * Class to test recursion issue #32
     */
    class RecursionTest : TestBase<RecursiveContainer>(
        RecursiveContainer(listOf(RecursiveContainer(listOf(RecursiveContainer(), RecursiveContainer())), RecursiveContainer())),
        RecursiveContainer.serializer()
                                                       ) {
        override val expectedXML: String = "<rec><rec><rec/><rec/></rec><rec/></rec>"
        override val expectedJson: String = "{\"values\":[{\"values\":[{\"values\":[]},{\"values\":[]}]},{\"values\":[]}]}"

    }

    class ListTest: TestBase<SimpleList>(
        SimpleList("1", "2", "3"),
        SimpleList.serializer()
                                        ) {
        override val expectedXML: String= "<l><values>1</values><values>2</values><values>3</values></l>"
        override val expectedJson: String = "{\"values\":[\"1\",\"2\",\"3\"]}"
    }

    class ValueContainerTestWithSpaces : TestBase<ValueContainer>(
        ValueContainer("    \nfoobar\n  "),
        ValueContainer.serializer()
                                                                 ) {
        override val expectedXML: String = "<valueContainer>    \nfoobar\n  </valueContainer>"
        override val expectedJson: String = "{\"content\":\"    \\nfoobar\\n  \"}"

        @Test
        fun testAlternativeXml() {
            val alternativeXml = "<valueContainer><![CDATA[    \nfoo]]>bar\n  </valueContainer>"
            assertEquals(value, baseXmlFormat.decodeFromString(serializer, alternativeXml))
        }

    }

    class MixedValueContainerTest : TestPolymorphicBase<MixedValueContainer>(
        MixedValueContainer(listOf("foo", Address("10", "Downing Street", "London"), "bar")),
        MixedValueContainer.serializer(),
        MixedValueContainer.module(),
        baseJsonFormat = Json {
            useArrayPolymorphism = true
            serializersModule = MixedValueContainer.module()
            encodeDefaults = true
        }
                                                                            ) {
        override val expectedXML: String =
            "<MixedValueContainer>foo<address houseNumber=\"10\" street=\"Downing Street\" city=\"London\" status=\"VALID\"/>bar</MixedValueContainer>"
        override val expectedNonAutoPolymorphicXML: String =
            "<MixedValueContainer><data type=\"kotlin.String\"><value>foo</value></data><data type=\".Address\"><value houseNumber=\"10\" street=\"Downing Street\" city=\"London\" status=\"VALID\"/></data><data type=\"kotlin.String\"><value>bar</value></data></MixedValueContainer>"
        override val expectedJson: String =
            "{\"data\":[[\"kotlin.String\",\"foo\"],[\"nl.adaptivity.xml.serialization.Address\",{\"houseNumber\":\"10\",\"street\":\"Downing Street\",\"city\":\"London\",\"status\":\"VALID\"}],[\"kotlin.String\",\"bar\"]]}"

    }

    class InvalidValueContainerTest {
        val format = XML()
        val data = InvalidValueContainer("foobar", Address("10", "Downing Street", "London"))
        val serializer = InvalidValueContainer.serializer()
        val invalidXML1: String =
            "<InvalidValueContainer><address houseNumber=\"10\" street=\"Downing Street\" city=\"London\" status=\"VALID\"/>foobar</InvalidValueContainer>"
        val invalidXML2: String =
            "<InvalidValueContainer>foobar<address houseNumber=\"10\" street=\"Downing Street\" city=\"London\" status=\"VALID\"/></InvalidValueContainer>"
        val invalidXML3: String =
            "<InvalidValueContainer>foo<address houseNumber=\"10\" street=\"Downing Street\" city=\"London\" status=\"VALID\"/>bar</InvalidValueContainer>"

        @Test
        fun testSerializeInvalid() {
            val e = assertFails {
                format.encodeToString(serializer, data)
            }
            assertTrue(e is XmlSerialException)
            assertTrue(e.message?.contains("@XmlValue") == true)
        }

        @Test
        fun testDeserializeInvalid1() {
            val e = assertFails {
                format.decodeFromString(serializer, invalidXML1)
            }
            assertTrue(e is XmlSerialException)
            assertTrue(e.message?.contains("@XmlValue") == true)
        }

        @Test
        fun testDeserializeInvalid2() {
            val e = assertFails {
                format.decodeFromString(serializer, invalidXML2)
            }
            assertTrue(e is XmlSerialException)
            assertTrue(e.message?.contains("@XmlValue") == true)
        }

        @Test
        fun testDeserializeInvalid3() {
            val e = assertFails {
                format.decodeFromString(serializer, invalidXML3)
            }
            assertTrue(e is XmlSerialException)
            assertTrue(e.message?.contains("@XmlValue") == true)
        }

    }

    class OptionalBooleanTest : TestBase<Location>(
        Location(Address("1600", "Pensylvania Avenue", "Washington DC")),
        Location.serializer()
                                                  ) {
        override val expectedXML: String =
            "<Location><address houseNumber=\"1600\" street=\"Pensylvania Avenue\" city=\"Washington DC\" status=\"VALID\"/></Location>"
        override val expectedJson: String =
            "{\"addres\":{\"houseNumber\":\"1600\",\"street\":\"Pensylvania Avenue\",\"city\":\"Washington DC\",\"status\":\"VALID\"},\"temperature\":NaN}"

        val noisyXml
            get() =
                "<Location><unexpected><address>Foo</address></unexpected><address houseNumber=\"1600\" street=\"Pensylvania Avenue\" city=\"Washington DC\" status=\"VALID\"/></Location>"

        fun fails_with_unexpected_child_tags() {
            val e = assertFailsWith<UnknownXmlFieldException> {
                decodeFromString(serializer, noisyXml)
            }
            assertEquals(
                "Could not find a field for name {http://www.w3.org/XML/1998/namespace}lang\n" +
                        "  candidates: houseNumber, street, city, status at position [row,col {unknown-source}]: [1,1]",
                e.message
                        )
        }


        @Test
        fun deserialize_with_unused_attributes_and_custom_handler() {
            var ignoredName: QName? = null
            var ignoredKind: InputKind? = null
            val xml = XML {
                unknownChildHandler = { _, inputKind, name, _ ->
                    ignoredName = name
                    ignoredKind = inputKind
                }
            }
            assertEquals(value, xml.decodeFromString(serializer, noisyXml))
            assertEquals(QName(XMLConstants.NULL_NS_URI, "unexpected", ""), ignoredName)
            assertEquals(InputKind.Element, ignoredKind)
        }

    }

    class SimpleClassWithNullableValueNONNULL : TestBase<NullableContainer>(
        NullableContainer("myBar"),
        NullableContainer.serializer()
                                                                           ) {
        override val expectedXML: String = "<p:NullableContainer xmlns:p=\"urn:myurn\" bar=\"myBar\"/>"
        override val expectedJson: String = "{\"bar\":\"myBar\"}"
    }

    class SimpleClassWithNullableValueNULL : TestBase<NullableContainer>(
        NullableContainer(),
        NullableContainer.serializer()
                                                                        ) {
        override val expectedXML: String = "<p:NullableContainer xmlns:p=\"urn:myurn\"/>"
        override val expectedJson: String = "{\"bar\":null}"
    }

    class ClassWithNullableUDValueNONNULL : TestBase<ContainerOfUserNullable>(
        ContainerOfUserNullable(SimpleUserType("foobar")),
        ContainerOfUserNullable.serializer()
                                                                             ) {
        override val expectedXML: String = "<ContainerOfUserNullable><SimpleUserType data=\"foobar\"/></ContainerOfUserNullable>"
        override val expectedJson: String = "{\"data\":{\"data\":\"foobar\"}}"
    }

    class ClassWithNullableUDValueNULL : TestBase<ContainerOfUserNullable>(
        ContainerOfUserNullable(null),
        ContainerOfUserNullable.serializer()
                                                                          ) {
        override val expectedXML: String = "<ContainerOfUserNullable/>"
        override val expectedJson: String = "{\"data\":null}"
    }

    class ASimpleBusiness : TestBase<Business>(
        Business("ABC Corp", Address("1", "ABC road", "ABCVille")),
        Business.serializer()
                                              ) {
        override val expectedXML: String =
            "<Business name=\"ABC Corp\"><headOffice houseNumber=\"1\" street=\"ABC road\" city=\"ABCVille\" status=\"VALID\"/></Business>"
        override val expectedJson: String =
            "{\"name\":\"ABC Corp\",\"headOffice\":{\"houseNumber\":\"1\",\"street\":\"ABC road\",\"city\":\"ABCVille\",\"status\":\"VALID\"}}"
    }

    class AChamberOfCommerce : TestBase<Chamber>(
        Chamber(
            "hightech", listOf(
                Business("foo", null),
                Business("bar", null)
                              )
               ),
        Chamber.serializer()
                                                ) {
        override val expectedXML: String = "<chamber name=\"hightech\">" +
                "<member name=\"foo\"/>" +
                "<member name=\"bar\"/>" +
                "</chamber>"
        override val expectedJson: String =
            "{\"name\":\"hightech\",\"members\":[{\"name\":\"foo\",\"headOffice\":null},{\"name\":\"bar\",\"headOffice\":null}]}"
    }

    class AnEmptyChamber : TestBase<Chamber>(
        Chamber("lowtech", emptyList()),
        Chamber.serializer()
                                            ) {
        override val expectedXML: String = "<chamber name=\"lowtech\"/>"
        override val expectedJson: String = "{\"name\":\"lowtech\",\"members\":[]}"
    }

    class ACompactFragment : TestBase<CompactFragment>(
        CompactFragment(listOf(XmlEvent.NamespaceImpl("p", "urn:ns")), "<p:a>someA</p:a><b>someB</b>"),
        CompactFragment.serializer()
                                                      ) {
        override val expectedXML: String =
            "<compactFragment xmlns:p=\"urn:ns\"><p:a>someA</p:a><b>someB</b></compactFragment>"
        override val expectedJson: String =
            "{\"namespaces\":[{\"prefix\":\"p\",\"namespaceURI\":\"urn:ns\"}],\"content\":\"<p:a>someA</p:a><b>someB</b>\"}"
    }

    class ClassWithImplicitChildNamespace : TestBase<Namespaced>(
        Namespaced("foo", "bar", "bla", "lalala", "tada"),
        Namespaced.serializer()
                                                                ) {
        override val expectedXML: String =
            "<xo:namespaced xmlns:xo=\"http://example.org\" xmlns:p3=\"http://example.org/2\" p3:Elem3=\"bla\" elem4=\"lalala\" xmlns=\"urn:foobar\" Elem5=\"tada\"><xo:elem1>foo</xo:elem1><p2:Elem2 xmlns:p2=\"urn:myurn\">bar</p2:Elem2></xo:namespaced>"
        val invalidXml =
            "<xo:namespaced xmlns:xo=\"http://example.org\" xmlns:p3=\"http://example.org/2\" p3:Elem3=\"bla\" elem4=\"lalala\" xmlns=\"urn:foobar\" Elem5=\"tada\"><elem1>foo</elem1><xo:elem2>bar</xo:elem2></xo:namespaced>"
        override val expectedJson: String = "{\"elem1\":\"foo\",\"elem2\":\"bar\",\"elem3\":\"bla\",\"elem4\":\"lalala\",\"elem5\":\"tada\"}"

        @Test
        fun invalidXmlDoesNotDeserialize() {
            assertFailsWith<UnknownXmlFieldException> {
                decodeFromString(serializer, invalidXml)
            }
        }
    }

    class AComplexElement : TestBase<Special>(
        Special(),
        Special.serializer()
                                             ) {
        override val expectedXML: String =
            """<localname xmlns="urn:namespace" paramA="valA"><paramb xmlns="urn:ns2">1</paramb><flags xmlns:f="urn:flag">""" +
                    "<f:flag>2</f:flag>" +
                    "<f:flag>3</f:flag>" +
                    "<f:flag>4</f:flag>" +
                    "<f:flag>5</f:flag>" +
                    "<f:flag>6</f:flag>" +
                    "</flags></localname>"
        override val expectedJson: String = "{\"paramA\":\"valA\",\"paramB\":1,\"flagValues\":[2,3,4,5,6]}"
    }

    class InvertedPropertyOrder : TestBase<Inverted>(
        Inverted("value2", 7),
        Inverted.serializer()
                                                    ) {
        override val expectedXML: String = """<Inverted arg="7"><elem>value2</elem></Inverted>"""
        override val expectedJson: String = "{\"elem\":\"value2\",\"arg\":7}"

        @Test
        fun noticeMissingChild() {
            val xml = "<Inverted arg='5'/>"
            assertFailsWith<SerializationException> {
                decodeFromString(serializer, xml)
            }
        }

        @Test
        fun noticeIncompleteSpecification() {
            val xml = "<Inverted arg='5' argx='4'><elem>v5</elem></Inverted>"
            assertFailsWith<UnknownXmlFieldException>("Could not find a field for name argx") {
                decodeFromString(serializer, xml)
            }

        }
    }

    class AClassWithPolymorhpicChild : TestPolymorphicBase<Container>(
        Container("lbl", ChildA("data")),
        Container.serializer(),
        baseModule
                                                                     ) {
        override val expectedXML: String
            get() = "<Container label=\"lbl\"><childA valueA=\"data\"/></Container>"
        override val expectedJson: String
            get() = "{\"label\":\"lbl\",\"member\":{\"type\":\"nl.adaptivity.xml.serialization.ChildA\",\"valueA\":\"data\"}}"
        override val expectedNonAutoPolymorphicXML: String get() = "<Container label=\"lbl\"><member type=\".ChildA\"><value valueA=\"data\"/></member></Container>"
    }

    class AClassWithMultipleChildren : TestPolymorphicBase<Container2>(
        Container2("name2", listOf(ChildA("data"), ChildB(1, 2, 3, "xxx"))),
        Container2.serializer(),
        baseModule
                                                                      ) {
        override val expectedXML: String
            get() = "<Container2 name=\"name2\"><childA valueA=\"data\"/><better a=\"1\" b=\"2\" c=\"3\" valueB=\"xxx\"/></Container2>"
        override val expectedNonAutoPolymorphicXML: String
            get() = expectedXML
        override val expectedJson: String
            get() = "{\"name\":\"name2\",\"children\":[{\"type\":\"nl.adaptivity.xml.serialization.ChildA\",\"valueA\":\"data\"},{\"type\":\"childBNameFromAnnotation\",\"a\":1,\"b\":2,\"c\":3,\"valueB\":\"xxx\"}]}"


    }

    class AClassWithXMLPolymorphicNullableChild : TestPolymorphicBase<Container4>(
        Container4("name2", ChildA("data")),
        Container4.serializer(),
        baseModule
                                                                                 ) {
        override val expectedXML: String
            get() = "<Container4 name=\"name2\"><childA valueA=\"data\"/></Container4>"
        override val expectedNonAutoPolymorphicXML: String
            get() = expectedXML
        override val expectedJson: String
            get() = "{\"name\":\"name2\",\"child\":{\"type\":\"nl.adaptivity.xml.serialization.ChildA\",\"valueA\":\"data\"}}"


    }

    class ASimplerClassWithUnspecifiedChildren : TestPolymorphicBase<Container3>(
        Container3("name2", listOf(ChildA("data"), ChildB(4, 5, 6, "xxx"), ChildA("yyy"))),
        Container3.serializer(),
        baseModule
                                                                                ) {
        override val expectedXML: String
            get() = "<container-3 xxx=\"name2\"><childA valueA=\"data\"/><childB a=\"4\" b=\"5\" c=\"6\" valueB=\"xxx\"/><childA valueA=\"yyy\"/></container-3>"
        override val expectedJson: String
            get() = "{\"xxx\":\"name2\",\"member\":[{\"type\":\"nl.adaptivity.xml.serialization.ChildA\",\"valueA\":\"data\"},{\"type\":\"childBNameFromAnnotation\",\"a\":4,\"b\":5,\"c\":6,\"valueB\":\"xxx\"},{\"type\":\"nl.adaptivity.xml.serialization.ChildA\",\"valueA\":\"yyy\"}]}"
        override val expectedNonAutoPolymorphicXML: String
            get() = "<container-3 xxx=\"name2\"><member type=\"nl.adaptivity.xml.serialization.ChildA\"><value valueA=\"data\"/></member><member type=\"childBNameFromAnnotation\"><value a=\"4\" b=\"5\" c=\"6\" valueB=\"xxx\"/></member><member type=\"nl.adaptivity.xml.serialization.ChildA\"><value valueA=\"yyy\"/></member></container-3>"
    }


    class CustomSerializedClass : TestBase<CustomContainer>(
        CustomContainer(Custom("foobar")),
        CustomContainer.serializer()
                                                           ) {

        override val expectedXML: String = "<CustomContainer elem=\"foobar\"/>"
        override val expectedJson: String = "{\"nonXmlElemName\":\"foobar\"}"

    }

    class AContainerWithSealedChild : TestBase<SealedSingle>(
        SealedSingle("mySealed", SealedA("a-data")),
        SealedSingle.serializer()
                                                            ) {
        override val expectedXML: String
            get() = "<SealedSingle name=\"mySealed\"><SealedA data=\"a-data\" extra=\"2\"/></SealedSingle>"
        override val expectedJson: String
            get() = "{\"name\":\"mySealed\",\"member\":{\"data\":\"a-data\",\"extra\":\"2\"}}"
    }

    class AContainerWithSealedChildren : TestPolymorphicBase<Sealed>(
        Sealed("mySealed", listOf(SealedA("a-data"), SealedB("b-data"))),
        Sealed.serializer(),
        EmptySerializersModule//sealedModule
                                                                    ) {
        override val expectedXML: String
            get() = "<Sealed name=\"mySealed\"><SealedA data=\"a-data\" extra=\"2\"/><SealedB_renamed main=\"b-data\" ext=\"0.5\"/></Sealed>"
        override val expectedJson: String
            get() = "{\"name\":\"mySealed\",\"members\":[{\"type\":\"nl.adaptivity.xml.serialization.SealedA\",\"data\":\"a-data\",\"extra\":\"2\"},{\"type\":\"nl.adaptivity.xml.serialization.SealedB\",\"main\":\"b-data\",\"ext\":0.5}]}"
        override val expectedNonAutoPolymorphicXML: String
            get() = "<Sealed name=\"mySealed\"><member type=\".SealedA\"><value data=\"a-data\" extra=\"2\"/></member><member type=\".SealedB\"><value main=\"b-data\" ext=\"0.5\"/></member></Sealed>"
    }

    class ComplexSealedTest : TestBase<ComplexSealedHolder>(
        ComplexSealedHolder("a", 1, 1.5f, OptionB1(5, 6, 7)),
        ComplexSealedHolder.serializer(),
        EmptySerializersModule,
        XML(XmlConfig(autoPolymorphic = true))
                                                           ) {
        override val expectedXML: String
            get() = "<ComplexSealedHolder a=\"a\" b=\"1\" c=\"1.5\"><OptionB1 g=\"5\" h=\"6\" i=\"7\"/></ComplexSealedHolder>"
        override val expectedJson: String
            get() = "{\"a\":\"a\",\"b\":1,\"c\":1.5,\"options\":{\"type\":\"nl.adaptivity.xml.serialization.OptionB1\",\"g\":5,\"h\":6,\"i\":7}}"
    }

    class NullableListTestWithElements : TestBase<NullList>(
        NullList(
            "A String", listOf(
                NullListElement("Another String1"),
                NullListElement("Another String2")
                              )
                ),
        NullList.serializer()
                                                           ) {
        override val expectedXML: String
            get() = "<Baz><Str>A String</Str><Bar><AnotherStr>Another String1</AnotherStr></Bar><Bar><AnotherStr>Another String2</AnotherStr></Bar></Baz>"
        override val expectedJson: String
            get() = "{\"Str\":\"A String\",\"Bar\":[{\"AnotherStr\":\"Another String1\"},{\"AnotherStr\":\"Another String2\"}]}"
    }

    class NullableListTestNull : TestBase<NullList>(
        NullList("A String"),
        NullList.serializer()
                                                   ) {
        override val expectedXML: String
            get() = "<Baz><Str>A String</Str></Baz>"
        override val expectedJson: String
            get() = "{\"Str\":\"A String\",\"Bar\":null}"

    }

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

        val serializedModel = format.encodeToString(SampleModel1.serializer(), model).normalize().replace('\'', '"')

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

        val serializedModel = format.encodeToString(SampleModel1.serializer(), model).normalize().replace('\'', '"')

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

}
