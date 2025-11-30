package jp.juggler.subwaytooter.api.entity

interface Mappable<out T> {
    val mapKey: T
}

// EntityUtil の parseMap() でマップを構築する際、マップのキーを返すインタフェース
