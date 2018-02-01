@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.ByteSequence

class ApngAnimationControl internal constructor(src : ByteSequence) {
	
	companion object {
		const val PLAY_INDEFINITELY = 0
	}
	
	// This must equal the number of `fcTL` chunks.
	// 0 is not a valid value.
	// 1 is a valid value for a single-frame APNG.
	val numFrames : Int
	
	//  if it is 0, the animation should play indefinitely.
	// If nonzero, the animation should come to rest on the final frame at the end of the last play.
	val numPlays : Int
	
	init {
		numFrames = src.readInt32()
		numPlays = src.readInt32()
	}
	
	override fun toString() = "ApngAnimationControl(numFrames=$numFrames,numPlays=$numPlays)"
	
	val isFinite : Boolean
		get() = numPlays > PLAY_INDEFINITELY
}
