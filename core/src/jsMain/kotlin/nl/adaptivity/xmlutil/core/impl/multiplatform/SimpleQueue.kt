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

package nl.adaptivity.xmlutil.core.impl.multiplatform

actual class SimpleQueue<E> actual constructor() {
    private var data = js("[]")

    actual val size: Int
        get() = data.length as Int

    @Suppress("UnsafeCastFromDynamic")
    actual fun peekFirst(): E? = when (size) {
        0    -> null
        else -> data[0]
    }

    @Suppress("UnsafeCastFromDynamic")
    actual fun peekLast(): E? = when (size) {
        0    -> null
        else -> data[size - 1]
    }

    @Suppress("UnsafeCastFromDynamic")
    actual fun removeFirst(): E = data.shift()

    @Suppress("UnsafeCastFromDynamic")
    actual fun removeLast(): E = data.pop()

    actual fun addLast(e: E) {
        data.push(e)
    }

    actual fun add(element: E): Boolean {
        data.push(element)
        return true
    }

    actual fun clear() {
        data = js("[]")
    }

}