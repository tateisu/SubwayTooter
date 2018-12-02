package jp.juggler.util

import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

val MEDIA_TYPE_FORM_URL_ENCODED : MediaType =
	MediaType.get("application/x-www-form-urlencoded")

val MEDIA_TYPE_JSON : MediaType =
	MediaType.get("application/json;charset=UTF-8")

fun String.toRequestBody(mediaType : MediaType = MEDIA_TYPE_FORM_URL_ENCODED) : RequestBody =
	RequestBody.create(mediaType, this)

fun JSONObject.toRequestBody(mediaType : MediaType = MEDIA_TYPE_JSON) : RequestBody =
	RequestBody.create(mediaType, this.toString())

fun RequestBody.toPost() : Request.Builder =
	Request.Builder().post(this)

fun RequestBody.toPut() :Request.Builder =
	Request.Builder().put(this)

// fun RequestBody.toDelete():Request.Builder  =
// Request.Builder().delete(this)

fun RequestBody.toPatch() :Request.Builder =
	Request.Builder().patch(this)

fun RequestBody.toRequest(methodArg : String) :Request.Builder =
	Request.Builder().method(methodArg, this)

fun JSONObject.toPostRequestBuilder() : Request.Builder =
	Request.Builder().post(RequestBody.create(MEDIA_TYPE_JSON, this.toString()))

fun JSONObject.toPutRequestBuilder() : Request.Builder =
	Request.Builder().put(RequestBody.create(MEDIA_TYPE_JSON, this.toString()))
