package com.constrakr.network

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.util.UUID

/** Moshi cannot serialize java.util.UUID without an explicit adapter. */
object UuidJsonAdapter {
    @ToJson
    fun toJson(value: UUID): String = value.toString()

    @FromJson
    fun fromJson(value: String): UUID = UUID.fromString(value)
}
