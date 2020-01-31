package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.JsonWriter
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.action.CustomShare
import jp.juggler.subwaytooter.action.CustomShareTarget
import jp.juggler.subwaytooter.dialog.DlgAppPicker
import jp.juggler.subwaytooter.dialog.ProgressDialogEx
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.math.abs

class ActAppSetting : AppCompatActivity(), ColorPickerDialogListener, View.OnClickListener {
	
	companion object {
		internal val log = LogCategory("ActAppSetting")
		
		fun open(activity : ActMain, request_code : Int) {
			activity.startActivityForResult(
				Intent(activity, ActAppSetting::class.java),
				request_code
			)
		}
		
		private const val COLOR_DIALOG_ID = 1
		
		private const val STATE_CHOOSE_INTENT_TARGET = "customShareTarget"
		
		// 他の設定子画面と重複しない値にすること
		private const val REQUEST_CODE_OTHER = 0
		private const val REQUEST_CODE_APP_DATA_IMPORT = 1
		
		internal const val REQUEST_CODE_TIMELINE_FONT = 1
		internal const val REQUEST_CODE_TIMELINE_FONT_BOLD = 2
		
		val reLinefeed = Regex("[\\x0d\\x0a]+")
	}
	
	private var customShareTarget : CustomShareTarget? = null
	
	lateinit var pref : SharedPreferences
	lateinit var handler : Handler
	private lateinit var lvList : ListView
	private lateinit var adapter : MyAdapter
	private lateinit var etSearch : EditText
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		App1.setActivityTheme(this, noActionBar = true)
		
		this.handler = App1.getAppState(this).handler
		this.pref = Pref.pref(this)
		
		//		val intent = this.intent
		//		val layoutId = intent.getIntExtra(EXTRA_LAYOUT_ID, 0)
		//		val titleId = intent.getIntExtra(EXTRA_TITLE_ID, 0)
		//		this.title = getString(titleId)
		
		if(savedInstanceState != null) {
			try {
				val sv = savedInstanceState.getString(STATE_CHOOSE_INTENT_TARGET)
				customShareTarget = CustomShareTarget.values().firstOrNull { it.name == sv }
			} catch(ex : Throwable) {
				log.e(ex, "can't restore customShareTarget.")
			}
		}
		
		setContentView(R.layout.act_app_setting)
		App1.initEdgeToEdge(this)
		
		Styler.fixHorizontalPadding0(findViewById(R.id.llContent))
		lvList = findViewById(R.id.lvList)
		
		adapter = MyAdapter()
		lvList.adapter = adapter
		
		etSearch = findViewById<EditText>(R.id.etSearch).apply {
			addTextChangedListener(object : TextWatcher {
				override fun afterTextChanged(p0 : Editable?) {
					pendingQuery = p0?.toString()
					this@ActAppSetting.handler.removeCallbacks(procQuery)
					this@ActAppSetting.handler.postDelayed(procQuery, 166L)
				}
				
				override fun beforeTextChanged(
					p0 : CharSequence?,
					p1 : Int,
					p2 : Int,
					p3 : Int
				) {
				}
				
				override fun onTextChanged(
					p0 : CharSequence?,
					p1 : Int,
					p2 : Int,
					p3 : Int
				) {
				}
			})
		}
		
		findViewById<View>(R.id.btnSearchReset).apply {
			setOnClickListener(this@ActAppSetting)
		}
		
		val e = pref.edit()
		var dirty = false
		appSettingRoot.scan{
			if( it.pref?.removeDefault(pref,e) ==true ) dirty = true
		}
		if(dirty) e.apply()

		load(null, null)
	}
	
	
	override fun onSaveInstanceState(outState : Bundle) {
		super.onSaveInstanceState(outState)
		val sv = customShareTarget?.name
		if(sv != null) outState.putString(STATE_CHOOSE_INTENT_TARGET, sv)
	}
	
	override fun onResume() {
		super.onResume()
		onCustomShareSelected()
	}
	
	override fun onPause() {
		super.onPause()
		
		// Pull通知チェック間隔を変更したかもしれないのでジョブを再設定する
		try {
			PollingWorker.scheduleJob(this, PollingWorker.JOB_POLLING)
		} catch(ex : Throwable) {
			log.trace(ex, "PollingWorker.scheduleJob failed.")
		}
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		if(resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_APP_DATA_IMPORT) {
			data.handleGetContentResult(contentResolver).firstOrNull()?.uri?.let {
				importAppData2(false, it)
			}
		} else if(resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_TIMELINE_FONT) {
			handleFontResult(AppSettingItem.TIMELINE_FONT, data, "TimelineFont")
		} else if(resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_TIMELINE_FONT_BOLD) {
			handleFontResult(AppSettingItem.TIMELINE_FONT_BOLD, data, "TimelineFontBold")
		}
		
		super.onActivityResult(requestCode, resultCode, data)
	}
	
	override fun onBackPressed() {
		when {
			lastQuery != null -> load(lastSection, null)
			lastSection != null -> load(null, null)
			else -> super.onBackPressed()
		}
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnSearchReset -> {
				handler.removeCallbacks(procQuery)
				etSearch.setText("")
				etSearch.hideKeyboard()
				load(lastSection, null)
			}
		}
	}
	
	///////////////////////////////////////////////////////////////
	private var pendingQuery : String? = null
	
	private val procQuery : Runnable = Runnable {
		if(pendingQuery != null) load(null, pendingQuery)
	}
	
	///////////////////////////////////////////////////////////////
	
	private val divider = Any()
	private val list = ArrayList<Any>()
	
	private var lastSection : AppSettingItem? = null
	private var lastQuery : String? = null
	
	private fun load(section : AppSettingItem?, query : String?) {
		list.clear()
		
		var lastPath : String? = null
		fun addParentPath(item : AppSettingItem) {
			list.add(divider)
			
			val pathList = ArrayList<String>()
			var parent = item.parent
			while(parent != null) {
				if(parent.caption != 0) pathList.add(0, getString(parent.caption))
				parent = parent.parent
			}
			val path = pathList.joinToString("/")
			if(path != lastPath) {
				lastPath = path
				list.add(path)
				list.add(divider)
			}
		}
		
		if(query?.isNotEmpty() == true) {
			lastQuery = query
			fun scanGroup(level : Int, item : AppSettingItem) {
				if(item.caption == 0) return
				if(item.type != SettingType.Section) {
					var match = getString(item.caption).contains(query, ignoreCase = true)
					if(item.type == SettingType.Group) {
						for(child in item.items) {
							if(child.caption == 0) continue
							if(getString(item.caption).contains(query, ignoreCase = true)) {
								match = true
								break
							}
						}
						if(match) {
							// put entire group
							addParentPath(item)
							list.add(item)
							for(child in item.items) {
								list.add(child)
							}
						}
						return
					}
					
					if(match) {
						addParentPath(item)
						list.add(item)
					}
				}
				for(child in item.items) {
					scanGroup(level + 1, child)
				}
			}
			scanGroup(0, appSettingRoot)
			if(list.isNotEmpty()) list.add(divider)
		} else if(section == null) {
			// show root page
			val root = appSettingRoot
			lastQuery = null
			lastSection = null
			for(child in root.items) {
				list.add(divider)
				list.add(child)
			}
			list.add(divider)
		} else {
			// show section page
			lastSection = section
			lastQuery = null
			fun scanGroup(level : Int, parent : AppSettingItem?) {
				parent ?: return
				for(item in parent.items) {
					list.add(divider)
					list.add(item)
					if(item.items.isNotEmpty()) {
						if(item.type == SettingType.Group) {
							for(child in item.items) {
								list.add(child)
							}
						} else {
							scanGroup(level + 1, item)
						}
					}
				}
			}
			scanGroup(0, section.cast())
			if(list.isNotEmpty()) list.add(divider)
		}
		adapter.notifyDataSetChanged()
		lvList.setSelectionFromTop(0, 0)
	}
	
	inner class MyAdapter : BaseAdapter() {
		
		override fun getCount() : Int = list.size
		override fun getItemId(position : Int) : Long = 0
		override fun getItem(position : Int) : Any = list[position]
		override fun getViewTypeCount() : Int = SettingType.values().maxBy { it.id } !!.id + 1
		
		override fun getItemViewType(position : Int) : Int =
			when(val item = list[position]) {
				is AppSettingItem -> item.type.id
				is String -> SettingType.Path.id
				divider -> SettingType.Divider.id
				else -> error("can't generate view for type ${item}")
			}
		
		override fun getView(position : Int, convertView : View?, parent : ViewGroup?) : View =
			when(val item = list[position]) {
				is AppSettingItem ->
					getViewSettingItem(item, convertView, parent)
				is String -> getViewPath(item, convertView)
				divider -> getViewDivider(convertView)
				else -> error("can't generate view for type ${item}")
			}
	}
	
	private fun dip(dp : Float) : Int =
		(resources.displayMetrics.density * dp + 0.5f).toInt()
	
	private fun dip(dp : Int) : Int = dip(dp.toFloat())
	
	private fun getViewDivider(convertView : View?) : View =
		convertView ?: FrameLayout(this@ActAppSetting).apply {
			layoutParams = AbsListView.LayoutParams(
				AbsListView.LayoutParams.MATCH_PARENT,
				AbsListView.LayoutParams.WRAP_CONTENT
			)
			addView(View(this@ActAppSetting).apply {
				layoutParams = FrameLayout.LayoutParams(
					AbsListView.LayoutParams.MATCH_PARENT,
					dip(1)
				).apply {
					val margin_lr = 0
					val margin_tb = dip(6)
					setMargins(margin_lr, margin_tb, margin_lr, margin_tb)
				}
				setBackgroundColor(getAttributeColor(context, R.attr.colorSettingDivider))
			})
		}
	
	private fun getViewPath(path : String, convertView : View?) : View {
		val tv : TextView = convertView.cast() ?: TextView(this@ActAppSetting).apply {
			layoutParams = AbsListView.LayoutParams(
				AbsListView.LayoutParams.MATCH_PARENT,
				AbsListView.LayoutParams.WRAP_CONTENT
			)
			val pad_lr = 0
			val pad_tb = dip(3)
			setTypeface(typeface, Typeface.BOLD)
			setPaddingRelative(pad_lr, pad_tb, pad_lr, pad_tb)
		}
		tv.text = path
		return tv
	}
	
	private fun getViewSettingItem(
		item : AppSettingItem,
		convertView : View?,
		parent : ViewGroup?
	) : View {
		val view : View
		val holder : ViewHolderSettingItem
		if(convertView != null) {
			view = convertView
			holder = convertView.tag.cast() !!
		} else {
			view = layoutInflater.inflate(R.layout.lv_setting_item, parent, false)
			holder = ViewHolderSettingItem(view)
			view.tag = holder
		}
		holder.bind(item)
		return view
	}
	
	private var colorTarget : AppSettingItem? = null
	
	override fun onDialogDismissed(dialogId : Int) {
	}
	
	override fun onColorSelected(dialogId : Int, @ColorInt colorSelected : Int) {
		val colorTarget = this.colorTarget ?: return
		val ip : IntPref = colorTarget.pref.cast() ?: error("$colorTarget has no in pref")
		val c = when(colorTarget.type) {
			SettingType.ColorAlpha -> colorSelected.notZero() ?: 0x01000000
			else -> colorSelected or Color.BLACK
		}
		pref.edit().put(ip, c).apply()
		findItemViewHolder(colorTarget)?.showColor()
		colorTarget.changed(this)
	}
	
	inner class ViewHolderSettingItem(viewRoot : View) :
		TextWatcher,
		AdapterView.OnItemSelectedListener,
		CompoundButton.OnCheckedChangeListener {
		
		private val tvCaption : TextView = viewRoot.findViewById(R.id.tvCaption)
		private val btnAction : Button = viewRoot.findViewById(R.id.btnAction)
		
		private val checkBox : CheckBox = viewRoot.findViewById<CheckBox>(R.id.checkBox)
			.also { it.setOnCheckedChangeListener(this) }
		
		private val swSwitch : Switch = viewRoot.findViewById<Switch>(R.id.swSwitch)
			.also { it.setOnCheckedChangeListener(this) }
		
		val llExtra : LinearLayout = viewRoot.findViewById(R.id.llExtra)
		
		val textView1 : TextView = viewRoot.findViewById(R.id.textView1)
		
		private val llButtonBar : LinearLayout = viewRoot.findViewById(R.id.llButtonBar)
		private val vColor : View = viewRoot.findViewById(R.id.vColor)
		private val btnEdit : Button = viewRoot.findViewById(R.id.btnEdit)
		private val btnReset : Button = viewRoot.findViewById(R.id.btnReset)
		
		private val spSpinner : Spinner = viewRoot.findViewById<Spinner>(R.id.spSpinner)
			.also { it.onItemSelectedListener = this }
		
		private val etEditText : EditText = viewRoot.findViewById<EditText>(R.id.etEditText)
			.also { it.addTextChangedListener(this) }
		
		private val tvDesc : TextView = viewRoot.findViewById(R.id.tvDesc)
		private val tvError : TextView = viewRoot.findViewById(R.id.tvError)
		
		val activity : ActAppSetting
			get() = this@ActAppSetting
		
		var item : AppSettingItem? = null
		
		private var bindingBusy = false
		
		fun bind(item : AppSettingItem) {
			bindingBusy = true
			try {
				this.item = item
				
				tvCaption.vg(false)
				btnAction.vg(false)
				checkBox.vg(false)
				swSwitch.vg(false)
				llExtra.vg(false)
				textView1.vg(false)
				llButtonBar.vg(false)
				vColor.vg(false)
				spSpinner.vg(false)
				etEditText.vg(false)
				tvDesc.vg(false)
				tvError.vg(false)
				
				val name = if(item.caption == 0) "" else getString(item.caption)
				
				if(item.desc != 0) {
					tvDesc.vg(true)
					tvDesc.text = getString(item.desc)
					if(item.descClickSet) {
						tvDesc.background = ContextCompat.getDrawable(
							activity,
							R.drawable.btn_bg_transparent_round6dp
						)
						tvDesc.setOnClickListener { item.descClick.invoke(activity) }
					} else {
						tvDesc.background = null
						tvDesc.setOnClickListener(null)
						tvDesc.isClickable = false
					}
				}
				
				when(item.type) {
					
					SettingType.Section -> {
						btnAction.vg(true)
						btnAction.text = name
						btnAction.isEnabled = item.enabled
						btnAction.setOnClickListener {
							load(item.cast() !!, null)
						}
					}
					
					SettingType.Action -> {
						btnAction.vg(true)
						btnAction.text = name
						btnAction.isEnabled = item.enabled
						btnAction.setOnClickListener {
							item.action(activity)
						}
					}
					
					SettingType.CheckBox -> {
						val bp : BooleanPref =
							item.pref.cast() ?: error("$name has no boolean pref")
						checkBox.vg(false) // skip animation
						checkBox.text = name
						checkBox.isEnabled = item.enabled
						checkBox.isChecked = bp(pref)
						checkBox.vg(true)
					}
					
					SettingType.Switch -> {
						val bp : BooleanPref =
							item.pref.cast() ?: error("$name has no boolean pref")
						showCaption(name)
						swSwitch.vg(false) // skip animation
						App1.setSwitchColor1(activity, pref, swSwitch)
						swSwitch.isEnabled = item.enabled
						swSwitch.isChecked = bp(pref)
						swSwitch.vg(true)
					}
					
					SettingType.Group -> {
						showCaption(name)
					}
					
					SettingType.Sample -> {
						llExtra.vg(true)
						llExtra.removeAllViews()
						layoutInflater.inflate(item.sampleLayoutId, llExtra, true)
						item.sampleUpdate(activity, llExtra)
					}
					
					SettingType.ColorAlpha, SettingType.ColorOpaque -> {
						val ip = item.pref.cast<IntPref>() ?: error("$name has no int pref")
						showCaption(name)
						llButtonBar.vg(true)
						vColor.vg(true)
						vColor.setBackgroundColor(ip(pref))
						btnEdit.isEnabled = item.enabled
						btnReset.isEnabled = item.enabled
						btnEdit.setOnClickListener {
							colorTarget = item
							val color = ip(pref)
							val builder = ColorPickerDialog.newBuilder()
								.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
								.setAllowPresets(true)
								.setShowAlphaSlider(item.type == SettingType.ColorAlpha)
								.setDialogId(COLOR_DIALOG_ID)
							if(color != 0) builder.setColor(color)
							builder.show(activity)
							
						}
						btnReset.setOnClickListener {
							pref.edit().remove(ip).apply()
							item.changed.invoke(activity)
						}
					}
					
					SettingType.Spinner -> {
						showCaption(name)
						
						spSpinner.vg(true)
						spSpinner.isEnabled = item.enabled
						
						val pi = item.pref
						if(pi is IntPref) {
							// 整数型の設定のSpinnerは全て選択肢を単純に覚える
							val argsInt = item.spinnerArgs
							if(argsInt != null) {
								initSpinner(spSpinner, argsInt.map { getString(it) })
							} else {
								initSpinner(spSpinner, item.spinnerArgsProc(activity))
							}
							spSpinner.setSelection(pi.invoke(pref))
						} else {
							item.spinnerInitializer.invoke(activity, spSpinner)
						}
					}
					
					SettingType.EditText -> {
						showCaption(name)
						etEditText.vg(true)
							
							?: error("EditText must have preference.")
						etEditText.inputType = item.inputType
						val text = when(val pi = item.pref) {
							is FloatPref -> {
								item.fromFloat.invoke(activity, pi(pref))
							}
							
							is StringPref -> {
								pi(pref)
							}
							
							else -> error("EditText han incorrect pref $pi")
						}
						etEditText.setText(text)
						etEditText.setSelection(0, text.length)
						
						item.hint?.let { etEditText.hint = it }
						
						updateErrorView()
					}
					
					SettingType.TextWithSelector -> {
						showCaption(name)
						llButtonBar.vg(true)
						vColor.vg(false)
						textView1.vg(true)
						
						item.showTextView.invoke(activity, textView1)
						
						btnEdit.setOnClickListener {
							item.onClickEdit.invoke(activity)
						}
						btnReset.setOnClickListener {
							item.onClickReset.invoke(activity)
						}
					}
					
					else -> error("unknown type ${item.type}")
				}
			} finally {
				bindingBusy = false
			}
		}
		
		private fun showCaption(caption : String) {
			if(caption.isNotEmpty()) {
				tvCaption.vg(true)
				tvCaption.text = caption
				updateCaption()
			}
		}
		
		fun updateCaption() {
			val item = item ?: return
			val key = item.pref?.key ?: return
			
			val sample : TextView = tvCaption
			var defaultExtra = defaultLineSpacingExtra[key]
			if(defaultExtra == null) {
				defaultExtra = sample.lineSpacingExtra
				defaultLineSpacingExtra[key] = defaultExtra
			}
			var defaultMultiplier = defaultLineSpacingMultiplier[key]
			if(defaultMultiplier == null) {
				defaultMultiplier = sample.lineSpacingMultiplier
				defaultLineSpacingMultiplier[key] = defaultMultiplier
			}
			
			val size = item.captionFontSize.invoke(activity)
			if(size != null) sample.textSize = size
			
			val spacing = item.captionSpacing.invoke(activity)
			if(spacing == null || ! spacing.isFinite()) {
				sample.setLineSpacing(defaultExtra, defaultMultiplier)
			} else {
				sample.setLineSpacing(0f, spacing)
			}
			
		}
		
		private fun updateErrorView() {
			val item = item ?: return
			val sv = etEditText.text.toString()
			val error = item.getError.invoke(activity, sv)
			tvError.vg(error != null)?.text = error
		}
		
		fun showColor() {
			val item = item ?: return
			val ip = item.pref.cast<IntPref>() ?: return
			val c = ip(pref)
			vColor.setBackgroundColor(c)
		}
		
		override fun beforeTextChanged(p0 : CharSequence?, p1 : Int, p2 : Int, p3 : Int) {
		}
		
		override fun onTextChanged(p0 : CharSequence?, p1 : Int, p2 : Int, p3 : Int) {
		}
		
		override fun afterTextChanged(p0 : Editable?) {
			if(bindingBusy) return
			val item = item ?: return
			
			val sv = item.filter.invoke(p0?.toString() ?: "")
			
			when(val pi = item.pref) {
				
				is StringPref -> {
					pref.edit().put(pi, sv).apply()
				}
				
				is FloatPref -> {
					val fv = item.toFloat.invoke(activity, sv)
					if(fv.isFinite()) {
						pref.edit().put(pi, fv).apply()
					} else {
						pref.edit().remove(pi.key).apply()
					}
				}
				
				else -> {
					error("not FloatPref or StringPref")
				}
			}
			
			item.changed.invoke(activity)
			updateErrorView()
		}
		
		override fun onNothingSelected(v : AdapterView<*>?) = Unit
		
		override fun onItemSelected(
			parent : AdapterView<*>?,
			view : View?,
			position : Int,
			id : Long
		) {
			if(bindingBusy) return
			val item = item ?: return
			when(val pi = item.pref) {
				is IntPref -> pref.edit().put(pi, spSpinner.selectedItemPosition).apply()
				else -> item.spinnerOnSelected.invoke(activity, spSpinner, position)
			}
			item.changed.invoke(activity)
		}
		
		override fun onCheckedChanged(v : CompoundButton?, isChecked : Boolean) {
			if(bindingBusy) return
			val item = item ?: return
			when(val pi = item.pref) {
				is BooleanPref -> pref.edit().put(pi, isChecked).apply()
				else -> error("CompoundButton has no booleanPref $pi")
			}
			item.changed.invoke(activity)
		}
	}
	
	private fun initSpinner(spinner : Spinner, captions : List<String>) {
		spinner.adapter = ArrayAdapter(
			this,
			android.R.layout.simple_spinner_item,
			captions.toTypedArray()
		).apply {
			setDropDownViewResource(R.layout.lv_spinner_dropdown)
		}
	}
	
	///////////////////////////////////////////////////////////////
	
	fun exportAppData() {
		
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
				progress.dismissSafe()
				
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
		
		progress.isIndeterminateEx = true
		progress.setCancelable(true)
		progress.setOnCancelListener { task.cancel(true) }
		progress.show()
		task.executeOnExecutor(App1.task_executor)
	}
	
	// open data picker
	fun importAppData1() {
		try {
			val intent = intentOpenDocument("*/*")
			startActivityForResult(intent, REQUEST_CODE_APP_DATA_IMPORT)
		} catch(ex : Throwable) {
			showToast(this, ex, "importAppData(1) failed.")
		}
	}
	
	// after data picked
	private fun importAppData2(bConfirm : Boolean, uri : Uri) {
		
		val type = contentResolver.getType(uri)
		log.d("importAppData type=%s", type)
		
		if(! bConfirm) {
			AlertDialog.Builder(this)
				.setMessage(getString(R.string.app_data_import_confirm))
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ -> importAppData2(true, uri) }
				.show()
			return
		}
		
		val data = Intent()
		data.data = uri
		setResult(ActMain.RESULT_APP_DATA_IMPORT, data)
		finish()
	}
	
	fun findItemViewHolder(item : AppSettingItem?) : ViewHolderSettingItem? {
		if(item != null) {
			for(i in 0 until lvList.childCount) {
				val view = lvList.getChildAt(i)
				val holder : ViewHolderSettingItem? = view?.tag?.cast()
				if(holder?.item == item) return holder
			}
		}
		return null
	}
	
	fun showSample(item : AppSettingItem?) {
		item ?: error("showSample: missing item…")
		findItemViewHolder(item)?.let {
			item.sampleUpdate.invoke(this, it.llExtra)
		}
	}
	
	fun setSwitchColor() {
		App1.setSwitchColor(this@ActAppSetting, pref, lvList)
	}
	
	//////////////////////////////////////////////////////
	
	fun formatFontSize(fv : Float) : String =
		when {
			fv.isFinite() -> String.format(Locale.getDefault(), "%.1f", fv)
			else -> ""
		}
	
	fun parseFontSize(src : String) : Float {
		try {
			if(src.isNotEmpty()) {
				val f = NumberFormat.getInstance(Locale.getDefault()).parse(src)?.toFloat()
				return when {
					f == null -> Float.NaN
					f.isNaN() -> Float.NaN
					f < 0f -> 0f
					f > 999f -> 999f
					else -> f
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return Float.NaN
	}
	
	private val defaultLineSpacingExtra = HashMap<String, Float>()
	private val defaultLineSpacingMultiplier = HashMap<String, Float>()
	
	private fun handleFontResult(item : AppSettingItem?, data : Intent, file_name : String) {
		item ?: error("handleFontResult : setting item is null")
		data.handleGetContentResult(contentResolver).firstOrNull()?.uri?.let {
			val file = saveTimelineFont(it, file_name)
			if(file != null) {
				pref.edit().put(item.pref.cast() !!, file.absolutePath).apply()
				showTimelineFont(item)
			}
		}
	}
	
	fun showTimelineFont(item : AppSettingItem?) {
		item ?: return
		val holder = findItemViewHolder(item) ?: return
		item.showTextView.invoke(this, holder.textView1)
	}
	
	fun showTimelineFont(item : AppSettingItem, tv : TextView) {
		val font_url = item.pref.cast<StringPref>() !!.invoke(this)
		try {
			if(font_url.isNotEmpty()) {
				tv.typeface = Typeface.DEFAULT
				val face = Typeface.createFromFile(font_url)
				tv.typeface = face
				tv.text = font_url
				return
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		// fallback
		tv.text = getString(R.string.not_selected)
		tv.typeface = Typeface.DEFAULT
	}
	
	private fun saveTimelineFont(uri : Uri?, file_name : String) : File? {
		try {
			if(uri == null) {
				showToast(this, false, "missing uri.")
				return null
			}
			
			contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
			
			val dir = filesDir
			
			dir.mkdir()
			
			val tmp_file = File(dir, "$file_name.tmp")
			
			val source : InputStream? = contentResolver.openInputStream(uri)
			if(source == null) {
				showToast(this, false, "openInputStream returns null. uri=%s", uri)
				return null
			} else {
				source.use { inStream ->
					FileOutputStream(tmp_file).use { outStream ->
						IOUtils.copy(inStream, outStream)
					}
				}
			}
			
			val face = Typeface.createFromFile(tmp_file)
			if(face == null) {
				showToast(this, false, "Typeface.createFromFile() failed.")
				return null
			}
			
			val file = File(dir, file_name)
			if(! tmp_file.renameTo(file)) {
				showToast(this, false, "File operation failed.")
				return null
			}
			
			return file
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(this, ex, "saveTimelineFont failed.")
			return null
		}
		
	}
	
	//////////////////////////////////////////////////////
	
	inner class AccountAdapter internal constructor() : BaseAdapter() {
		
		internal val list = java.util.ArrayList<SavedAccount>()
		
		init {
			for(a in SavedAccount.loadAccountList(this@ActAppSetting)) {
				if(a.isPseudo) continue
				list.add(a)
			}
			SavedAccount.sort(list)
		}
		
		override fun getCount() : Int {
			return 1 + list.size
		}
		
		override fun getItem(position : Int) : Any? {
			return if(position == 0) null else list[position - 1]
		}
		
		override fun getItemId(position : Int) : Long {
			return 0
		}
		
		override fun getView(position : Int, viewOld : View?, parent : ViewGroup) : View {
			val view = viewOld ?: layoutInflater.inflate(
				android.R.layout.simple_spinner_item,
				parent,
				false
			)
			view.findViewById<TextView>(android.R.id.text1).text =
				if(position == 0)
					getString(R.string.ask_always)
				else
					AcctColor.getNickname(list[position - 1].acct)
			return view
		}
		
		override fun getDropDownView(position : Int, viewOld : View?, parent : ViewGroup) : View {
			val view =
				viewOld ?: layoutInflater.inflate(R.layout.lv_spinner_dropdown, parent, false)
			view.findViewById<TextView>(android.R.id.text1).text =
				if(position == 0)
					getString(R.string.ask_always)
				else
					AcctColor.getNickname(list[position - 1].acct)
			return view
		}
		
		internal fun getIndexFromId(db_id : Long) : Int {
			var i = 0
			val ie = list.size
			while(i < ie) {
				if(list[i].db_id == db_id) return i + 1
				++ i
			}
			return 0
		}
		
		internal fun getIdFromIndex(position : Int) : Long {
			return if(position > 0) list[position - 1].db_id else - 1L
		}
	}
	
	private class Item(
		val id : String,
		val caption : String,
		val offset : Int
	)
	
	inner class TimeZoneAdapter internal constructor() : BaseAdapter() {
		
		private val list = ArrayList<Item>()
		
		init {
			
			for(id in TimeZone.getAvailableIDs()) {
				val tz = TimeZone.getTimeZone(id)
				
				// GMT数字を指定するタイプのタイムゾーンは無視する。ただしGMT-12:00の１項目だけは残す
				// 3文字のIDは曖昧な場合があるので非推奨
				// '/' を含まないIDは列挙しない
				if(! when {
						! tz.id.contains('/') -> false
						tz.id == "Etc/GMT+12" -> true
						tz.id.startsWith("Etc/") -> false
						else -> true
					}) continue
				
				var offset = tz.rawOffset.toLong()
				val caption = when(offset) {
					0L -> String.format("(UTC\u00B100:00) %s %s", tz.id, tz.displayName)
					
					else -> {
						
						val format = if(offset > 0)
							"(UTC+%02d:%02d) %s %s"
						else
							"(UTC-%02d:%02d) %s %s"
						
						offset = abs(offset)
						
						val hours = TimeUnit.MILLISECONDS.toHours(offset)
						val minutes =
							TimeUnit.MILLISECONDS.toMinutes(offset) - TimeUnit.HOURS.toMinutes(hours)
						
						
						
						String.format(format, hours, minutes, tz.id, tz.displayName)
					}
				}
				if(null == list.find { it.caption == caption }) {
					list.add(Item(id, caption, tz.rawOffset))
				}
			}
			
			list.sortWith(Comparator { a, b ->
				(a.offset - b.offset).notZero() ?: a.caption.compareTo(b.caption)
			})
			
			list.add(0, Item("", getString(R.string.device_timezone), 0))
		}
		
		override fun getCount() : Int {
			return list.size
		}
		
		override fun getItem(position : Int) : Any? {
			return list[position]
		}
		
		override fun getItemId(position : Int) : Long {
			return 0
		}
		
		override fun getView(position : Int, viewOld : View?, parent : ViewGroup) : View {
			val view = viewOld ?: layoutInflater.inflate(
				android.R.layout.simple_spinner_item,
				parent,
				false
			)
			val item = list[position]
			view.findViewById<TextView>(android.R.id.text1).text = item.caption
			return view
		}
		
		override fun getDropDownView(position : Int, viewOld : View?, parent : ViewGroup) : View {
			val view =
				viewOld ?: layoutInflater.inflate(R.layout.lv_spinner_dropdown, parent, false)
			val item = list[position]
			view.findViewById<TextView>(android.R.id.text1).text = item.caption
			return view
		}
		
		internal fun getIndexFromId(tz_id : String) : Int {
			val index = list.indexOfFirst { it.id == tz_id }
			return if(index == - 1) 0 else index
		}
		
		internal fun getIdFromIndex(position : Int) : String {
			return list[position].id
		}
	}
	
	fun openCustomShareChooser(target : CustomShareTarget) {
		try {
			DlgAppPicker(this){ setCustomShare(target, it) }.show()
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(this, ex, "openCustomShareChooser failed.")
		}
	}
	
	private fun onCustomShareSelected() {
		if(isDestroyed) return
		
		val cn = ChooseReceiver.lastComponentName
		if(cn != null) {
			ChooseReceiver.lastComponentName = null
			setCustomShare(customShareTarget, "${cn.packageName}/${cn.className}")
		}
	}
	
	fun setCustomShare(target : CustomShareTarget?, value : String) {
		
		target ?: return
		
		val item = when(target) {
			CustomShareTarget.Translate -> AppSettingItem.CUSTOM_TRANSLATE
			CustomShareTarget.CustomShare1 -> AppSettingItem.CUSTOM_SHARE_1
			CustomShareTarget.CustomShare2 -> AppSettingItem.CUSTOM_SHARE_2
			CustomShareTarget.CustomShare3 -> AppSettingItem.CUSTOM_SHARE_3
		}
			?: error("setCustomShare $target has no setting item.")
		
		val sp : StringPref = item.pref.cast() ?: error("$target: not StringPref")
		pref.edit().put(sp, value).apply()
		
		showCustomShareIcon(findItemViewHolder(item)?.textView1, target)
	}
	
	fun showCustomShareIcon(tv : TextView?, target : CustomShareTarget) {
		tv ?: return
		val cn = CustomShare.getCustomShareComponentName(pref, target)
		val (label, icon) =CustomShare.getInfo(this, cn)
		tv.text = label ?: getString(R.string.not_selected)
		tv.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
		tv.compoundDrawablePadding = (resources.displayMetrics.density * 4f + 0.5f).toInt()
	}
	
}