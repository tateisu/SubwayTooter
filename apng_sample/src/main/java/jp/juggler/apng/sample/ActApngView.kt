package jp.juggler.apng.sample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.TextView
import jp.juggler.apng.ApngFrames
import kotlin.concurrent.thread

class ActApngView : AppCompatActivity() {
	
	companion object {
		const val EXTRA_RES_ID = "res_id"
		const val EXTRA_CAPTION = "caption"
		
		fun open(context: Context, resId:Int, caption:String){
			val intent =Intent( context, ActApngView::class.java)
			intent.putExtra(EXTRA_RES_ID,resId)
			intent.putExtra(EXTRA_CAPTION,caption)
			context.startActivity(intent)
		}
	}
	
	private lateinit var handler : Handler
	private lateinit var apngView : ApngView
	private lateinit var tvError : TextView
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		this.handler = Handler()

		val intent = this.intent
		val resId = intent.getIntExtra(EXTRA_RES_ID,0)
		this.title = intent.getStringExtra(EXTRA_CAPTION) ?: "?"

		setContentView(R.layout.act_apng_view)
		this.apngView = findViewById(R.id.apngView)
		this.tvError = findViewById(R.id.tvError)

		thread(start=true){
			try {
				this@ActApngView.resources.openRawResource(resId).use {
					val apngFrames = ApngFrames.parseApng(it, 1024)
					handler.post {
						if(isDestroyed) return@post
						apngView.visibility = View.VISIBLE
						tvError.visibility = View.GONE
						apngView.apngFrames = apngFrames
					}
				}
			}catch(ex:Throwable){
				ex.printStackTrace()
				Log.e(ActMain.TAG,"load error: ${ex.javaClass.simpleName} ${ex.message}")
				
				val message = "%s %s".format( ex.javaClass.simpleName, ex.message)
				handler.post{
					if(isDestroyed) return@post
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