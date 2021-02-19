package jp.juggler.subwaytooter.emoji.model

import jp.juggler.subwaytooter.emoji.cast

class ResName(val cameFrom:String,val name:String):Comparable<ResName>{
	override fun equals(other: Any?) : Boolean =
		name == other?.cast<ResName>()?.name

	override fun hashCode(): Int =
		name.hashCode()

	override fun toString(): String =
		"RN($cameFrom)$name"

	override fun compareTo(other: ResName): Int =
		name.compareTo(other.name)
}

