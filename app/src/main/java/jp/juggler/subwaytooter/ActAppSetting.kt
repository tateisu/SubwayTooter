package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.JsonWriter
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import jp.juggler.subwaytooter.dialog.ProgressDialogEx
import jp.juggler.util.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ActAppSetting : AppCompatActivity() {
	
	companion object {
		internal val log = LogCategory("ActAppSetting")
		
		fun open(activity : ActMain, request_code : Int) {
			activity.startActivityForResult(
				Intent(activity, ActAppSetting::class.java),
				request_code
			)
		}
		
		// 他の設定子画面と重複しない値にすること
		private const val REQUEST_CODE_OTHER = 0
		private const val REQUEST_CODE_APP_DATA_IMPORT = 1
	}
	
	private lateinit var lvList : ListView
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, false)
		
		setContentView(R.layout.act_app_setting)
		
		Styler.fixHorizontalPadding2(findViewById(R.id.llContent))
		lvList = findViewById(R.id.lvList)
		
		val adapter = MyAdapter()
		lvList.adapter = adapter
		lvList.onItemClickListener = adapter
		
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		if(resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_APP_DATA_IMPORT) {
			data.handleGetContentResult(contentResolver).firstOrNull()?.uri?.let {
				importAppData(false, it)
			}
		}
		super.onActivityResult(requestCode, resultCode, data)
	}
	///////////////////////////////////////////////////////////////
	
	class Item(
		val titleId : Int,
		val descId : Int? = null,
		val block : (Item) -> Unit
	)
	
	private fun genItem(titleId : Int, descId : Int? = null, block : (Item) -> Unit) =
		Item(titleId, descId, block)
	
	inner class MyAdapter : BaseAdapter(), AdapterView.OnItemClickListener {
		
		val items = arrayOf(
			
			genItem(R.string.notifications) {
				openChild(it, R.layout.act_app_setting_notifications)
			},
			
			genItem(R.string.behavior) {
				openChild(it, R.layout.act_app_setting_behavior)
			},
			
			genItem(R.string.post) {
				openChild(it, R.layout.act_app_setting_post)
			},
			
			genItem(R.string.tablet_mode) {
				openChild(it, R.layout.act_app_setting_tablet)
			},

			genItem(R.string.media_attachment) {
				openChild(it, R.layout.act_app_setting_media_attachment)
			},
			
			genItem(R.string.emoji) {
				openChild(it, R.layout.act_app_setting_emoji)
			},

			genItem(R.string.appearance) {
				openChild(it, R.layout.act_app_setting_appearance)
			},

			genItem(R.string.color) {
				openChild(it, R.layout.act_app_setting_color)
			},

			genItem(R.string.performance) {
				openChild(it, R.layout.act_app_setting_performance)
			},

			genItem(R.string.app_data_export) {
				exportAppData()
			},
			genItem(R.string.app_data_import, R.string.app_data_import_desc) {
				importAppData()
			}
		)
		
		override fun getCount() : Int = items.size
		
		override fun getItemId(position : Int) : Long = items[position].titleId.toLong()
		
		override fun getItem(position : Int) : Any = items[position]
		
		override fun onItemClick(
			parent : AdapterView<*>?,
			view : View?,
			position : Int,
			id : Long
		) {
			items[position].let { it.block(it) }
		}
		
		override fun getView(position : Int, convertView : View?, parent : ViewGroup?) : View {
			val view : View
			val holder : ViewHolder
			if(convertView == null) {
				view = layoutInflater.inflate(R.layout.lv_app_setting, parent, false)
				holder = ViewHolder(view)
				view.tag = holder
			} else {
				view = convertView
				holder = view.tag as ViewHolder
			}
			holder.bind(items[position])
			return view
		}
	}
	
	inner class ViewHolder(viewRoot : View) {
		private val tvTitle : TextView = viewRoot.findViewById(R.id.tvTitle)
		private val tvDesc : TextView = viewRoot.findViewById(R.id.tvDesc)
		
		fun bind(item : Item) {
			tvTitle.setText(item.titleId)
			vg(tvDesc, item.descId != null)
			item.descId?.let { tvDesc.setText(it) }
		}
	}
	
	///////////////////////////////////////////////////////////////
	
	private fun openChild(item : Item, layoutId : Int) {
		ActAppSettingChild.open(this, REQUEST_CODE_OTHER, layoutId, item.titleId)
	}
	
	///////////////////////////////////////////////////////////////
	
	private fun exportAppData() {
		
		@Suppress("DEPRECATION")
		val progress = ProgressDialogEx(this)
		
		val task = @SuppressLint("StaticFieldLeak")
		object : AsyncTask<Void, String, File?>() {
			
			override fun doInBackground(vararg params : Void) : File? {
				try {
					val cache_dir = cacheDir
					cache_dir.mkdir()
					
					val file = File(
						cache_dir,
						"SubwayTooter.${android.os.Process.myPid()}.${android.os.Process.myTid()}.zip"
					)
					
					// ZipOutputStreamオブジェクトの作成
					ZipOutputStream(FileOutputStream(file)).use { zipStream ->
						
						// アプリデータjson
						zipStream.putNextEntry(ZipEntry("AppData.json"))
						try {
							val jw = JsonWriter(OutputStreamWriter(zipStream, "UTF-8"))
							AppDataExporter.encodeAppData(this@ActAppSetting, jw)
							jw.flush()
						} finally {
							zipStream.closeEntry()
						}
						
						// カラム背景画像
						val appState = App1.getAppState(this@ActAppSetting)
						for(column in appState.column_list) {
							AppDataExporter.saveBackgroundImage(
								this@ActAppSetting,
								zipStream,
								column
							)
						}
					}
					
					return file
				} catch(ex : Throwable) {
					log.trace(ex)
					showToast(this@ActAppSetting, ex, "exportAppData failed.")
				}
				
				return null
			}
			
			override fun onCancelled(result : File?) {
				onPostExecute(result)
			}
			
			override fun onPostExecute(result : File?) {
				progress.dismiss()
				
				if(isCancelled || result == null) {
					// cancelled.
					return
				}
				
				try {
					val uri = FileProvider.getUriForFile(
						this@ActAppSetting,
						App1.FILE_PROVIDER_AUTHORITY,
						result
					)
					val intent = Intent(Intent.ACTION_SEND)
					intent.type = contentResolver.getType(uri)
					intent.putExtra(Intent.EXTRA_SUBJECT, "SubwayTooter app data")
					intent.putExtra(Intent.EXTRA_STREAM, uri)
					
					intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
					startActivityForResult(intent, REQUEST_CODE_OTHER)
				} catch(ex : Throwable) {
					log.trace(ex)
					showToast(this@ActAppSetting, ex, "exportAppData failed.")
				}
				
			}
		}
		
		progress.isIndeterminate = true
		progress.setCancelable(true)
		progress.setOnCancelListener { task.cancel(true) }
		progress.show()
		task.executeOnExecutor(App1.task_executor)
	}
	
	private fun importAppData() {
		try {
			val intent = intentOpenDocument("*/*")
			startActivityForResult(intent, REQUEST_CODE_APP_DATA_IMPORT)
		} catch(ex : Throwable) {
			showToast(this, ex, "importAppData(1) failed.")
		}
		
	}
	
	private fun importAppData(bConfirm : Boolean, uri : Uri) {
		
		val type = contentResolver.getType(uri)
		log.d("importAppData type=%s", type)
		
		if(! bConfirm) {
			AlertDialog.Builder(this)
				.setMessage(getString(R.string.app_data_import_confirm))
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ -> importAppData(true, uri) }
				.show()
			return
		}
		
		val data = Intent()
		data.data = uri
		setResult(ActMain.RESULT_APP_DATA_IMPORT, data)
		finish()
		
	}
}
