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

@file:Suppress("KDocUnresolvedReference")

package nl.adaptivity.xmlutil.serialization

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import nl.adaptivity.xmlutil.*
import nl.adaptivity.xmlutil.core.impl.multiplatform.StringWriter
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor
import nl.adaptivity.xmlutil.serialization.structure.XmlRootDescriptor
import nl.adaptivity.xmlutil.util.CompactFragment
import kotlin.reflect.KClass

@ExperimentalSerializationApi
expect fun getPlatformDefaultModule(): SerializersModule

@ExperimentalSerializationApi
private val defaultXmlModule = getPlatformDefaultModule() + SerializersModule {
    contextual(CompactFragment::class, CompactFragmentSerializer)
}

@Suppress("MemberVisibilityCanBePrivate")
/**
 * Class that provides access to XML parsing and serialization for the Kotlin Serialization system. In most cases the
 * companion functions would be used. Creating an object explicitly however allows for the serialization to be configured.
 *
 * **Note** that at this point not all configuration options will work on all platforms.
 *
 * The serialization can be configured with various annotations: [XmlSerialName], [XmlChildrenName], [XmlPolyChildren],
 * [XmlDefault], [XmlElement] [XmlValue]. These control the way the content is actually serialized. Those tags that support
 * being set on types (rather than properties) prefer values set on properties (the property can override settings on the
 * type).
 *
 * Serialization normally prefers to store values as attributes. This can be overridden by the [XmlElement] annotation
 * to force a tag child. Similarly [XmlValue] can be used for the child to be marked as textual content of the parent tag
 * - only one such child is allowed.
 *
 * Naming of tags and attributes follows some special rules. In particular attributes will be named based on their use
 * where tags are named based on their type. Both can be overridden by specifying [XmlSerialName] on the property.
 *
 * When names are not specified on types, their class name is used, but the package is normally omitted if it matches the
 * package name of the class that represents the parent. Attributes get their use site name which is either the property
 * name or the name modified through [SerialName]
 *
 * @property serializersModule The serialization context used to resolve serializers etc.
 * @property config The configuration of the various options that may apply.
 */
@OptIn(ExperimentalSerializationApi::class)
class XML constructor(
    val config: XmlConfig,
    serializersModule: SerializersModule = EmptySerializersModule
                     ) : StringFormat {
    override val serializersModule: SerializersModule = serializersModule + defaultXmlModule

    @Deprecated("Use config directly", ReplaceWith("config.repairNamespaces"))
    val repairNamespaces: Boolean
        get() = config.repairNamespaces

    @Suppress("DEPRECATION")
    @Deprecated("Use config directly", ReplaceWith("config.omitXmlDecl"))
    val omitXmlDecl: Boolean
        get() = config.omitXmlDecl

    @Suppress("DEPRECATION")
    @Deprecated("Use config directly, consider using indentString", ReplaceWith("config.indent"))
    val indent: Int
        get() = config.indent

    @Suppress("DEPRECATION")
    @Deprecated("Use the new configuration system")
    constructor(
        repairNamespaces: Boolean = true,
        omitXmlDecl: Boolean = true,
        indent: Int = 0,
        serializersModule: SerializersModule = EmptySerializersModule
               )
            : this(XmlConfig(repairNamespaces, omitXmlDecl, indent), serializersModule)

    constructor(config: XmlConfig.Builder, serializersModule: SerializersModule = EmptySerializersModule) : this(
        XmlConfig(config),
        serializersModule
                                                                                                                )

    constructor(
        serializersModule: SerializersModule = EmptySerializersModule,
        configure: XmlConfig.Builder.() -> Unit = {}
               ) : this(XmlConfig.Builder().apply(configure), serializersModule)

    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        return encodeToString(serializer, value, null)
    }

    /**
     * Transform into a string. This function is expected to be called indirectly.
     *
     * @param value The actual object
     * @param serializer The serializer/saver to use to write
     * @param prefix The prefix (if any) to use for the namespace
     */
    fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T, prefix: String?): String {
        val stringWriter = StringWriter()
        val xmlWriter = XmlStreaming.newWriter(stringWriter, config.repairNamespaces, config.xmlDeclMode)

        var ex: Throwable? = null
        try {
            toXml(xmlWriter, serializer, value, prefix)
        } catch (e: Throwable) {
            ex = e
        } finally {
            try {
                xmlWriter.close()
            } finally {
                ex?.let { throw it }
            }

        }
        return stringWriter.toString()
    }

    /**
     * Write the object to the given writer
     *
     * @param value The actual object
     * @param target The [XmlWriter] to append the object to
     * @param prefix The prefix (if any) to use for the namespace
     */
    inline fun <reified T : Any> encodeToWriter(target: XmlWriter, value: T, prefix: String?) {
        toXml(target, serializer<T>(), value, prefix)
    }

    /**
     * Transform onto an existing xml writer.
     *
     * @param target The [XmlWriter] to append the object to
     * @param value The actual object
     * @param serializer The serializer/saver to use to write
     * @param prefix The prefix (if any) to use for the namespace
     */
    fun <T> encodeToWriter(
        target: XmlWriter,
        serializer: SerializationStrategy<T>,
        value: T,
        prefix: String? = null
                          ) {
        target.indentString = config.indentString

        val serialName = serializer.descriptor.serialName
        val serialQName =
            serializer.descriptor.annotations.firstOrNull<XmlSerialName>()?.toQName()
                ?.let { if (prefix != null) it.copy(prefix = prefix) else it }
                ?: config.policy.serialNameToQName(
                    serialName,
                    XmlEvent.NamespaceImpl(
                        XMLConstants.DEFAULT_NS_PREFIX,
                        XMLConstants.NULL_NS_URI
                                          )
                                                  )

        val xmlEncoderBase = XmlEncoderBase(serializersModule, config, target)
        val root = XmlRootDescriptor(xmlEncoderBase, serializer.descriptor, serialQName)

        val xmlDescriptor = root.getElementDescriptor(0)

        val encoder = xmlEncoderBase.XmlEncoder(xmlDescriptor)

        serializer.serialize(encoder, value)
    }

    /**
     * Decode the given string value using the deserializer. It is equivalent to
     * `decodeFromReader(deserializer, XmlStreaming.newReader(string))`.
     * @param deserializer The deserializer to use.
     * @param string The string input
     */
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        return decodeFromReader(deserializer, XmlStreaming.newReader(string))
    }


    inline fun <reified T : Any> decodeFromReader(reader: XmlReader): T =
        decodeFromReader(serializer<T>(), reader)

    /**
     * Parse an object of the type [T] out of the reader. This function is intended mostly to be used indirectly where
     * though the reified function.
     *
     * @param reader An [XmlReader] that contains the XML from which to read the object
     * @param deserializer The loader to use to read the object
     */
    fun <T> decodeFromReader(
        deserializer: DeserializationStrategy<T>,
        reader: XmlReader
                            ): T {
        val serialDescriptor = deserializer.descriptor
        val serialName = serialDescriptor.annotations.firstOrNull<XmlSerialName>()?.toQName()
            ?: config.policy.serialNameToQName(
                serialDescriptor.serialName,
                XmlEvent.NamespaceImpl(XMLConstants.DEFAULT_NS_PREFIX, XMLConstants.NULL_NS_URI)
                                              )

        // We skip all ignorable content here. To get started while supporting direct content we need to put the parser
        // in the correct state of having just read the startTag (that would normally be read by the code that determines
        // what to parse (before calling readSerializableValue on the value)
        reader.skipPreamble()

        val xmlDecoderBase = XmlDecoderBase(serializersModule, config, reader)
        val rootDescriptor = XmlRootDescriptor(xmlDecoderBase, serialDescriptor, serialName)

        val decoder = xmlDecoderBase.XmlDecoder(
            rootDescriptor.getElementDescriptor(0)
                                               )
        return decoder.decodeSerializableValue(deserializer)
    }

    fun xmlDescriptor(serializer: SerializationStrategy<*>): XmlDescriptor {
        return xmlDescriptor(serializer.descriptor)
    }

    fun xmlDescriptor(deserializer: DeserializationStrategy<*>): XmlDescriptor {
        return xmlDescriptor(deserializer.descriptor)
    }

    fun xmlDescriptor(deserializer: KSerializer<*>): XmlDescriptor {
        return xmlDescriptor(deserializer.descriptor)
    }

    private fun xmlDescriptor(serialDescriptor: SerialDescriptor): XmlRootDescriptor {
        val serialName = serialDescriptor.annotations.firstOrNull<XmlSerialName>()?.toQName()
            ?: config.policy.serialNameToQName(
                serialDescriptor.serialName,
                XmlEvent.NamespaceImpl(XMLConstants.DEFAULT_NS_PREFIX, XMLConstants.NULL_NS_URI)
                                              )

        val codecBase = XmlEncoderBase(serializersModule, config, XmlStreaming.newWriter(StringWriter(), true))
        return XmlRootDescriptor(codecBase, serialDescriptor, serialName)
    }

    /**
     * Transform the object into an XML String. This is a shortcut for the non-reified version that takes a
     * KClass parameter
     */
    @Deprecated("Use encodeToString")
    @Suppress("unused")
    inline fun <reified T : Any> stringify(obj: T, prefix: String? = null): String =
        encodeToString(serializer<T>(), obj, prefix)

    /**
     * Transform into a string. This function is expected to be called indirectly.
     *
     * @param obj The actual object
     * @param saver The serializer/saver to use to write
     * @param prefix The prefix (if any) to use for the namespace
     */
    @Deprecated(
        "Fit within the serialization library, so reorder arguments",
        ReplaceWith("stringify(saver, obj, prefix)")
               )
    fun <T : Any> stringify(obj: T, saver: SerializationStrategy<T>, prefix: String? = null): String =
        encodeToString(saver, obj, prefix)


    @Deprecated("Use encodeToString", ReplaceWith("encodeToString(serializer, value)"))
    fun <T> stringify(serializer: SerializationStrategy<T>, value: T) =
        encodeToString(serializer, value)

    /**
     * Transform into a string. This function is expected to be called indirectly.
     *
     * @param obj The actual object
     * @param serializer The serializer/saver to use to write
     * @param prefix The prefix (if any) to use for the namespace
     */
    @Deprecated("Use encodeToString", ReplaceWith("encodeToString(serializer, obj, prefix)"))
    fun <T> stringify(serializer: SerializationStrategy<T>, obj: T, prefix: String?): String =
        encodeToString(serializer, obj, prefix)

    /**
     * Transform onto an existing xml writer.
     *
     * @param target The [XmlWriter] to append the object to
     * @param value The actual object
     * @param serializer The serializer/saver to use to write
     * @param prefix The prefix (if any) to use for the namespace
     */
    @Deprecated("Renamed to encodeToWriter", ReplaceWith("encodeToWriter(target, serializer, value, prefix)"))
    fun <T> toXml(
        target: XmlWriter,
        serializer: SerializationStrategy<T>,
        value: T,
        prefix: String? = null
                 ) {
        encodeToWriter(target, serializer, value, prefix)
    }

    /**
     * Write the object to the given writer
     *
     * @param obj The actual object
     * @param target The [XmlWriter] to append the object to
     * @param prefix The prefix (if any) to use for the namespace
     */
    @Deprecated("Use new naming scheme: encodeToWriter", ReplaceWith("encodeToWriter(target, obj, prefix)"))
    inline fun <reified T : Any> toXml(target: XmlWriter, obj: T, prefix: String? = null) {
        encodeToWriter(target, obj, prefix)
    }

    @Deprecated("Replaced by version with consistent parameter order", ReplaceWith("toXml(target, obj, prefix)"))
    inline fun <reified T : Any> toXml(obj: T, target: XmlWriter, prefix: String? = null) {
        encodeToWriter(target, obj, prefix)
    }

    /**
     * Parse an object of the type [T] out of the reader
     */
    @Suppress("unused")
    @Deprecated("Renamed to decodeFromReader", ReplaceWith("decodeFromReader<T>(reader)"))
    inline fun <reified T : Any> parse(reader: XmlReader) = decodeFromReader<T>(reader)

    /**
     * Parse an object of the type [T] out of the reader. This function is intended mostly to be used indirectly where
     * though the reified function.
     *
     * @param reader An [XmlReader] that contains the XML from which to read the object
     * @param deserializer The loader to use to read the object
     */
    @Deprecated("Renamed to decodeFromReader", ReplaceWith("decodeFromReader(deserializer, reader)"))
    fun <T> parse(deserializer: DeserializationStrategy<T>, reader: XmlReader): T {
        return decodeFromReader(deserializer, reader)
    }

    @Deprecated("Use new function name", ReplaceWith("decodeFromString(deserializer, string)"))
    fun <T> parse(deserializer: DeserializationStrategy<T>, string: String): T {
        return decodeFromString(deserializer, string)
    }

    /**
     * Transform into a string. This function is expected to be called indirectly.
     *
     * @param kClass The type of the object being serialized
     * @param obj The actual object
     * @param prefix The prefix (if any) to use for the namespace
     */
    @Deprecated("Reflection is no longer supported", level = DeprecationLevel.ERROR)
    @Suppress("UNUSED_PARAMETER")
    fun <T : Any> stringify(kClass: KClass<T>, obj: T, prefix: String? = null): String {
        throw UnsupportedOperationException("Not supported by serialization library ")
    }

    /**
     * Transform into a string. This function is expected to be called indirectly.
     *
     * @param kClass The type of the object being serialized
     * @param obj The actual object
     * @param target The [XmlWriter] to append the object to
     * @param saver The serializer/saver to use to write
     * @param prefix The prefix (if any) to use for the namespace
     */
    @Deprecated("Reflection is no longer supported", level = DeprecationLevel.ERROR)
    @Suppress("UNUSED_PARAMETER")
    fun <T : Any> toXml(target: XmlWriter, kClass: KClass<T>, obj: T, prefix: String? = null) {
        throw UnsupportedOperationException("Reflection no longer works")
    }

    @Deprecated("Reflection is no longer supported", level = DeprecationLevel.ERROR)
    @Suppress("UNUSED_PARAMETER")
    fun <T : Any> parse(kClass: KClass<T>, reader: XmlReader): T {
        throw UnsupportedOperationException("Reflection for serialization is no longer supported")
    }

    /**
     * Parse an object of the type [T] out of the string. It merely creates an xml reader and forwards the request.
     * This function is intended mostly to be used indirectly where
     * though the reified function. The loader defaults to the loader for [kClass]
     *
     * @param kClass The actual class object to parse the object from.
     * @param string The string that contains the XML from which to read the object
     * @param loader The loader to use to read the object
     */
    @Deprecated("Reflection is no longer supported", level = DeprecationLevel.ERROR)
    @Suppress("UNUSED_PARAMETER")
    fun <T : Any> parse(kClass: KClass<T>, string: String): T {
        throw UnsupportedOperationException("Reflection for serialization is no longer supported")
    }

    companion object : StringFormat {
        val defaultInstance = XML(XmlConfig())
        override val serializersModule: SerializersModule
            get() = defaultInstance.serializersModule

        fun xmlDescriptor(serializer: SerializationStrategy<*>): XmlDescriptor {
            return defaultInstance.xmlDescriptor(serializer)
        }

        fun xmlDescriptor(deserializer: DeserializationStrategy<*>): XmlDescriptor {
            return defaultInstance.xmlDescriptor(deserializer)
        }

        fun xmlDescriptor(serializer: KSerializer<*>): XmlDescriptor {
            return defaultInstance.xmlDescriptor(serializer)
        }

        private fun xmlDescriptor(serialDescriptor: SerialDescriptor): XmlRootDescriptor {
            return defaultInstance.xmlDescriptor(serialDescriptor)
        }

        /**
         * Transform the object into an XML string. This requires the object to be serializable by the kotlin
         * serialization library (either it has a built-in serializer or it is [kotlinx.serialization.Serializable].
         * @param value The object to transform
         * @param serializer The serializer to user
         */
        override fun <T> encodeToString(
            serializer: SerializationStrategy<T>,
            value: T
                                       ): String =
            defaultInstance.encodeToString(serializer, value)

        /**
         * Transform the object into an XML string. This requires the object to be serializable by the kotlin
         * serialization library (either it has a built-in serializer or it is [kotlinx.serialization.Serializable].
         * @param value The object to transform
         * @param serializer The serializer to user
         * @param prefix The namespace prefix to use
         */
        fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T, prefix: String): String =
            defaultInstance.encodeToString(serializer, value, prefix)

        /**
         * Transform the object into an XML string. This requires the object to be serializable by the kotlin
         * serialization library (either it has a built-in serializer or it is [kotlinx.serialization.Serializable].
         * @param obj The object to transform
         * @param prefix The namespace prefix to use
         */
        inline fun <reified T : Any> encodeToString(obj: T, prefix: String? = null): String =
            encodeToString(serializer<T>(), obj, prefix ?: "")

        /**
         * Write the object to the given writer
         *
         * @param target The [XmlWriter] to append the object to
         * @param value The actual object
         * @param prefix The prefix (if any) to use for the namespace
         */
        inline fun <reified T : Any> encodeToWriter(target: XmlWriter, value: T, prefix: String? = null) {
            defaultInstance.encodeToWriter(target, serializer<T>(), value, prefix)
        }

        /**
         * Write the object to the given writer
         *
         * @param target The [XmlWriter] to append the object to
         * @param serializer The serializer to use
         * @param value The actual object
         * @param prefix The prefix (if any) to use for the namespace
         */
        fun <T> encodeToWriter(
            target: XmlWriter,
            serializer: SerializationStrategy<T>,
            value: T,
            prefix: String? = null
                              ) {
            defaultInstance.encodeToWriter(target, serializer, value, prefix)
        }

        /**
         * Parse an object of the type [T] out of the reader
         * @param str The source of the XML events
         */
        inline fun <reified T : Any> decodeFromString(str: String): T = decodeFromString(serializer(), str)

        /**
         * Parse an object of the type [T] out of the reader
         * @param string The source of the XML events
         * @param deserializer The loader to use
         */
        override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
            return defaultInstance.decodeFromString(deserializer, string)
        }

        /**
         * Parse an object of the type [T] out of the reader
         * @param reader The source of the XML events
         */
        inline fun <reified T : Any> decodeFromReader(reader: XmlReader): T =
            defaultInstance.decodeFromReader(reader)

        /**
         * Parse an object of the type [T] out of the reader
         * @param deserializer The loader to use (rather than the default)
         * @param reader The source of the XML events
         */
        fun <T : Any> decodeFromReader(
            deserializer: DeserializationStrategy<T>,
            reader: XmlReader
                                      ) = defaultInstance.decodeFromReader(deserializer, reader)

        @Deprecated("Use encodeToString", ReplaceWith("encodeToString(serializer, value)"))
        fun <T> stringify(serializer: SerializationStrategy<T>, value: T): String {
            return encodeToString(serializer, value)
        }

        /**
         * Transform the object into an XML string. This requires the object to be serializable by the kotlin
         * serialization library (either it has a built-in serializer or it is [kotlinx.serialization.Serializable].
         * @param obj The object to transform
         * @param serializer The serializer to user
         * @param prefix The namespace prefix to use
         */
        @Deprecated(
            "Use encodeToString", ReplaceWith(
                "encodeToString(serializer, obj, prefix)",
                "nl.adaptivity.xmlutil.serialization.XML.Companion.encodeToString"
                                             )
                   )
        fun <T> stringify(serializer: SerializationStrategy<T>, obj: T, prefix: String): String =
            encodeToString(serializer, obj, prefix)

        /**
         * Transform the object into an XML string. This requires the object to be serializable by the kotlin
         * serialization library (either it has a built-in serializer or it is [kotlinx.serialization.Serializable].
         * @param obj The object to transform
         * @param prefix The namespace prefix to use
         */
        @Deprecated(
            "Use encodeToString", ReplaceWith(
                "encodeToString(obj, prefix ?: \"\")",
                "nl.adaptivity.xmlutil.serialization.XML.Companion.encodeToString"
                                             )
                   )
        inline fun <reified T : Any> stringify(obj: T, prefix: String? = null): String =
            encodeToString(obj, prefix ?: "")

        /**
         * Write the object to the given writer
         *
         * @param obj The actual object
         * @param dest The [XmlWriter] to append the object to
         * @param prefix The prefix (if any) to use for the namespace
         */
        @Suppress("unused")
        @Deprecated("Renamed to encodeToWriter", ReplaceWith(
            "encodeToWriter(dest, obj, prefix)",
            "nl.adaptivity.xmlutil.serialization.XML.Companion.encodeToWriter"
                                                            )
                   )
        inline fun <reified T : Any> toXml(dest: XmlWriter, obj: T, prefix: String? = null) =
            encodeToWriter(dest, obj, prefix)

        /**
         * Write the object to the given writer
         *
         * @param target The [XmlWriter] to append the object to
         * @param serializer The serializer to use
         * @param value The actual object
         * @param prefix The prefix (if any) to use for the namespace
         */
        @Deprecated("Renamed to encodeToWriter", ReplaceWith(
            "encodeToWriter(target, serializer, value, prefix)",
            "nl.adaptivity.xmlutil.serialization.XML.Companion.encodeToWriter"
                                                            )
                   )
        fun <T> toXml(
            target: XmlWriter,
            serializer: SerializationStrategy<T>,
            value: T,
            prefix: String? = null
                     ) =
            encodeToWriter(target, serializer, value, prefix)

        /**
         * Parse an object of the type [T] out of the reader
         * @param str The source of the XML events
         */
        @Deprecated(
            "Use decodeFromString", ReplaceWith(
                "decodeFromString(str)",
                "nl.adaptivity.xmlutil.serialization.XML.Companion.decodeFromString"
                                               )
                   )
        inline fun <reified T : Any> parse(str: String): T = decodeFromString(str)

        /**
         * Parse an object of the type [T] out of the reader
         * @param string The source of the XML events
         * @param deserializer The loader to use
         */
        @Suppress("unused")
        @Deprecated("Use new name", ReplaceWith("decodeFromString(deserializer, string)"))
        fun <T> parse(
            deserializer: DeserializationStrategy<T>,
            string: String
                     ): T = decodeFromString(deserializer, string)

        @Deprecated(
            "Replaced by version with consistent parameter order",
            ReplaceWith("parse(reader, kClass, loader)")
                   )
        fun <T : Any> parse(
            @Suppress("UNUSED_PARAMETER") kClass: KClass<T>, reader: XmlReader, loader: DeserializationStrategy<T>
                           ): T =
            parse(reader, loader)

        /**
         * Parse an object of the type [T] out of the reader
         * @param reader The source of the XML events
         * @param loader The loader to use (rather than the default)
         */
        @Suppress("UNUSED_PARAMETER")
        @Deprecated(
            "Use the version that doesn't take a KClass",
            ReplaceWith("parse(reader, loader)", "nl.adaptivity.xmlutil.serialization.XML.Companion.parse")
                   )
        fun <T : Any> parse(reader: XmlReader, kClass: KClass<T>, loader: DeserializationStrategy<T>): T =
            parse(reader, loader)

        /**
         * Parse an object of the type [T] out of the reader
         * @param reader The source of the XML events
         */
        @Deprecated(
            "Renamed to decodeFromReader", ReplaceWith(
                "decodeFromReader(reader)",
                "nl.adaptivity.xmlutil.serialization.XML.Companion.decodeFromReader"
                                                      )
                   )
        inline fun <reified T : Any> parse(reader: XmlReader): T = decodeFromReader(reader)

        /**
         * Parse an object of the type [T] out of the reader
         * @param reader The source of the XML events
         * @param loader The loader to use (rather than the default)
         */
        @Suppress("unused")
        @Deprecated(
            "Renamed to decodeFromReader", ReplaceWith(
                "decodeFromReader(reader, loader)",
                "nl.adaptivity.xmlutil.serialization.XML.Companion.decodeFromReader"
                                                      )
                   )
        fun <T : Any> parse(reader: XmlReader, loader: DeserializationStrategy<T>): T =
            decodeFromReader(loader, reader)

        /**
         * Transform the object into an XML string. This requires the object to be serializable by the kotlin
         * serialization library (either it has a built-in serializer or it is [kotlinx.serialization.Serializable].
         * @param obj The object to transform
         * @param kClass The class where to get the serializer from
         * @param prefix The namespace prefix to use
         */
        @Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
        @Deprecated("Reflection is no longer supported", level = DeprecationLevel.ERROR)
        fun <T : Any> stringify(obj: T, kClass: KClass<T> = obj::class as KClass<T>, prefix: String? = null): String {
            throw UnsupportedOperationException("Reflection for serialization is no longer supported")
        }

        /**
         * Transform into a string. This function is expected to be called indirectly.
         *
         * @param kClass The type of the object being serialized
         * @param obj The actual object
         * @param dest The [XmlWriter] to append the object to
         * @param prefix The prefix (if any) to use for the namespace
         */
        @Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
        @Deprecated("Reflection is no longer supported", level = DeprecationLevel.ERROR)
        fun <T : Any> toXml(
            dest: XmlWriter,
            obj: T,
            kClass: KClass<T> = obj::class as KClass<T>,
            prefix: String? = null
                           ) {
            throw UnsupportedOperationException("Reflection is no longer supported for serialization")
        }

        /**
         * Parse an object of the type [T] out of the reader
         * @param str The source of the XML events
         * @param kClass The class to parse. Used for class annotations.
         */
        @Deprecated("Reflection is no longer supported", level = DeprecationLevel.ERROR)
        @Suppress("UNUSED_PARAMETER")
        fun <T : Any> parse(kClass: KClass<T>, str: String): T =
            throw UnsupportedOperationException("Reflection for serialization is no longer supported")

        @Suppress("UNUSED_PARAMETER")
        @Deprecated("Reflection is no longer supported", level = DeprecationLevel.ERROR)
        fun <T : Any> parse(kClass: KClass<T>, reader: XmlReader): T =
            throw UnsupportedOperationException("Reflection is no longer supported for serialization")

        /**
         * Parse an object of the type [T] out of the reader
         * @param reader The source of the XML events
         * @param kClass The class to parse. Used for class annotations.
         */
        @Deprecated("Reflection is no longer supported", level = DeprecationLevel.ERROR)
        @Suppress("UNUSED_PARAMETER")
        fun <T : Any> parse(reader: XmlReader, kClass: KClass<T>): T =
            throw UnsupportedOperationException("Reflection is no longer supported for serialization")
    }

    interface XmlCodecConfig {
        /**
         * The currently active serialization context
         */
        val serializersModule: SerializersModule

        /**
         * The configuration used for serialization
         */
        val config: XmlConfig

        /**
         * A delegate method to get access to a format with the same configuration
         */
        fun delegateFormat(): XML = XML(config, serializersModule)
    }

    /**
     * An interface that allows custom serializers to special case being serialized to XML and retrieve the underlying
     * [XmlWriter]. This is used for example by [CompactFragment] to make the fragment transparent when serializing to
     * XML.
     */
    interface XmlOutput: XmlCodecConfig {
        /**
         * The name for the current tag
         */
        val serialName: QName

        /**
         * The XmlWriter used. Can be used directly by serializers
         */
        val target: XmlWriter

        @Deprecated("Not used will always return null", ReplaceWith("null"))
        val currentTypeName: Nothing?
            get() = null
    }

    /**
     * An interface that allows custom serializers to special case being serialized to XML and retrieve the underlying
     * [XmlReader]. This is used for example by [CompactFragment] to read arbitrary XML from the stream and store it inside
     * the buffer (without attempting to use the serializer/decoder for it.
     */
    interface XmlInput: XmlCodecConfig {
        /**
         * The reader used. Can be used directly by serializers
         */
        val input: XmlReader
    }

}

enum class OutputKind {
    Element, Attribute, Text, Mixed;
}

enum class InputKind {
    Element {
        override fun mapsTo(outputKind: OutputKind): Boolean {
            return outputKind == OutputKind.Element// || outputKind == OutputKind.Mixed
        }
    },
    Attribute {
        override fun mapsTo(outputKind: OutputKind): Boolean = outputKind == OutputKind.Attribute
    },
    Text {
        override fun mapsTo(outputKind: OutputKind): Boolean {
            return outputKind == OutputKind.Text// || outputKind == OutputKind.Mixed
        }
    };

    internal fun mapsTo(xmlDescriptor: XmlDescriptor): Boolean {
        return mapsTo(xmlDescriptor.outputKind)
    }

    internal abstract fun mapsTo(outputKind: OutputKind): Boolean
}

fun XmlSerialName.toQName() = when {
    namespace == UNSET_ANNOTATION_VALUE -> QName(value)
    prefix == UNSET_ANNOTATION_VALUE    -> QName(namespace, value)
    else                                -> QName(namespace, value, prefix)
}

fun XmlChildrenName.toQName() = when {
    namespace == UNSET_ANNOTATION_VALUE -> QName(value)
    prefix == UNSET_ANNOTATION_VALUE    -> QName(namespace, value)
    else                                -> QName(namespace, value, prefix)
}

internal data class PolyBaseInfo(
    val tagName: QName,
    val descriptor: SerialDescriptor
                                ) {

    @OptIn(ExperimentalSerializationApi::class)
    val describedName
        get() = descriptor.serialName

}

internal inline fun <reified T> Iterable<*>.firstOrNull(): T? {
    for (e in this) {
        if (e is T) return e
    }
    return null
}


@ExperimentalSerializationApi
internal fun SerialDescriptor.getValueChild(): Int {
    for (i in 0 until elementsCount) {
        if (getElementAnnotations(i).any { it is XmlValue }) return i
    }
    return CompositeDecoder.UNKNOWN_NAME
}

@ExperimentalSerializationApi
@Deprecated("Use index version that returns -1 for missing child")
internal fun SerialDescriptor.getValueChildOrThrow(): Int {
    if (elementsCount == 1) {
        return 0
    } else {
        return getValueChild().also {
            if (it < 0) throw XmlSerialException("No value child found for type with descriptor: $this")
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal fun XmlDescriptor.getValueChild(): Int {
    for (i in 0 until elementsCount) {
        if (serialDescriptor.getElementAnnotations(i).any { it is XmlValue }) return i
    }
    return -1
}

/** Straightforward copy function */
internal fun QName.copy(
    namespaceURI: String = this.namespaceURI,
    localPart: String = this.localPart,
    prefix: String = this.prefix
                       ) =
    QName(namespaceURI, localPart, prefix)

/** Shortcircuit copy function that creates a new version (if needed) with the new prefix only */
internal fun QName.copy(prefix: String = this.prefix) = when (prefix) {
    this.prefix -> this
    else        -> QName(namespaceURI, localPart, prefix)
}

/**
 * Extension function for writing an object as XML.
 *
 * @param out The writer to use for writing the XML
 * @param serializer The serializer to use. Often `T.Companion.serializer()`
 */
@Deprecated("\"Use the XML object that allows configuration\"")
fun <T : Any> T.writeAsXml(out: XmlWriter, serializer: SerializationStrategy<T>) =
    XML.defaultInstance.toXml(out, serializer, this)

/**
 * Extension function that allows any (serializable) object to be written to an XmlWriter.
 */
@Deprecated("Use the XML object that allows configuration", ReplaceWith("XML.toXml(out, this)"))
inline fun <reified T : Any> T.writeAsXML(out: XmlWriter) = XML.toXml(out, this)

@RequiresOptIn("This function will become private in the future", RequiresOptIn.Level.WARNING)
annotation class WillBePrivate