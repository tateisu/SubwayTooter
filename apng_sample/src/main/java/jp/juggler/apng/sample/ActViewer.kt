package jp.juggler.apng.sample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.TextView
import jp.juggler.apng.ApngFrames
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

class ActViewer : AppCompatActivity() {
	
	companion object {
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
		
		launch(UI) {
			try {
				if(isDestroyed) return@launch
				
				val apngFrames = async(CommonPool) {
					resources.openRawResource(resId).use {
						ApngFrames.parseApng(it, 1024)
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
}