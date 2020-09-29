package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.util.AsyncActivity
import jp.juggler.util.*
import org.apache.commons.io.IOUtils
import org.jetbrains.anko.textColor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList

class ActLanguageFilter : AsyncActivity(), View.OnClickListener {
	
	private class MyItem(
		val code : String,
		var allow : Boolean
	)
	
	companion object {
		
		internal val log = LogCategory("ActLanguageFilter")
		
		internal const val EXTRA_COLUMN_INDEX = "column_index"
		private const val STATE_LANGUAGE_LIST = "language_list"
		
		fun open(activity : ActMain, idx : Int, request_code : Int) {
			val intent = Intent(activity, ActLanguageFilter::class.java)
			intent.putExtra(EXTRA_COLUMN_INDEX, idx)
			activity.startActivityForResult(intent, request_code)
		}
		
		// 他の設定子画面と重複しない値にすること
		private const val REQUEST_CODE_OTHER = 0
		private const val REQUEST_CODE_IMPORT = 1
		
		private val languageComparator = Comparator<MyItem> { a, b ->
			when {
				a.code == TootStatus.LANGUAGE_CODE_DEFAULT -> - 1
				b.code == TootStatus.LANGUAGE_CODE_DEFAULT -> 1
				a.code == TootStatus.LANGUAGE_CODE_UNKNOWN -> - 1
				b.code == TootStatus.LANGUAGE_CODE_UNKNOWN -> 1
				else -> a.code.compareTo(b.code)
			}
		}
		
		private fun equalsLanguageList(a : JsonObject?, b : JsonObject?) : Boolean {
			fun JsonObject.encodeToString() : String {
				val clone = this.toString().decodeJsonObject()
				if(! clone.contains(TootStatus.LANGUAGE_CODE_DEFAULT)) {
					clone[TootStatus.LANGUAGE_CODE_DEFAULT] = true
				}
				return clone.keys.sorted().joinToString(",") { "$it=${this[it]}" }
			}
			
			val a_sign = (a ?: JsonObject()).encodeToString()
			val b_sign = (b ?: JsonObject()).encodeToString()
			return a_sign == b_sign
		}
	}
	
	private val languageNameMap by lazy {
		HashMap<String, String>().apply {
			
			// from https://github.com/google/cld3/blob/master/src/task_context_params.cc#L43
			val languageNamesCld3 = arrayOf(
				"eo", "co", "eu", "ta", "de", "mt", "ps", "te", "su", "uz", "zh-Latn", "ne",
				"nl", "sw", "sq", "hmn", "ja", "no", "mn", "so", "ko", "kk", "sl", "ig",
				"mr", "th", "zu", "ml", "hr", "bs", "lo", "sd", "cy", "hy", "uk", "pt",
				"lv", "iw", "cs", "vi", "jv", "be", "km", "mk", "tr", "fy", "am", "zh",
				"da", "sv", "fi", "ht", "af", "la", "id", "fil", "sm", "ca", "el", "ka",
				"sr", "it", "sk", "ru", "ru-Latn", "bg", "ny", "fa", "haw", "gl", "et",
				"ms", "gd", "bg-Latn", "ha", "is", "ur", "mi", "hi", "bn", "hi-Latn", "fr",
				"yi", "hu", "xh", "my", "tg", "ro", "ar", "lb", "el-Latn", "st", "ceb",
				"kn", "az", "si", "ky", "mg", "en", "gu", "es", "pl", "ja-Latn", "ga", "lt",
				"sn", "yo", "pa", "ku"
			)
			
			for(src1 in languageNamesCld3) {
				val src2 = src1.replace("-Latn", "")
				val isLatn = src2 != src1
				val locale = Locale(src2)
				log.w("languageNameMap $src1 ${locale.language} ${locale.country} ${locale.displayName}")
				put(
					src1, if(isLatn) {
						"${locale.displayName}(Latn)"
					} else {
						locale.displayName
					}
				)
			}
			put(TootStatus.LANGUAGE_CODE_DEFAULT, getString(R.string.language_code_default))
			put(TootStatus.LANGUAGE_CODE_UNKNOWN, getString(R.string.language_code_unknown))
		}
		
	}
	
	private fun getDesc(item : MyItem) : String {
		val code = item.code
		return languageNameMap[code] ?: getString(R.string.custom)
	}
	
	private var column_index : Int = 0
	internal lateinit var column : Column
	internal lateinit var app_state : AppState
	internal var density : Float = 0f
	
	private lateinit var listView : ListView
	private lateinit var adapter : MyAdapter
	private val languageList = ArrayList<MyItem>()
	private var loading_busy : Boolean = false
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this)
		initUI()
		
		app_state = App1.getAppState(this)
		density = app_state.density
		column_index = intent.getIntExtra(EXTRA_COLUMN_INDEX, 0)
		column = app_state.column_list[column_index]
		
		if(savedInstanceState != null) {
			try {
				val sv = savedInstanceState.getString(STATE_LANGUAGE_LIST, null)
				if(sv != null) {
					val list = sv.decodeJsonObject()
					load(list)
					return
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		load(column.language_filter)
	}
	
	override fun onSaveInstanceState(outState : Bundle, outPersistentState : PersistableBundle) {
		super.onSaveInstanceState(outState, outPersistentState)
		outState.putString(STATE_LANGUAGE_LIST, encodeLanguageList().toString())
	}
	
	override fun onBackPressed() {
		if(! equalsLanguageList(column.language_filter, encodeLanguageList())) {
			AlertDialog.Builder(this)
				.setMessage(R.string.language_filter_quit_waring)
				.setPositiveButton(R.string.ok) { _, _ -> finish() }
				.setNegativeButton(R.string.cancel, null)
				.show()
			return
		}
		
		super.onBackPressed()
	}
	
	private fun initUI() {
		setContentView(R.layout.act_language_filter)
		App1.initEdgeToEdge(this)
		Styler.fixHorizontalPadding(findViewById(R.id.llContent))
		
		for(id in intArrayOf(
			R.id.btnAdd,
			R.id.btnSave,
			R.id.btnMore
		)) {
			findViewById<View>(id)?.setOnClickListener(this)
		}
		
		listView = findViewById(R.id.listView)
		adapter = MyAdapter()
		listView.adapter = adapter
		listView.onItemClickListener = adapter
	}
	
	// UIのデータをJsonObjectにエンコード
	private fun encodeLanguageList() = jsonObject {
		for(item in languageList) {
			put(item.code, item.allow)
		}
	}
	
	private fun load(src : JsonObject?) {
		loading_busy = true
		try {
			languageList.clear()
			
			if(src != null) {
				for(key in src.keys) {
					languageList.add(MyItem(key, src.boolean(key) ?: true))
				}
			}
			
			if(null == languageList.find { it.code == TootStatus.LANGUAGE_CODE_DEFAULT }) {
				languageList.add(MyItem(TootStatus.LANGUAGE_CODE_DEFAULT, true))
			}
			
			languageList.sortWith(languageComparator)
			
			adapter.notifyDataSetChanged()
		} finally {
			loading_busy = false
		}
	}
	
	private fun save() {
		column.language_filter = encodeLanguageList()
	}
	
	private inner class MyAdapter : BaseAdapter(), AdapterView.OnItemClickListener {
		
		override fun getCount() : Int = languageList.size
		override fun getItemId(idx : Int) : Long = 0L
		override fun getItem(idx : Int) : Any = languageList[idx]
		
		override fun getView(idx : Int, viewArg : View?, parent : ViewGroup?) : View {
			val tv = (viewArg ?: layoutInflater.inflate(
				R.layout.lv_language_filter,
				parent,
				false
			)) as TextView
			val item = languageList[idx]
			tv.text = String.format(
				"%s %s : %s",
				item.code,
				getDesc(item),
				getString(if(item.allow) R.string.language_show else R.string.language_hide)
			)
			tv.textColor = getAttributeColor(
				when(item.allow) {
					true -> R.attr.colorContentText
					false -> R.attr.colorRegexFilterError
				}
			)
			return tv
		}
		
		override fun onItemClick(parent : AdapterView<*>?, viewArg : View?, idx : Int, id : Long) {
			if(idx in languageList.indices) edit(languageList[idx])
		}
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnSave -> {
				save()
				val data = Intent()
				data.putExtra(EXTRA_COLUMN_INDEX, column_index)
				setResult(RESULT_OK, data)
				finish()
			}
			
			R.id.btnAdd -> edit(null)
			
			R.id.btnMore -> {
				ActionsDialog()
					.addAction(getString(R.string.clear_all)) {
						languageList.clear()
						languageList.add(MyItem(TootStatus.LANGUAGE_CODE_DEFAULT, true))
						adapter.notifyDataSetChanged()
					}
					.addAction(getString(R.string.export)) { export() }
					.addAction(getString(R.string.import_)) { import() }
					.show(this)
			}
		}
	}
	
	private fun edit(myItem : MyItem?) =
		DlgLanguageFilter.open(this, myItem, object : DlgLanguageFilter.Callback {
			override fun onOK(code : String, allow : Boolean) {
				val it = languageList.iterator()
				while(it.hasNext()) {
					val item = it.next()
					if(item.code == code) {
						item.allow = allow
						adapter.notifyDataSetChanged()
						return
					}
				}
				languageList.add(MyItem(code, allow))
				languageList.sortWith(languageComparator)
				adapter.notifyDataSetChanged()
				return
			}
			
			override fun onDelete(code : String) {
				val it = languageList.iterator()
				while(it.hasNext()) {
					val item = it.next()
					if(item.code == code) it.remove()
				}
				adapter.notifyDataSetChanged()
			}
		})
	
	private object DlgLanguageFilter {
		
		interface Callback {
			
			fun onOK(code : String, allow : Boolean)
			fun onDelete(code : String)
		}
		
		@SuppressLint("InflateParams")
		fun open(activity : ActLanguageFilter, item : MyItem?, callback : Callback) {
			
			val view = activity.layoutInflater.inflate(R.layout.dlg_language_filter, null, false)
			
			val etLanguage : EditText = view.findViewById(R.id.etLanguage)
			val btnPresets : ImageButton = view.findViewById(R.id.btnPresets)
			val tvLanguage : TextView = view.findViewById(R.id.tvLanguage)
			
			val rbShow : RadioButton = view.findViewById(R.id.rbShow)
			val rbHide : RadioButton = view.findViewById(R.id.rbHide)
			
			when(item?.allow ?: true) {
				true -> rbShow.isChecked = true
				else -> rbHide.isChecked = true
			}
			
			fun updateDesc() {
				val code = etLanguage.text.toString().trim()
				val desc = activity.languageNameMap[code] ?: activity.getString(R.string.custom)
				tvLanguage.text = desc
			}
			
			val languageList =
				activity.languageNameMap.map { MyItem(it.key, true) }.sortedWith(languageComparator)
			btnPresets.setOnClickListener {
				val ad = ActionsDialog()
				for(a in languageList) {
					ad.addAction("${a.code} ${activity.getDesc(a)}") {
						etLanguage.setText(a.code)
						updateDesc()
					}
				}
				ad.show(activity, activity.getString(R.string.presets))
			}
			
			etLanguage.setText(item?.code ?: "")
			updateDesc()
			
			etLanguage.addTextChangedListener(object : TextWatcher {
				override fun afterTextChanged(p0 : Editable?) {
					updateDesc()
				}
				
				override fun beforeTextChanged(p0 : CharSequence?, p1 : Int, p2 : Int, p3 : Int) {
				}
				
				override fun onTextChanged(p0 : CharSequence?, p1 : Int, p2 : Int, p3 : Int) {
				}
				
			})
			if(item != null) {
				etLanguage.isEnabled = false
				btnPresets.isEnabled = false
				btnPresets.setEnabledColor(
					activity,
					R.drawable.ic_edit,
					activity.getAttributeColor(R.attr.colorVectorDrawable),
					false
				)
			}
			
			val builder = AlertDialog.Builder(activity)
				.setView(view)
				.setCancelable(true)
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ ->
					callback.onOK(etLanguage.text.toString().trim(), rbShow.isChecked)
				}
			
			if(item != null && item.code != TootStatus.LANGUAGE_CODE_DEFAULT) {
				builder.setNeutralButton(R.string.delete) { _, _ ->
					callback.onDelete(etLanguage.text.toString().trim())
				}
			}
			
			builder.show()
		}
	}
	
	@Suppress("BlockingMethodInNonBlockingContext")
	private fun export() = runWithProgress(
		"export language filter",
		{
			val data = JsonObject().apply {
				for(item in languageList) {
					put(item.code, item.allow)
				}
			}
				.toString()
				.encodeUTF8()
			
			val cache_dir = cacheDir
			cache_dir.mkdir()
			
			val file = File(
				cache_dir,
				"SubwayTooter-language-filter.${Process.myPid()}.${Process.myTid()}.json"
			)
			FileOutputStream(file).use {
				it.write(data)
			}
			file
		},
		{
			val uri = FileProvider.getUriForFile(
				this@ActLanguageFilter,
				App1.FILE_PROVIDER_AUTHORITY,
				it
			)
			val intent = Intent(Intent.ACTION_SEND)
			intent.type = contentResolver.getType(uri)
			intent.putExtra(Intent.EXTRA_SUBJECT, "SubwayTooter language filter data")
			intent.putExtra(Intent.EXTRA_STREAM, uri)
			
			intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
			startActivityForResult(intent, REQUEST_CODE_OTHER)
		}
	)
	
	private fun import() {
		try {
			val intent = intentOpenDocument("*/*")
			startActivityForResult(intent, REQUEST_CODE_IMPORT)
		} catch(ex : Throwable) {
			showToast(ex, "import failed.")
		}
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		if(resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_IMPORT) {
			data.handleGetContentResult(contentResolver).firstOrNull()?.uri?.let {
				import2(it)
			}
		}
		super.onActivityResult(requestCode, resultCode, data)
	}
	
	@Suppress("BlockingMethodInNonBlockingContext")
	private fun import2(uri : Uri) = runWithProgress(
		"import language filter",
		{
			log.d("import2 type=${contentResolver.getType(uri)}")
			val source = contentResolver.openInputStream(uri)
			if(source == null) {
				showToast(true, "openInputStream failed.")
				null
			} else {
				source.use { inStream ->
					val bao = ByteArrayOutputStream()
					IOUtils.copy(inStream, bao)
					bao.toByteArray().decodeUTF8().decodeJsonObject()
				}
			}
		},
		{ if(it != null) load(it) }
	)
}
