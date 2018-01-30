package jp.juggler.apng

interface ApngDecoderCallback {
	
	// called for non-fatal warning
	fun onApngWarning(message : String)
	
	// called for debug message
	fun onApngDebug(message : String) {}
	
	fun canApngDebug():Boolean = false
	
	// called when PNG image header is detected.
	fun onHeader(apng : Apng, header : ApngImageHeader)
	
	// called when APNG Animation Control is detected.
	fun onAnimationInfo(
		apng : Apng,
		header : ApngImageHeader,
		animationControl : ApngAnimationControl
	)
	
	// called when default image bitmap was rendered.
	fun onDefaultImage(apng : Apng, bitmap : ApngBitmap)
	
	// called when APNG Frame Control is detected and its bitmap was rendered.
	// its bitmap may same to default image for first frame.
	// ( in this case, both of onDefaultImage and onAnimationFrame are called for same bitmap)
	fun onAnimationFrame(apng : Apng, frameControl : ApngFrameControl, frameBitmap : ApngBitmap)
	
}
