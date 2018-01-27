package jp.juggler.apng

interface ApngDecoderCallback{
    fun onHeader(apng: Apng, header: ApngImageHeader)
    fun onAnimationInfo(apng: Apng, animationControl: ApngAnimationControl)
    fun onDefaultImage(apng: Apng, bitmap: ApngBitmap)
    fun onAnimationFrame(apng: Apng, frameControl: ApngFrameControl, bitmap: ApngBitmap)
    fun log(message:String)
}
