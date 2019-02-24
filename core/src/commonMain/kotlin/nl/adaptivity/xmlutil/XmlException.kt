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

package nl.adaptivity.xmlutil

import nl.adaptivity.xmlutil.multiplatform.IOException


/**
 * Simple exception for xml related things.
 * Created by pdvrieze on 15/11/15.
 */
open class XmlException : IOException {

    constructor()

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(cause: Throwable) : super(cause)

    constructor(message: String, reader: XmlReader, cause: Throwable) :
        super("${reader.locationInfo ?: "Unknown position"} - $message", cause)

    @Deprecated("Only use in Java, in kotlin just throw", ReplaceWith("throw this"))
    fun doThrow(): Nothing { throw this }
}