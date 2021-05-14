/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.content.*
import io.ktor.client.features.observer.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.reflect.*

public class ObservableBody(
    public val body: Any,
    public val bodyType: TypeInfo,
    public val listener: ProgressListener
)

public inline fun <reified T : Any> observableBodyOf(body: T, noinline listener: ProgressListener): ObservableBody {
    return ObservableBody(body, typeInfo<T>(), listener)
}

public fun HttpResponse.observable(listener: ProgressListener): HttpResponse {
    val observableByteChannel = content.observable(coroutineContext, contentLength(), listener)
    return call.wrapWithContent(observableByteChannel).response
}
