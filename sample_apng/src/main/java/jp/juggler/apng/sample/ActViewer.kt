package jp.juggler.apng.sample

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.TextView
import jp.juggler.apng.ApngFrames
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.CoroutineContext

class ActViewer : AppCompatActivity() , CoroutineScope {
	
	companion object {
		const val TAG="ActViewer"
		const val EXTRA_RES_ID = "res_id"
		const val EXTRA_CAPTION = "caption"
		
		fun open(context : Context, resId : Int, caption : String) {
			val intent = Intent(context, ActViewer::class.java)
			intent.putExtra(EXTRA_RES_ID, resId)
			intent.putExtra(EXTRA_CAPTION, caption)
			context.startActivity(intent)
		}
	}
	
	private lateinit var apngView : ApngView
	private lateinit var tvError : TextView
	
	private lateinit var activityJob: Job
	
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.Main + activityJob

	override fun onCreate(savedInstanceState : Bundle?) {
		activityJob = Job()
		super.onCreate(savedInstanceState)
		
		val intent = this.intent
		val resId = intent.getIntExtra(EXTRA_RES_ID, 0)
		this.title = intent.getStringExtra(EXTRA_CAPTION) ?: "?"
		
		setContentView(R.layout.act_apng_view)
		this.apngView = findViewById(R.id.apngView)
		this.tvError = findViewById(R.id.tvError)
		
		apngView.setOnLongClickListener {
			val apngFrames = apngView.apngFrames

			if( apngFrames != null ){
				save(apngFrames)
			}
			
			return@setOnLongClickListener true
		}
		
		launch{
			var apngFrames : ApngFrames? = null
			try {
				
				apngFrames = async(Dispatchers.IO) {
					resources.openRawResource(resId).use {
						ApngFrames.parseApng(
							it,
							1024,
							debug = true
						)
					}
				}.await()
				
				apngView.visibility = View.VISIBLE
				tvError.visibility = View.GONE
				apngView.apngFrames = apngFrames
				apngFrames = null
				
			} catch(ex : Throwable) {
				ex.printStackTrace()
				Log.e(ActList.TAG, "load error: ${ex.javaClass.simpleName} ${ex.message}")
				
				val message = "%s %s".format(ex.javaClass.simpleName, ex.message)
				if(! isDestroyed) {
					apngView.visibility = View.GONE
					tvError.visibility = View.VISIBLE
					tvError.text = message
				}
			}finally{
				apngFrames?.dispose()
			}
		}
	}
	
	override fun onDestroy() {
		super.onDestroy()
		apngView.apngFrames?.dispose()
		activityJob.cancel()
	}
	
	private fun save(apngFrames:ApngFrames){
		val title = this.title
		
		launch(Dispatchers.IO){
			val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
			dir.mkdirs()
			if(! dir.exists() ) {
				Log.e(TAG, "Directory not exists: ${dir}")
				return@launch
			}
			val frames = apngFrames.frames
			if( frames == null ){
				Log.e(TAG, "missing frames")
				return@launch
			}
			var i=0
			for( f in frames) {
				Log.d(TAG, "${title}[${i}] timeWidth=${f.timeWidth}")
				val bitmap = f.bitmap
				
				FileOutputStream( File(dir,"${title}_${i}.png")).use{ fo ->
					bitmap.compress (Bitmap.CompressFormat.PNG,100,fo)
				}
				
				++i
			}
		}
	}
}