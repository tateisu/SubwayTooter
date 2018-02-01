package jp.juggler.apng

enum class ColorType(val num : Int) {
	GREY(0),
	RGB(2),
	INDEX(3),
	GREY_ALPHA(4),
	RGBA(6),
}

enum class CompressionMethod(val num : Int) {
	Standard(0)
}

enum class FilterMethod(val num : Int) {
	Standard(0)
}

enum class InterlaceMethod(val num : Int) {
	None(0),
	Standard(1)
}

enum class FilterType(val num : Int) {
	None(0),
	Sub(1),
	Up(2),
	Average(3),
	Paeth(4)
}

enum class DisposeOp(val num : Int) {
	None(0),
	Background(1),
	Previous(2)
}

enum class BlendOp(val num : Int) {
	Source(0),
	Over(1)
}
