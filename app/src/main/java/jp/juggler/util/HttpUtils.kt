package jp.juggler.util

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

val MEDIA_TYPE_FORM_URL_ENCODED: MediaType =
    "application/x-www-form-urlencoded".toMediaType()

val MEDIA_TYPE_JSON: MediaType =
    "application/json;charset=UTF-8".toMediaType()

fun String.toFormRequestBody() = toRequestBody(MEDIA_TYPE_FORM_URL_ENCODED)

fun JsonObject.toRequestBody(mediaType: MediaType = MEDIA_TYPE_JSON): RequestBody =
    toString().toRequestBody(contentType = mediaType)

fun RequestBody.toPost(): Request.Builder =
    Request.Builder().post(this)

fun RequestBody.toPut(): Request.Builder =
    Request.Builder().put(this)

fun RequestBody.toDelete(): Request.Builder =
    Request.Builder().delete(this)

fun RequestBody.toPatch(): Request.Builder =
    Request.Builder().patch(this)

fun RequestBody.toRequest(methodArg: String): Request.Builder =
    Request.Builder().method(methodArg, this)

fun JsonObject.toPostRequestBuilder(): Request.Builder = toRequestBody().toPost()
fun JsonObject.toPutRequestBuilder(): Request.Builder = toRequestBody().toPut()
fun JsonObject.toDeleteRequestBuilder(): Request.Builder = toRequestBody().toDelete()
