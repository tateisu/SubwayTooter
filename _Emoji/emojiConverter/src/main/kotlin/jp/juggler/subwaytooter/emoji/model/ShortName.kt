package jp.juggler.subwaytooter.emoji.model

import jp.juggler.subwaytooter.emoji.cast
import jp.juggler.subwaytooter.emoji.notEmpty


class ShortName(val cameFrom:String,val name:String) :Comparable<ShortName>{
	override fun equals(other: Any?): Boolean =
		name == other.cast<ShortName>()?.name

	override fun hashCode(): Int =
		name.hashCode()

	override fun toString(): String =
		"SN($cameFrom)$name"

	override fun compareTo(other: ShortName): Int =
		name.compareTo(other.name)
}

private val reColonHead = """\A:""".toRegex()
private val reColonTail = """:\z""".toRegex()
private val reNotCode = """[^\w\d+_]+""".toRegex()
private val reUnderTail = """_+\z""".toRegex()

fun String.toShortName(cameFrom:String) =
	toLowerCase()
		.replace(reColonHead, "")
		.replace(reColonTail, "")
		.replace(reNotCode, "_")
		.replace(reUnderTail,"")
		.notEmpty()?.let{ ShortName(cameFrom=cameFrom,it) }
