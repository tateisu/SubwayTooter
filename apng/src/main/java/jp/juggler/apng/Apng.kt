package jp.juggler.apng

class Apng {
    var header: ApngImageHeader? = null
    var background: ApngBackground? = null
    var animationControl: ApngAnimationControl? = null
    internal var palette: ApngPalette? = null
    internal var transparentColor: ApngTransparentColor? = null
}
