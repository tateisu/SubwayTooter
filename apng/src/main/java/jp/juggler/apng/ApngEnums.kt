package jp.juggler.apng

enum class ColorType(val num:Int ){
    GREY(0),
    RGB ( 2),
    INDEX( 3),
    GREY_ALPHA ( 4),
    RGBA ( 6),
}

enum class CompressionMethod(val num:Int ){
    Standard(0)
}

enum class FilterMethod(val num:Int ){
    Standard(0)
}

enum class InterlaceMethod(val num:Int ){
    None(0),
    Standard(1)
}

enum class FilterType(val num:Int ){
    None(0),
    Sub(1),
    Up(2),
    Average(3),
    Paeth(4)
}

enum class DisposeOp(val num :Int){
    //no disposal is done on this frame before rendering the next; the contents of the output buffer are left as is.
    None(0),
    // the frame's region of the output buffer is to be cleared to fully transparent black before rendering the next frame.
    Background(1),
    // the frame's region of the output buffer is to be reverted to the previous contents before rendering the next frame.
    Previous(2)
}

enum class BlendOp(val num :Int){
    // all color components of the frame, including alpha, overwrite the current contents of the frame's output buffer region.
    Source(0),
    //  the frame should be composited onto the output buffer based on its alpha, using a simple OVER operation as described in the "Alpha Channel Processing" section of the PNG specification [PNG-1.2].
    Over(1)
}
