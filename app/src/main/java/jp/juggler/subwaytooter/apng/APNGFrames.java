package jp.juggler.subwaytooter.apng;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.ellerton.japng.PngScanlineBuffer;
import net.ellerton.japng.argb8888.Argb8888Bitmap;
import net.ellerton.japng.argb8888.Argb8888Processor;
import net.ellerton.japng.argb8888.Argb8888Processors;
import net.ellerton.japng.argb8888.Argb8888ScanlineProcessor;
import net.ellerton.japng.argb8888.BasicArgb8888Director;
import net.ellerton.japng.chunks.PngAnimationControl;
import net.ellerton.japng.chunks.PngFrameControl;
import net.ellerton.japng.chunks.PngHeader;
import net.ellerton.japng.error.PngException;
import net.ellerton.japng.reader.DefaultPngChunkReader;
import net.ellerton.japng.reader.PngReadHelper;

import java.io.InputStream;
import java.util.ArrayList;

import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.util.LogCategory;

// APNGを解釈した結果を保持する
// (フレーム数分のbitmapと時間情報)

@SuppressWarnings({ "WeakerAccess", "unused" })
public class APNGFrames {
	
	
	static final LogCategory log = new LogCategory("APNGFrames");
	
	// フレーム計算中に使われる
	private PngHeader header;
	private Argb8888ScanlineProcessor scanlineProcessor;
	private Bitmap canvasBitmap;
	private Canvas canvas;
	private Paint srcModePaint;
	
	// 各フレームはDrawableを持つ。フレーム制御情報にはコマの時間などが含まれる
	long time_total = 0L;
	private PngAnimationControl animationControl;
	private ArrayList< Frame > frames;
	
	public static class Frame {
		public final Bitmap bitmap;
		public final long time_start;
		public final long time_width;
		
		public Frame( Bitmap bitmap, long time_start, long time_width ){
			this.bitmap = bitmap;
			this.time_start = time_start;
			this.time_width = time_width;
		}
	}
	
	/**
	 * Keep a 1x1 transparent image around as reference for creating a scaled starting bitmap.
	 * Considering this because of some reported OutOfMemory errors, and this post:
	 * <p>
	 * http://stackoverflow.com/a/8527745/963195
	 * <p>
	 * Specifically: "NEVER use Bitmap.createBitmap(width, height, Config.ARGB_8888). I mean NEVER!"
	 * <p>
	 * Instead the 1x1 image (68 bytes of resources) is scaled up to the needed size.
	 * Whether or not this fixes the OOM problems is TBD...
	 */
	static Bitmap referenceImage = null;
	
	private static Bitmap getReferenceImage( Resources resources ){
		if( referenceImage == null ){
			referenceImage = BitmapFactory.decodeResource( resources, R.drawable.onepxtransparent );
		}
		return referenceImage;
	}
	
	///////////////////////////////////////////////////////////////
	
	public static APNGFrames parseAPNG( Context context, InputStream is, int size_max) throws PngException{
		Argb8888Processor< APNGFrames > processor = new Argb8888Processor<>( new APNGParseEventHandler( context ,size_max) );
		APNGFrames result = PngReadHelper.read( is, new DefaultPngChunkReader<>( processor ) );
		if( result != null ) result.onParseComplete();
		return result;
	}
	
	//	public static Drawable readDrawable( Context context, int id ) throws PngException, IOException{
	//		final TypedValue value = new TypedValue();
	//		InputStream is = context.getResources().openRawResource( id, value );
	//		try{
	//			return readDrawable( context, is );
	//		}finally{
	//			IOUtils.closeQuietly( is );
	//		}
	//	}
	
	
	// APNGじゃなかった場合に使われる
	@Nullable private Bitmap mBitmapNonAnimation;
	
	int size_max;
	
	public APNGFrames( @NonNull Bitmap bitmap ){
		this.mBitmapNonAnimation = bitmap;
	}
	
	public APNGFrames(
		@NonNull Resources resources
		, @NonNull PngHeader header
		, @NonNull Argb8888ScanlineProcessor scanlineProcessor
		, @NonNull PngAnimationControl animationControl
		, int size_max
	){
		this.header = header;
		this.scanlineProcessor = scanlineProcessor;
		this.animationControl = animationControl;
		this.size_max = size_max;
		
		//this.canvasBitmap = Bitmap.createBitmap(this.header.width, this.header.height, Bitmap.Config.ARGB_8888);
		this.canvasBitmap = Bitmap.createScaledBitmap( getReferenceImage( resources ), this.header.width, this.header.height, false );
		this.canvas = new Canvas( this.canvasBitmap );
		this.frames = new ArrayList<>( animationControl.numFrames );
		this.srcModePaint = new Paint();
		this.srcModePaint.setXfermode( new PorterDuffXfermode( PorterDuff.Mode.SRC ) );
		
	}
	
	public void onParseComplete(){
		if( frames != null && frames.size() <= 1 ){
			mBitmapNonAnimation = toBitmap( scanlineProcessor.getBitmap(), size_max );
		}
		if( canvasBitmap != null ){
			canvasBitmap.recycle();
			canvasBitmap = null;
		}
	}
	
	public void dispose(){
		if( canvasBitmap != null ){
			canvasBitmap.recycle();
		}
		if( mBitmapNonAnimation != null ){
			mBitmapNonAnimation.recycle();
		}
		if( frames != null ){
			for( Frame f : frames ){
				f.bitmap.recycle();
			}
		}
	}
	
	private PngFrameControl currentFrame;
	
	// フレームが追加される
	public Argb8888ScanlineProcessor beginFrame( PngFrameControl frameControl ){
		currentFrame = frameControl;
		return scanlineProcessor.cloneWithSharedBitmap( header.adjustFor( currentFrame ) );
	}
	
	// フレームが追加される
	public void completeFrame( Argb8888Bitmap frameImage ){
		
		Bitmap frame = toBitmap( frameImage );
		// boolean isFull = currentFrame.height == header.height && currentFrame.width == header.width;
		Paint paint = null;
		Drawable d;
		Bitmap previous = null;
		
		// Capture the current bitmap region IF it needs to be reverted after rendering
		if( 2 == currentFrame.disposeOp ){
			previous = Bitmap.createBitmap( canvasBitmap, currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height ); // or could use from frames?
			//System.out.println(String.format("Captured previous %d x %d", previous.getWidth(), previous.getHeight()));
		}
		
		if( 0 == currentFrame.blendOp ){ // SRC_OVER, not blend (for blend, leave paint null)
			//paint = new Paint();
			//paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
			paint = srcModePaint;
		}
		
		// Draw the new frame into place
		canvas.drawBitmap( frame, currentFrame.xOffset, currentFrame.yOffset, paint );
		
		// Extract a drawable from the canvas. Have to copy the current bitmap.
		// Store the drawable in the sequence of frames
		long time_start = time_total;
		long time_width = currentFrame.getDelayMilliseconds();
		if( time_width <= 0L ) time_width = 1L;
		time_total = time_start + time_width;
		
		frames.add( new Frame(
			scaleBitmap( size_max, canvasBitmap.copy( Bitmap.Config.ARGB_8888, false ) )
			, time_start
			, time_width
		) );
		
		// Now "dispose" of the frame in preparation for the next.
		
		// https://wiki.mozilla.org/APNG_Specification#.60fcTL.60:_The_Frame_Control_Chunk
		//
		// APNG_DISPOSE_OP_NONE: no disposal is done on this frame before rendering the next; the contents of the output buffer are left as is.
		// APNG_DISPOSE_OP_BACKGROUND: the frame's region of the output buffer is to be cleared to fully transparent black before rendering the next frame.
		// APNG_DISPOSE_OP_PREVIOUS: the frame's region of the output buffer is to be reverted to the previous contents before rendering the next frame.
		//
		switch( currentFrame.disposeOp ){
		case 1: // APNG_DISPOSE_OP_BACKGROUND
			//System.out.println(String.format("Frame %d clear background (full=%s, x=%d y=%d w=%d h=%d) previous=%s", currentFrame.sequenceNumber,
			//        isFull, currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height, previous));
			//if (true || isFull) {
			canvas.drawColor( 0, PorterDuff.Mode.CLEAR ); // Clear to fully transparent black
			//                } else {
			//                    Rect rt = new Rect(currentFrame.xOffset, currentFrame.yOffset, currentFrame.width+currentFrame.xOffset, currentFrame.height+currentFrame.yOffset);
			//                    paint = new Paint();
			//                    paint.setColor(0);
			//                    paint.setStyle(Paint.Style.FILL);
			//                    canvas.drawRect(rt, paint);
			//                }
			break;
		
		case 2: // APNG_DISPOSE_OP_PREVIOUS
			//System.out.println(String.format("Frame %d restore previous (full=%s, x=%d y=%d w=%d h=%d) previous=%s", currentFrame.sequenceNumber,
			//        isFull, currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height, previous));
			
			// Put the original section back
			if( null != previous ){
				//paint = new Paint();
				//paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
				paint = srcModePaint;
				canvas.drawBitmap( previous, currentFrame.xOffset, currentFrame.yOffset, paint );
				
				//System.out.println("  Restored previous "+previous.getWidth()+" x "+previous.getHeight());
				previous.recycle();
			}else{
				System.out.println( "  Huh, no previous?" );
			}
			break;
		
		case 0: // APNG_DISPOSE_OP_NONE
		default: // Default should never happen
			// do nothing
			//System.out.println("Frame "+currentFrame.sequenceNumber+" do nothing dispose");
			break;
			
		}
		
		currentFrame = null;
	}
	
	///////////////////////////////////////////////////////////////
	
	// 再生速度の調整
	private float durationScale = 1f;
	
	public float getDurationScale(){
		return durationScale;
	}
	
	public void setDurationScale( float durationScale ){
		this.durationScale = durationScale;
	}
	
	public boolean isSingleFrame(){
		return animationControl == null || 1 == animationControl.numFrames;
	}
	
	public int getNumFrames() {
		return animationControl == null ? 1 : animationControl.numFrames;
	}
	
	static final long DELAY_AFTER_END = 3000L;
	
	public static class FindFrameResult {
		public Bitmap bitmap;
		public long delay;
	}
	
	public void findFrame( @NonNull FindFrameResult result, long t ){
		
		if( mBitmapNonAnimation != null ){
			result.bitmap = mBitmapNonAnimation;
			result.delay = Long.MAX_VALUE;
			return;
		}

		int frame_count = frames.size();
		
		boolean isFinite = ! animationControl.loopForever();
		int repeatSequenceCount = isFinite ? animationControl.numPlays : 1;
		long end_wait = ( isFinite ? DELAY_AFTER_END : 0L );
		long loop_total = ( time_total * repeatSequenceCount ) + end_wait;
		if( loop_total <= 0 ) loop_total = 1;

		long tf = (long)(0.5f + t < 0f ? 0f : t / durationScale );
		
		// 全体の繰り返し時刻で余りを計算
		long tl = tf % loop_total;
		if( tl >= loop_total - end_wait ){
			// 終端で待機状態
			result.bitmap = frames.get( frame_count - 1 ).bitmap;
			result.delay = (long)(0.5f + ( loop_total - tl ) * durationScale );
			return;
		}
		// １ループの繰り返し時刻で余りを計算
		long tt = tl % time_total;
		
		// フレームリストを時刻で二分探索
		int s = 0, e = frame_count;
		while( e - s > 1 ){
			int mid = ( s + e ) >> 1;
			Frame frame = frames.get( mid );
			//log.d("s=%d,m=%d,e=%d tt=%d,fs=%s,fe=%d",s,mid,e,tt,frame.time_start,frame.time_start+frame.time_width );
			if( tt < frame.time_start ){
				e = mid;
			}else if( tt >= frame.time_start + frame.time_width ){
				s = mid + 1;
			}else{
				s = mid;
				break;
			}
		}
		s = s < 0 ? 0 : s >= frame_count - 1 ? frame_count - 1 : s;
		Frame frame = frames.get( s );
		long delay = frame.time_start + frame.time_width - tt;
		result.bitmap = frames.get( s ).bitmap;
		result.delay = (long)(0.5f + durationScale * ( delay < 0f ? 0f : delay ) );
		
		//log.d("findFrame tf=%d,tl=%d/%d,tt=%d/%d,s=%d,w=%d",tf,tl,loop_total,tt,time_total,s,frame.time_width);
	}
	
	//	// AnimationDrawableを合成する。AnimationDrawableは複数のフレームを持つ。
	//	public AnimationDrawable assemble(){
	//		// FIXME: handle special case of one frame animation as a plain ImageView
	//		boolean isFinite = ! animationControl.loopForever();
	//		AnimationDrawable ad = new AnimationDrawable();
	//		ad.setOneShot( isFinite );
	//
	//		// The AnimationDrawable doesn't support a repeat count so add
	//		// frames as required. At least the frames can re-use drawables.
	//		int repeatSequenceCount = isFinite ? animationControl.numPlays : 1;
	//
	//		for( int i = 0 ; i < repeatSequenceCount ; i++ ){
	//			for( Frame frame : frames ){
	//				ad.addFrame( frame.drawable, (int)(0.5f + durationScale * frame.control.getDelayMilliseconds() ) );
	//			}
	//		}
	//		return ad;
	//	}
	
	static Bitmap scaleBitmap( int size_max,Bitmap src){
		if( src == null ) return null;
		
		int src_w = src.getWidth();
		int src_h = src.getHeight();
		if( src_w <= size_max && src_h <= size_max ) return src;
		
		int dst_w;
		int dst_h;
		if( src_w >= src_h  ){
			dst_w = size_max;
			dst_h = (int)(0.5f+ src_h * size_max / (float)src_w);
		}else{
			dst_h = size_max;
			dst_w = (int)(0.5f+ src_w * size_max / (float)src_h);
		}
		Rect rect_src = new Rect( 0,0,src_w,src_h );
		Rect rect_dst = new Rect( 0,0,dst_w,dst_h );
		
		Bitmap b2 = Bitmap.createBitmap(dst_w,dst_h,Bitmap.Config.ARGB_8888 );
		Canvas canvas = new Canvas( b2 );
		Paint srcModePaint = new Paint();
		srcModePaint.setXfermode( new PorterDuffXfermode( PorterDuff.Mode.SRC ) );
		canvas.drawBitmap( src,rect_src,rect_dst,srcModePaint );
		src.recycle();
		return b2;
	}
	
	static Bitmap toBitmap( Argb8888Bitmap src ){
		int offset = 0;
		int stride = src.width;
		return Bitmap.createBitmap( src.getPixelArray(), offset, stride, src.width, src.height, Bitmap.Config.ARGB_8888 );
	}
	
	static Bitmap toBitmap( Argb8888Bitmap src ,int size_max){
		int offset = 0;
		int stride = src.width;
		Bitmap bitmap = Bitmap.createBitmap( src.getPixelArray(), offset, stride, src.width, src.height, Bitmap.Config.ARGB_8888 );
		return scaleBitmap(size_max,bitmap);
	}
	
	
	static class APNGParseEventHandler extends BasicArgb8888Director< APNGFrames > {
		
		@NonNull final Context context;
		int size_max;
		
		// 作成
		public APNGParseEventHandler( @NonNull Context context, int size_max ){
			this.context = context;
			this.size_max = size_max;
		}
		
		PngHeader header;
		PngScanlineBuffer buffer;
		Argb8888Bitmap pngBitmap;
		
		// ヘッダが分かった
		@Override
		public void receiveHeader( PngHeader header, PngScanlineBuffer buffer ) throws PngException{
			
			this.header = header;
			this.buffer = buffer;
			this.pngBitmap = new Argb8888Bitmap( header.width, header.height );
			this.scanlineProcessor = Argb8888Processors.from( header, buffer, pngBitmap );
			
		}
		
		// デフォルト画像の手前
		@Override
		public Argb8888ScanlineProcessor beforeDefaultImage(){
			return scanlineProcessor;
		}
		
		// デフォルト画像が分かった
		// おそらく receiveAnimationControl より先に呼ばれる
		Bitmap defaultImage = null;
		
		@Override
		public void receiveDefaultImage( Argb8888Bitmap srcDefaultImage ){
			this.defaultImage = toBitmap( srcDefaultImage, size_max );
		}
		
		// アニメーション制御情報が分かった
		APNGFrames animationComposer = null;
		
		@Override
		public void receiveAnimationControl( PngAnimationControl animationControl ){
			this.animationComposer = new APNGFrames( context.getResources(), header, scanlineProcessor, animationControl, size_max );
		}
		
		public boolean isAnimated(){
			return animationComposer != null;
		}
		
		@Override
		public boolean wantDefaultImage(){
			return ! isAnimated();
		}
		
		@Override
		public boolean wantAnimationFrames(){
			return true; // isAnimated;
		}
		
		// フレーム制御情報が分かった
		@Override
		public Argb8888ScanlineProcessor receiveFrameControl( PngFrameControl frameControl ){
			if( ! isAnimated() ) throw new RuntimeException( "not animation image" );
			return animationComposer.beginFrame( frameControl );
		}
		
		// フレーム画像が分かった
		@Override
		public void receiveFrameImage( Argb8888Bitmap frameImage ){
			if( ! isAnimated() ) throw new RuntimeException( "not animation image" );
			animationComposer.completeFrame( frameImage );
		}
		
		// 画像を取得する
		@Override
		public APNGFrames getResult(){
			return animationComposer != null ? animationComposer : new APNGFrames( defaultImage );
		}
		
	}
	
}
