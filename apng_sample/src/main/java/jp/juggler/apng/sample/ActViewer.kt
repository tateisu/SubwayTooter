package jp.juggler.apng.sample

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.TextView
import jp.juggler.apng.ApngFrames
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import java.io.File
import java.io.FileOutputStream

class ActViewer : AppCompatActivity() {
	
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
	
	override fun onCreate(savedInstanceState : Bundle?) {
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
		
		launch(UI) {
			try {
				if(isDestroyed) return@launch
				
				val apngFrames = async(CommonPool) {
					resources.openRawResource(resId).use {
						ApngFrames.parseApng(
							it,
							1024,
							debug = true
						)
					}
				}.await()
				
				
				
				
				if(isDestroyed) {
					apngFrames.dispose()
				} else {
					apngView.visibility = View.VISIBLE
					tvError.visibility = View.GONE
					apngView.apngFrames = apngFrames
				}
			} catch(ex : Throwable) {
				ex.printStackTrace()
				Log.e(ActList.TAG, "load error: ${ex.javaClass.simpleName} ${ex.message}")
				
				val message = "%s %s".format(ex.javaClass.simpleName, ex.message)
				if(! isDestroyed) {
					apngView.visibility = View.GONE
					tvError.visibility = View.VISIBLE
					tvError.text = message
				}
			}
		}
	}
	
	override fun onDestroy() {
		super.onDestroy()
		apngView.apngFrames?.dispose()
	}
	
	private fun save(apngFrames:ApngFrames){
		val title = this.title
		launch(CommonPool){
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