/*
 * Copyright (c) 2018.
 *
 * This file is part of xmlutil.
 *
 * xmlutil is free software: you can redistribute it and/or modify it under the terms of version 3 of the
 * GNU Lesser General Public License as published by the Free Software Foundation.
 *
 * xmlutil is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with xmlutil.  If not,
 * see <http://www.gnu.org/licenses/>.
 */

package nl.adaptivity.xmlutil.serialization

import kotlinx.serialization.*
import nl.adaptivity.xmlutil.*
import nl.adaptivity.xmlutil.multiplatform.assert
import nl.adaptivity.xmlutil.multiplatform.name
import nl.adaptivity.xmlutil.serialization.canary.*
import nl.adaptivity.xmlutil.serialization.compat.DummyParentDescriptor
import nl.adaptivity.xmlutil.serialization.compat.EncoderOutput
import nl.adaptivity.xmlutil.util.CompactFragment
import kotlin.reflect.KClass

data class NameHolder(val name: QName, val specified: Boolean)

class XmlNameMap {
    private val classMap = mutableMapOf<QName, String>()
    private val nameMap = mutableMapOf<String, NameHolder>()

    fun lookupName(kClass: KClass<*>) = lookupName(kClass.name)
    fun lookupClass(name: QName) = classMap[name.copy(prefix = "")]
    fun lookupName(kClass: String) = nameMap[kClass]

    fun registerClass(kClass: KClass<*>) {
        val serialInfo = kClass.serializer().serialClassDesc
        val serialName = serialInfo.getAnnotationsForClass().getXmlSerialName(null)//getSerialName(kClass.serializer())

        val name: QName
        val specified: Boolean
        if (serialName == null) {
            specified = false
            name = QName(kClass.name.substringAfterLast('.'))
        } else {
            specified = true
            name = serialName
        }
        registerClass(name, kClass.name, specified)
    }

    fun registerClass(name: QName, kClass: String, specified: Boolean) {
        classMap[name.copy(prefix = "")] = kClass
        nameMap[kClass] = NameHolder(name, specified)
    }
}

class XML(val context: SerialContext? = defaultSerialContext(),
          val repairNamespaces: Boolean = true,
          val omitXmlDecl: Boolean = true,
          var indent: Int = 0) {

    val nameMap = XmlNameMap()

    fun registerClass(kClass: KClass<*>) = nameMap.registerClass(kClass)

    fun registerClass(name: QName, kClass: KClass<*>) = nameMap.registerClass(name, kClass.name, true)

    inline fun <reified T : Any> stringify(obj: T, prefix: String? = null): String = stringify(T::class, obj,
                                                                                               context.klassSerializer(
                                                                                                   T::class), prefix)

    fun <T : Any> stringify(kClass: KClass<T>,
                            obj: T,
                            saver: KSerialSaver<T> = context.klassSerializer(kClass),
                            prefix: String? = null): String {
        return buildString {
            val writer = XmlStreaming.newWriter(this, repairNamespaces, omitXmlDecl)

            var ex: Exception? = null
            try {
                toXml(kClass, obj, writer, prefix, saver)
            } catch (e: Exception) {
                ex = e
            } finally {
                try {
                    writer.close()
                } finally {
                    ex?.let { throw it }
                }

            }
        }
    }

    inline fun <reified T : Any> toXml(obj: T, target: XmlWriter, prefix: String? = null) {
        toXml(T::class, obj, target, prefix, context.klassSerializer(T::class))
    }

    fun <T : Any> toXml(kClass: KClass<T>,
                        obj: T,
                        target: XmlWriter,
                        prefix: String? = null,
                        serializer: KSerialSaver<T> = context.klassSerializer(kClass)) {
        target.indent = indent

        val serialDescriptor = Canary.serialDescriptor(serializer, obj)

        val serialName = kClass.getSerialName(serializer as? KSerializer<*>, prefix)
        val encoder = XmlEncoderBase(context, target).XmlEncoder(DummyParentDescriptor(serialName, serialDescriptor), 0)

        val output = EncoderOutput(encoder, serialDescriptor)

        output.write(serializer, obj)
    }

    inline fun <reified T : Any> parse(reader: XmlReader) = parse(T::class, reader)

    fun <T : Any> parse(kClass: KClass<T>,
                        reader: XmlReader,
                        loader: KSerialLoader<T> = context.klassSerializer(kClass)): T {
        val serialName = kClass.getSerialName(loader as? KSerializer<*>)
        val extInfo = Canary.extInfo(loader)
        val input = XmlInputBase(reader).Initial(serialName, extInfo)
        return input.read(loader)
    }

    fun <T : Any> parse(kClass: KClass<T>, str: String, loader: KSerialLoader<T> = context.klassSerializer(kClass)): T {
        return parse(kClass, XmlStreaming.newReader(str), loader)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> stringify(obj: T, kClass: KClass<T> = obj::class as KClass<T>, prefix: String? = null): String =
            XML().run { stringify(kClass, obj, context.klassSerializer(kClass), prefix) }

        inline fun <reified T : Any> stringify(obj: T, prefix: String?): String = stringify(obj, T::class, prefix)

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> toXml(dest: XmlWriter,
                            obj: T,
                            kClass: KClass<T> = obj::class as KClass<T>,
                            prefix: String? = null) =
            XML().run { toXml(kClass, obj, dest, prefix, context.klassSerializer(kClass)) }

        inline fun <reified T : Any> toXml(dest: XmlWriter, obj: T, prefix: String?) = toXml(dest, obj, T::class,
                                                                                                                   prefix)

        fun <T : Any> parse(kClass: KClass<T>, str: String): T = XML().parse(kClass, str)
        fun <T : Any> parse(kClass: KClass<T>, str: String, loader: KSerialLoader<T>): T = XML().parse(kClass, str,
                                                                                                       loader)

        inline fun <reified T : Any> parse(str: String): T = parse(T::class, str)
        inline fun <reified T : Any> parse(str: String, loader: KSerialLoader<T>): T = parse(T::class, str, loader)

        fun <T : Any> parse(kClass: KClass<T>, reader: XmlReader): T = XML().parse(kClass, reader)
        fun <T : Any> parse(kClass: KClass<T>, reader: XmlReader, loader: KSerialLoader<T>): T = XML().parse(kClass,
                                                                                                                                   reader,
                                                                                                                                   loader)

        inline fun <reified T : Any> parse(reader: XmlReader): T = parse(T::class, reader)
        inline fun <reified T : Any> parse(reader: XmlReader, loader: KSerialLoader<T>): T = parse(T::class, reader,
                                                                                                                         loader)
    }

    internal interface XmlCommon<QN : QName?> {
        var serialName: QN
        val childName: QName?
        val myCurrentTag: XmlTag
        val namespaceContext: NamespaceContext

        fun getTagName(desc: KSerialClassDesc) =
            myCurrentTag.typeInfo.typeName//.also { serialName = it }


        fun KSerialClassDesc.getSafeTagName(index:Int): QName {
            getTagName(index)?.let { return it }

            val name = getElementName(index)
            val i = name.indexOf(':')
            return when {
                i > 0 -> {
                    val prefix = name.substring(i + 1)
                    val ns = namespaceContext.getNamespaceURI(prefix) ?: throw IllegalArgumentException(
                            "Missing namespace for prefix $prefix")
                    QName(ns, name.substring(0, i), prefix)
                }
                else  -> QName(serialName?.namespaceURI ?: "", name, /*serialName?.prefix ?: */"")
            }
        }

        fun KSerialClassDesc.getTagName(index: Int): QName? {
            return getAnnotationsForIndex(index).getXmlSerialName(serialName)
        }

        fun KSerialClassDesc.getValueChild(): Int {
            if (associatedFieldsCount == 1) {
                return 0
            } else {
                for (i in 0 until associatedFieldsCount) {
                    if (getAnnotationsForIndex(i).any { it is XmlValue }) return i
                }
                // TODO actually determine this properly
                return KInput.UNKNOWN_NAME
            }
        }

        fun doGetTag(classDesc: KSerialClassDesc, index: Int): XmlTag {
            return XmlTagImpl(classDesc, index, classDesc.outputKind(index), classDesc.getSafeTagName(index), useQName = classDesc.getTagName(index))
        }

        fun QName.normalize(): QName {
            return when {
                namespaceURI.isEmpty() -> copy(namespaceURI = namespaceContext.getNamespaceURI(prefix) ?: "",
                                               prefix = "")
                else                   -> copy(prefix = "")
            }
        }

        fun polyTagName(parentTag: QName,
                        polyChild: String,
                        currentTypeName: String?,
                        itemIdx: Int): PolyInfo {
            val currentPkg = currentTypeName?.substringBeforeLast('.', "") ?: ""
            val eqPos = polyChild.indexOf('=')
            val pkgPos: Int
            val prefPos: Int
            val typeNameBase: String
            val prefix: String
            val localPart: String

            if (eqPos < 0) {
                typeNameBase = polyChild
                pkgPos = polyChild.lastIndexOf('.')
                prefPos = -1
                prefix = parentTag.prefix
                localPart = if (pkgPos < 0) polyChild else polyChild.substring(pkgPos + 1)
            } else {
                typeNameBase = polyChild.substring(0, eqPos).trim()
                pkgPos = polyChild.lastIndexOf('.', eqPos - 1)
                prefPos = polyChild.indexOf(':', eqPos + 1)

                if (prefPos < 0) {
                    prefix = parentTag.prefix
                    localPart = polyChild.substring(eqPos + 1).trim()
                } else {
                    prefix = polyChild.substring(eqPos + 1, prefPos).trim()
                    localPart = polyChild.substring(prefPos + 1).trim()
                }
            }


            val ns = if (prefPos >= 0) namespaceContext.getNamespaceURI(prefix)
                                       ?: parentTag.namespaceURI else parentTag.namespaceURI

            val typename = if (pkgPos >= 0 || currentPkg.isEmpty()) typeNameBase else "$currentPkg.$typeNameBase"

            val name = QName(ns, localPart, prefix)

            return PolyInfo(typename, name, itemIdx)
        }

        fun PolyInfo(parentTag: QName,
                     currentTypeName: String?,
                     polyChildren: Array<String>): XmlNameMap {
            val result = XmlNameMap()

            for (polyChild in polyChildren) {
                val polyInfo = polyTagName(parentTag, polyChild, currentTypeName, -1)

                result.registerClass(polyInfo.tagName, polyInfo.kClass, polyChild.indexOf('=') >= 0)
            }

            return result
        }

    }

    interface XmlOutput {
        /**
         * The name for the current tag
         */
        val serialName: QName
        /**
         * The currently active serialization context
         */
        val context: SerialContext?
        /**
         * The XmlWriter used. Can be used directly by serializers
         */
        val target: XmlWriter

        @Deprecated("Not used will always return null", ReplaceWith("null"))
        val currentTypeName: Nothing? get() = null
    }

    interface XmlInput {
        val input: XmlReader
    }

    open class XmlInputBase internal constructor(val input: XmlReader) {

        var skipRead = false

        inner class Initial(val serialName: QName, val extInfo: ExtInfo) : ElementValueInput(), XmlInput {
            override val input: XmlReader
                get() = this@XmlInputBase.input

            override fun readBegin(desc: KSerialClassDesc, vararg typeParams: KSerializer<*>): KInput {
                input.nextTag()

                return readBegin(desc, serialName, null, false, extInfo)
            }
        }

        internal abstract inner class Base(val desc: KSerialClassDesc,
                                           override var serialName: QName,
                                           override val childName: QName?) : TaggedInput<XmlTag>(), XmlCommon<QName>, XmlInput {


            private var nameToMembers: Map<QName, Int>? = null
            protected var polyChildren: Map<QName, PolyInfo>? = null

            override val input: XmlReader get() = this@XmlInputBase.input

            override val myCurrentTag: XmlTag get() = currentTag
            override val namespaceContext: NamespaceContext get() = input.namespaceContext

            final override fun KSerialClassDesc.getTag(index: Int): XmlTag {
                return doGetTag(this, index)
            }

            override fun doGetTag(classDesc: KSerialClassDesc, index: Int): XmlTag {
                return XmlTagImpl(classDesc, index, classDesc.outputKind(index), classDesc.getSafeTagName(index), useQName = classDesc.getTagName(index))
            }

            final override fun readBegin(desc: KSerialClassDesc, vararg typeParams: KSerializer<*>): KInput {
                return readBegin(ElementTag(desc, currentTag.asExtTag))
            }

            open fun readBegin(tag: ElementXmlTag): KInput {
                val tagName = tag.useInfo.tagName

                val polyInfo = polyChildren?.values?.firstOrNull { it.index == tag.useInfo.index && it.tagName.normalize() == input.name.normalize() }

                return readBegin(tag.elementDesc, tagName, polyInfo, false, tag.extInfo)
            }

            override fun <T> readSerializableValue(loader: KSerialLoader<T>): T {
                currentTag.updateExtInfo(loader)
                return super.readSerializableValue(loader)
            }

            override fun <T : Any> updateNullableSerializableValue(loader: KSerialLoader<T?>,
                                                                   desc: KSerialClassDesc,
                                                                   old: T?): T? {
                currentTag.updateExtInfo(loader)
                return super.updateNullableSerializableValue(loader, desc, old)
            }

            override fun <T> updateSerializableValue(loader: KSerialLoader<T>, desc: KSerialClassDesc, old: T): T {
                currentTag.updateExtInfo(loader)
                return super.updateSerializableValue(loader, desc, old)
            }

            open fun KSerialClassDesc.indexOf(name: QName, attr: Boolean): Int {
                val polyMap: Map<QName, PolyInfo>
                val nameMap: Map<QName, Int>

                if (polyChildren != null && nameToMembers != null) {
                    polyMap = polyChildren!!
                    nameMap = nameToMembers!!
                } else {
                    polyMap = mutableMapOf(); polyChildren = polyMap
                    nameMap = mutableMapOf(); nameToMembers = nameMap


                    for (fieldNo in 0 until associatedFieldsCount) {
                        val polyChildren = getAnnotationsForIndex(fieldNo).firstOrNull<XmlPolyChildren>()
                        if (polyChildren != null) {
                            for (child in polyChildren.value) {
                                val polyInfo = polyTagName(serialName, child, this.name, fieldNo)
                                polyMap[polyInfo.tagName.normalize()] = polyInfo
                            }
                        } else {
                            nameMap[getSafeTagName(fieldNo).normalize()] = fieldNo
                        }
                    }

                }
                val normalName = name.normalize()
                nameMap[normalName]?.let { return it }
                polyMap[normalName]?.let { return it.index }

                if (attr && name.namespaceURI.isEmpty()) {
                    val attrName = normalName.copy(namespaceURI = serialName.namespaceURI)
                    nameMap[attrName]?.let { return it }
                    polyMap[normalName.copy(namespaceURI = serialName.namespaceURI)]?.let { return it.index }
                }

//                val pkg = desc.name.substringBeforeLast('.', "")

                throw SerializationException("Could not find a field for name $name\n  candidates " +
                                             "were: ${(nameMap.keys + polyMap.keys).joinToString()}")
            }

            override fun readElement(desc: KSerialClassDesc): Int {
                if (skipRead) { // reading values will already move to the end tag.
                    assert(input.isEndElement())
                    skipRead = false
                    return readElementEnd(desc)
                }
                // TODO validate correctness of type
                for (eventType in input) {
                    if (!eventType.isIgnorable) {
                        // The android reader doesn't check whitespaceness

                        when (eventType) {
                            EventType.END_ELEMENT   -> return readElementEnd(desc)
                            EventType.TEXT          -> if (!input.isWhitespace()) return desc.getValueChild()
                            EventType.ATTRIBUTE     -> return desc.indexOf(input.name, true)
                            EventType.START_ELEMENT -> return desc.indexOf(input.name, false)
                            else                    -> throw SerializationException("Unexpected event in stream")
                        }
                    }
                }
                return KInput.READ_DONE
            }

            open fun readElementEnd(desc: KSerialClassDesc): Int {
                return READ_DONE
            }

            open fun doReadAttribute(tag: XmlTag): String =
                throw UnsupportedOperationException("Base doesn't support reading attributes")

            override fun readTaggedString(tag: XmlTag): String {
                tag.useInfo.replaceUnknownOutputWith(OutputKind.Attribute)
                return when (tag.useInfo.outputKind) {
                    OutputKind.Element   -> input.readSimpleElement()
                    OutputKind.Text      -> {
                        skipRead = true
                        input.allText()
                    }
                    OutputKind.Attribute -> doReadAttribute(tag)
                    OutputKind.Unknown   -> error("Unknown output kinds should be able to not occur here")
                }
            }

            override fun readTaggedBoolean(tag: XmlTag) = readTaggedString(tag).toBoolean()

            override fun readTaggedByte(tag: XmlTag) = readTaggedString(tag).toByte()

            override fun readTaggedChar(tag: XmlTag) = readTaggedString(tag).single()

            override fun readTaggedDouble(tag: XmlTag) = readTaggedString(tag).toDouble()

            override fun readTaggedFloat(tag: XmlTag) = readTaggedString(tag).toFloat()

            override fun readTaggedInt(tag: XmlTag) = when (tag.typeInfo.serialClassKind) {
                KSerialClassKind.SET,
                KSerialClassKind.LIST,
                KSerialClassKind.MAP -> if (tag.useInfo.index == 0) 1 else readTaggedString(
                    tag).toInt() // Always return elements one by one (there is no list size)

                else                 -> readTaggedString(tag).toInt()
            }

            override fun readTaggedLong(tag: XmlTag) = readTaggedString(tag).toLong()

            override fun readTaggedShort(tag: XmlTag) = readTaggedString(tag).toShort()

            override fun readTaggedValue(tag: XmlTag): Any {
                throw UnsupportedOperationException(
                    "Unable to read object ${tag.useInfo.declChildName} with tag $tag")
            }
        }

        internal inner class Element(desc: KSerialClassDesc,
                                     serialName: QName,
                                     childName: QName?,
                                     private var attrIndex: Int = 0,
                                     var extInfo: ExtInfo) : Base(desc, serialName, childName) {
            private val seenItems = BooleanArray(desc.associatedFieldsCount)

            private var nulledItemsIdx = -1

            override fun readBegin(tag: ElementXmlTag): KInput {
                val tagName = tag.useInfo.tagName

                val polyInfo = polyChildren?.values?.firstOrNull { it.index == tag.useInfo.index && it.tagName.normalize() == input.name.normalize() }


                return readBegin(tag.elementDesc, tagName, polyInfo, nulledItemsIdx >= 0, tag.extInfo)
            }

            fun nextNulledItemsIdx() {
                for (i in (nulledItemsIdx + 1) until seenItems.size) {
                    if (!seenItems[i]) {
                        val childInfo = extInfo.childInfo[i]
                        val default = childInfo.useAnnotations.firstOrNull<XmlDefault>()
                        // If a
                        val defaultOrList = childInfo.isNullable || default != null || when (childInfo.kind) {
                            KSerialClassKind.SET,
                            KSerialClassKind.MAP,
                            KSerialClassKind.LIST -> true
                            else                  -> false
                        }
                        if (defaultOrList) {
                            nulledItemsIdx = i
                            return
                        }
                    }
                }
                nulledItemsIdx = seenItems.size
            }

            override fun doGetTag(classDesc: KSerialClassDesc, index: Int): XmlTag {
                markItemSeen(index)

                if (index < extInfo.childInfo.size) {
                    return XmlTagImpl(classDesc, index, classDesc.outputKind(index, extInfo),
                                  classDesc.getSafeTagName(index), useQName = classDesc.getTagName(index))
                } else {
                    return XmlTagImpl(classDesc, index, OutputKind.Unknown, QName("value"), null, useQName = QName("value"))
                }
            }

            fun markItemSeen(index: Int) {
                seenItems[index] = true
            }

            override fun readElement(desc: KSerialClassDesc): Int {
                if (nulledItemsIdx >= 0) {
                    val sn = serialName
                    input.require(EventType.END_ELEMENT, sn.namespaceURI, sn.localPart)

                    if (nulledItemsIdx >= seenItems.size) return KInput.READ_DONE
                    val i = nulledItemsIdx
                    nextNulledItemsIdx()
                    return i
                }

                if (attrIndex >= 0 && attrIndex < input.attributeCount) {

                    val name = input.getAttributeName(attrIndex)

                    return if (name.getNamespaceURI() == XMLConstants.XMLNS_ATTRIBUTE_NS_URI ||
                               name.prefix == XMLConstants.XMLNS_ATTRIBUTE ||
                               (name.prefix.isEmpty() && name.localPart == XMLConstants.XMLNS_ATTRIBUTE)) {
                        // Ignore namespace decls
                        readElement(desc)
                    } else {
                        desc.indexOf(name, true)
                    }
                }
                attrIndex = -1 // Ensure to reset here

                return super.readElement(desc)
            }

            override fun readElementEnd(desc: KSerialClassDesc): Int {
                nextNulledItemsIdx()
                return when {
                    nulledItemsIdx < seenItems.size -> nulledItemsIdx
                    else                            -> READ_DONE
                }
            }

            override fun readTaggedNotNullMark(tag: XmlTag): Boolean {
                return nulledItemsIdx < 0 // If we are not yet reading "missing values" we have no nulls
            }

            override fun doReadAttribute(tag: XmlTag): String {
                return input.getAttributeValue(attrIndex).also { attrIndex++ }
            }

            override fun <T> readSerializableValue(loader: KSerialLoader<T>): T {
                if (attrIndex >= 0) {
                    if (currentTag.useInfo.outputKind == OutputKind.Element) { // We are having an element masquerading as attribute.
                        // Whatever we do, increase the index
                        attrIndex++
                    }
                }
                return super.readSerializableValue(loader)
            }

            override fun readTaggedString(tag: XmlTag): String {
                if (nulledItemsIdx >= 0) {
                    return tag.useInfo.useAnnotations.firstOrNull<XmlDefault>()?.value ?: throw MissingFieldException(
                        "${tag.useInfo.declChildName}:${tag.useInfo.index}")
                }
                return super.readTaggedString(tag)
            }
        }

        internal inner class AnonymousListInput(desc: KSerialClassDesc,
                                                childName: QName,
                                                val polyInfo: PolyInfo?,
                                                var finished: Boolean,
                                                val childInfo: ChildInfo) :
            Base(desc, childName, null), XmlInput {

            lateinit var extInfo: ExtInfo

            override fun readBegin(tag: ElementXmlTag): KInput {
                return readBegin(tag.elementDesc, serialName, polyInfo, false, currentTag.asExtTag.extInfo)
            }

            override fun readElement(desc: KSerialClassDesc): Int {
                return when {
                    finished -> KInput.READ_DONE
                    else     -> {
                        finished = true; 1
                    }
                }
            }

            override fun <T> updateSerializableElementValue(desc: KSerialClassDesc,
                                                            index: Int,
                                                            loader: KSerialLoader<T>,
                                                            old: T): T {
                extInfo = Canary.extInfo(loader)
                val outKind = desc.outputKind(index, extInfo)
                if (outKind == OutputKind.Attribute && index > 1) {
                    throw IndexOutOfBoundsException("Cannot read more than one attribute of name $serialName")
                }
                return super.updateSerializableElementValue(desc, index, loader, old)
            }

            override fun doReadAttribute(tag: XmlTag): String {
                val expectedNS = serialName.namespaceURI
                val expectedName = serialName.localPart
                val allowEmpty = serialName.namespaceURI == expectedNS

                var index = -1
                for (i in 0 until input.attributeCount) {
                    if (input.getAttributeLocalName(i) == expectedName) {
                        val actualNS = input.getAttributeNamespace(i)
                        if (actualNS == expectedNS) {
                            index = i; break
                        } else if (actualNS.isEmpty() && allowEmpty) {
                            index = i
                        }
                    }
                }
                if (index < 0) throw SerializationException(
                    "No attribute for name $serialName found on element of name ${input.name}")

                return input.getAttributeValue(index)
            }

            override fun doGetTag(classDesc: KSerialClassDesc, index: Int): XmlTag {
                val tagName = serialName
                val suggestedOutputKind = when (index) {
                    0    -> classDesc.outputKind(index)
                    else -> if (childInfo.type.isPrimitive) {
                        OutputKind.Text
                    } else {
                        outputKind(childInfo)
                    }
                }
                val outputKind = when (suggestedOutputKind) {
                    OutputKind.Attribute -> OutputKind.Text
                    else                 -> suggestedOutputKind
                }

                return XmlTagImpl(classDesc, index, outputKind, tagName, useQName = serialName)
            }
        }

        internal inner class NamedListInput(desc: KSerialClassDesc, serialName: QName, childName: QName) :
            Base(desc, serialName, childName) {
            var childCount = 0

            override fun readElement(desc: KSerialClassDesc): Int {
                return when (input.nextTag()) {
                    EventType.END_ELEMENT -> KInput.READ_DONE
                    else                  -> ++childCount // This is important to ensure appending in the list.
                }
            }

            override fun doGetTag(classDesc: KSerialClassDesc, index: Int): XmlTag {
                // The classDesc only has indices 0 and 1. Just make sure to return the needed tag, rather than the "truth"
                return XmlTagImpl(classDesc, 1, OutputKind.Element, childName!!, useQName = childName)
            }
        }

        internal inner class PolymorphicInput(desc: KSerialClassDesc,
                                              val polyInfo: PolyInfo?,
                                              val transparent: Boolean = polyInfo != null) : Base(desc,
                                                                                                  QName(
                                                                                                      "--invalid--"),
                                                                                                  null) {
            override fun readBegin(tag: ElementXmlTag): KInput {
                return super.readBegin(tag)
            }

            override fun readElement(desc: KSerialClassDesc): Int {
                return KInput.READ_ALL
            }

            override fun doGetTag(classDesc: KSerialClassDesc, index: Int): XmlTag {
                return when (index) {
                    0    -> XmlTagImpl(classDesc, index, OutputKind.Attribute,
                                   QName("type"), useQName = QName("type"))
                    else -> XmlTagImpl(classDesc, index, OutputKind.Element,
                                             polyInfo?.tagName ?: childName ?: classDesc.getSafeTagName(index), useQName = polyInfo?.tagName ?: childName ?: classDesc.getSafeTagName(index))
                }
            }

            override fun doReadAttribute(tag: XmlTag): String {
                return if (!transparent) {
                    input.getAttributeValue(0)
                } else {
                    polyInfo?.kClass ?: input.name.localPart // Likely to fail unless the tagname matches the type
                }
            }

            override fun KSerialClassDesc.indexOf(name: QName, attr: Boolean): Int {
                return if (name.namespaceURI == "" && name.localPart == "type") 0 else 1
            }

            override fun <T> readSerializableValue(loader: KSerialLoader<T>): T {
                if (!transparent) input.nextTag()
                return super.readSerializableValue(loader)
            }

            override fun readEnd(desc: KSerialClassDesc) {
                if (!transparent) {
                    input.nextTag()
                    require(input.isEndElement())
                }
                super.readEnd(desc)
            }
        }

        internal fun XmlInput.readBegin(desc: KSerialClassDesc,
                                        tagName: QName,
                                        polyInfo: PolyInfo?,
                                        isReadingNulls: Boolean,
                                        extInfo: ExtInfo): KInput {

            return when (desc.kind) {
                KSerialClassKind.LIST,
                KSerialClassKind.MAP,
                KSerialClassKind.SET    -> {
                    val t = (this as XmlCommon<*>).myCurrentTag
                    t.useInfo.replaceUnknownOutputWith(OutputKind.Element)

                    val childName = t.useInfo.useAnnotations.getChildName()
                    return when (childName) {
                        null -> AnonymousListInput(desc, tagName, polyInfo, isReadingNulls, extInfo.childInfo[1])
                        else -> {
                            input.require(EventType.START_ELEMENT, tagName.namespaceURI, tagName.localPart)
                            NamedListInput(desc, tagName, childName)
                        }
                    }
                }

                KSerialClassKind.POLYMORPHIC,
                KSerialClassKind.SEALED -> {
                    PolymorphicInput(desc, polyInfo, tagName.copy(prefix = "") != input.name.copy(prefix = ""))
                }

                KSerialClassKind.CLASS,
                KSerialClassKind.OBJECT -> {
                    input.require(EventType.START_ELEMENT, tagName.namespaceURI, tagName.localPart)
                    Element(desc, tagName, null, extInfo = extInfo)
                }

                KSerialClassKind.ENTRY  -> TODO("Maps are not yet supported")//MapEntryWriter(currentTagOrNull)
                else                    -> throw SerializationException(
                    "Primitives are not supported at top-level")
            }
        }

    }
}

private fun defaultSerialContext() = SerialContext().apply {
    //    registerSerializer(ICompactFragment::class, ICompactFragmentSerializer())
    registerSerializer(CompactFragment::class, CompactFragmentSerializer)
}

fun Collection<Annotation>.getXmlSerialName(current: QName?): QName? {
    val serialName = firstOrNull<XmlSerialName>()
    return when {
        serialName == null -> null
        serialName.namespace == UNSET_ANNOTATION_VALUE
                           -> if (current == null) {
            QName(serialName.value)
        } else {
            QName(current.namespaceURI, serialName.value, current.prefix)
        }

        serialName.prefix == UNSET_ANNOTATION_VALUE
                           -> QName(serialName.namespace, serialName.value)

        else               -> QName(serialName.namespace, serialName.value, serialName.prefix)
    }
}

fun Collection<Annotation>.getChildName(): QName? {
    val childrenName = firstOrNull<XmlChildrenName>()
    return when {
        childrenName == null -> null
        childrenName.namespace == UNSET_ANNOTATION_VALUE
                             -> QName(childrenName.value)

        childrenName.prefix == UNSET_ANNOTATION_VALUE
                             -> QName(childrenName.namespace, childrenName.value)

        else                 -> QName(childrenName.namespace, childrenName.value,
                                                            childrenName.prefix)
    }
}

fun <T : Any> KClass<T>.getSerialName(serializer: KSerializer<*>?, prefix: String?): QName {
    return getSerialName(serializer).let {
        if (prefix == null) it else it.copy(prefix = prefix)
    }
}

fun <T : Any> KClass<T>.getSerialName(serializer: KSerializer<*>?): QName {
    return myAnnotations.getXmlSerialName(null)
           ?: serializer?.run { QName(serialClassDesc.name.substringAfterLast('.')) }
           ?: QName(name.substringAfterLast('.'))
}

fun <T : Any> KClass<T>.getChildName(): QName? {
    return myAnnotations.getChildName()
}

enum class OutputKind {
    Element, Attribute, Text, Unknown;

    fun matchesExpectationBy(expectedOutputKind: OutputKind): Boolean {
        return when (expectedOutputKind) {
            this    -> true
            Unknown -> true
            else    -> false
        }
    }
}

internal interface XmlTag {
    fun updateExtInfo(loader: KSerialLoader<*>): ExtXmlTag

    val typeInfo: TypeInfo
    val useInfo: UseInfo
    val asExtTag: ExtXmlTag
}

internal interface ExtXmlTag: XmlTag {
    val _extInfo: ExtInfo?
    val extInfo: ExtInfo get() = _extInfo!!
    override val typeInfo: ExtTypeInfo
}

internal inline fun <reified T: Annotation> ExtXmlTag.effectiveAnnotation(): T? {
    return useInfo.useAnnotations.firstOrNull<T>()
    ?: typeInfo.declAnnotations.firstOrNull<T>()
}

internal interface ElementXmlTag: ExtXmlTag {
    val elementDesc: KSerialClassDesc
}

internal class ElementTag(override val elementDesc: KSerialClassDesc, private val base: ExtXmlTag): ElementXmlTag, ExtXmlTag {
    override fun updateExtInfo(loader: KSerialLoader<*>) = base.updateExtInfo(loader)

    override val useInfo: UseInfo get() = base.useInfo

    override val asExtTag: ExtXmlTag get() = apply { base.asExtTag }

    override val _extInfo: ExtInfo? get() = base._extInfo

    override val typeInfo: ExtTypeInfo get() = base.typeInfo
}

internal data class XmlTagImpl(val desc: KSerialClassDesc,
                               override val index: Int,
                               var kind: OutputKind,
                               private var _name: QName,
                               override val childName: QName? = if (index < desc.associatedFieldsCount) desc.getAnnotationsForIndex(
                                         index).getChildName() else null,
                               var _polyInfo: PolyInfo? = null,
                               override var _extInfo: ExtInfo? = null,
                               override var useQName: QName?): XmlTag, ExtTypeInfo, UseInfo, ExtXmlTag {

    override fun updateExtInfo(loader: KSerialLoader<*>): ExtXmlTag = apply {
        _extInfo = Canary.extInfo(loader)
    }

    override fun replaceUnknownOutputWith(newKind: OutputKind) {
        if (kind==OutputKind.Unknown) kind = newKind
    }

    override val asExtTag: ExtXmlTag
        get() = apply { if (_extInfo==null) throw NullPointerException("No extra information was initialised") }

    override val tagName:QName = useQName ?: _extInfo?.classAnnotations?.firstOrNull<XmlSerialName>()?.toQName() ?: _name

    override val declAnnotations: List<Annotation> get() = extInfo.classAnnotations
    override val useAnnotations: List<Annotation> get() = desc.getAnnotationsForIndex(index)
    override val typeInfo: ExtTypeInfo get() = this
    override val useInfo: UseInfo get() = this

    override val typeName: String? get() = desc.name
    override val serialClassKind: KSerialClassKind get() = desc.kind
    override val declChildName: String get() = desc.getElementName(index)
    override val outputKind: OutputKind get() = kind
}

internal fun XmlSerialName.toQName() = QName(namespace, value, prefix)

internal fun XmlChildrenName.toQName() = QName(namespace, value, prefix)

internal data class PolyInfo(val kClass: String, val tagName: QName, val index: Int)

internal const val UNSET_ANNOTATION_VALUE = "ZXCVBNBVCXZ"

internal inline fun <reified T> Iterable<*>.firstOrNull(): T? {
    for (e in this) {
        if (e is T) return e
    }
    return null
}

private fun KSerialClassDesc.outputKind(index: Int, extInfo: ExtInfo? = null): OutputKind {
    // lists will always be elements
    if (index < associatedFieldsCount) {
        getAnnotationsForIndex(index).forEach {
            if (it is XmlChildrenName) return OutputKind.Element
            if (it is XmlElement) return if (it.value) OutputKind.Element else OutputKind.Attribute
            if (it is XmlValue && it.value) return OutputKind.Text
        }
    }

    val childInfo = extInfo?.childInfo?.let {
        if (index < it.size) it[index] else null
    }

    return outputKind(childInfo)
}

private fun KSerialClassDesc.outputKind(index: Int, childInfo: ChildInfo): OutputKind {
    // lists will always be elements
    if (index < associatedFieldsCount) {
        getAnnotationsForIndex(index).forEach {
            if (it is XmlChildrenName) return OutputKind.Element
            if (it is XmlElement) return if (it.value) OutputKind.Element else OutputKind.Attribute
            if (it is XmlValue && it.value) return OutputKind.Text
        }
    }

    return outputKind(childInfo)
}

private fun outputKind(baseInfo: BaseInfo? = null): OutputKind {

    val childType = when {
        baseInfo == null -> null
        else             -> baseInfo.type
    }

    return when (childType) {
        ChildType.CLASS   -> OutputKind.Element
        null,
        ChildType.UNKNOWN -> OutputKind.Unknown
        else              -> OutputKind.Attribute
    }
}

private fun KSerialClassDesc.isOptional(index: Int): Boolean {
    return getAnnotationsForIndex(index).firstOrNull<Optional>() != null
}

fun QName.copy(namespaceURI: String = this.namespaceURI,
                                     localPart: String = this.localPart,
                                     prefix: String = this.prefix) = QName(namespaceURI,
                                                                                                 localPart,
                                                                                                 prefix)

fun QName.copy(prefix: String = this.prefix) = if (prefix == this.prefix) this else QName(
    namespaceURI, localPart,
    prefix)

inline fun <reified T : Any> T.writeAsXML(out: XmlWriter) = XML().toXml(this, out)

@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any> T.writeAsXML(kClass: KClass<T>, out: XmlWriter) = XML().toXml(kClass, this, out)
