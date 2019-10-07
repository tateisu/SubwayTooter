package it.sephiroth.android.library.exif2.utils

internal fun <E> Collection<E>?.notEmpty() : Collection<E>? =
	if(this?.isNotEmpty() == true) this else null

internal fun <E> List<E>?.notEmpty() : List<E>? =
	if(this?.isNotEmpty() == true) this else null
