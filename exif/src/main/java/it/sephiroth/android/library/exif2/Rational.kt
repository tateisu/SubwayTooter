/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.sephiroth.android.library.exif2

/**
 * The rational data type of EXIF tag. Contains a pair of longs representing the
 * numerator and denominator of a Rational number.
 */
class Rational(
	val numerator : Long=0,//the numerator of the rational.
	val denominator : Long =1//the denominator of the rational
	
) {
	// copy from a Rational.
	constructor(r : Rational) :this(
		numerator = r.numerator,
		denominator = r.denominator
	)

	// Gets the rational value as type double.
	// Will cause a divide-by-zero error if the denominator is 0.
	fun toDouble() : Double = numerator.toDouble() / denominator.toDouble()
	
	override fun toString() : String = "$numerator/$denominator"
	
	override fun equals(other : Any?) : Boolean {
		return when {
			other === null -> false
			other === this -> true
			other is Rational -> numerator == other.numerator && denominator == other.denominator
			else -> false
		}
	}
	
	
	override fun hashCode() : Int =
		31 * numerator.hashCode() + denominator.hashCode()
}
