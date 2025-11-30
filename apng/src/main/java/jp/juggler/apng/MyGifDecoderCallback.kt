package jp.juggler.apng

interface MyGifDecoderCallback {
    fun onGifWarning(message: String)
    fun onGifDebug(message: String)
    fun canGifDebug(): Boolean
    fun onGifHeader(header: ApngImageHeader)
    fun onGifAnimationInfo(header: ApngImageHeader, animationControl: ApngAnimationControl)
    fun onGifAnimationFrame(frameControl: ApngFrameControl, frameBitmap: ApngBitmap)
}
