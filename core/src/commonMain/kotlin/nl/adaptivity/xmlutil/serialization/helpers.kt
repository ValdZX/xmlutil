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

package nl.adaptivity.xmlutil.serialization

import kotlinx.serialization.*
import kotlinx.serialization.internal.StringSerializer
import nl.adaptivity.xmlutil.impl.CharArraySequence
import nl.adaptivity.xmlutil.multiplatform.toCharArray

object CharArrayAsStringSerializer : KSerializer<CharArray> {
    override val descriptor = simpleSerialClassDesc<CharArray>()

    override fun serialize(encoder: Encoder, obj: CharArray) = encoder.encodeString(
        CharArraySequence(obj).toString())

    override fun deserialize(decoder: Decoder): CharArray = decoder.decodeString().toCharArray()

    override fun patch(decoder: Decoder, old: CharArray): CharArray = deserialize(decoder)
}

@Suppress("UNCHECKED_CAST")
fun Decoder.readNullableString(): String? = decodeNullableSerializableValue(StringSerializer as DeserializationStrategy<String?>)

@Suppress("UNCHECKED_CAST")
fun CompositeDecoder.readNullableString(desc: SerialDescriptor, index: Int): String? = decodeNullableSerializableElement(desc, index, StringSerializer as DeserializationStrategy<String?>)

fun CompositeEncoder.writeNullableStringElementValue(desc: SerialDescriptor, index: Int, value: String?) = encodeNullableSerializableElement(desc, index, StringSerializer, value)


inline fun DeserializationStrategy<*>.readElements(input: CompositeDecoder, body: (Int) -> Unit) {
    var elem = input.decodeElementIndex(descriptor)
    while (elem >= 0) {
        body(elem)
        elem = input.decodeElementIndex(descriptor)
    }
}

inline fun <T> Decoder.readBegin(desc: SerialDescriptor, body: CompositeDecoder.(desc: SerialDescriptor) -> T):T {
    val input = beginStructure(desc)
    return input.body(desc).also {
        input.endStructure(desc)
    }
}

inline fun Encoder.writeStructure(desc: SerialDescriptor, body: CompositeEncoder.(desc: SerialDescriptor) -> Unit) {
    val output = beginStructure(desc)
    try {
        output.body(desc)
    } finally {
        output.endStructure(desc)
    }
}