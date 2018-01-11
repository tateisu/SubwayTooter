package jp.juggler.subwaytooter.api.entity

interface Mappable<out T> {
	val mapKey : T
}