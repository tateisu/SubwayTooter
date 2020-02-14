package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.CustomShare
import jp.juggler.subwaytooter.action.cn
import jp.juggler.util.*

class DlgAppPicker(
	val activity : AppCompatActivity,
	val intent : Intent,
	val autoSelect : Boolean = false,
	val filter : (ResolveInfo) -> Boolean = { true },
	val callback : (String) -> Unit
) {
	
	companion object {
		fun Char.isAlpha() = ('A' <= this && this <= 'Z') || ('a' <= this && this <= 'z')
	}
	
	class ListItem(
		val icon : Drawable?,
		val text : String,
		val componentName : String
	)
	
	val list = ArrayList<ListItem>().apply {
		
		val pm = activity.packageManager
		val listResolveInfo = pm.queryIntentActivities(
			intent,
			if(Build.VERSION.SDK_INT >= 23) {
				PackageManager.MATCH_ALL
			} else {
				0 // PackageManager.MATCH_DEFAULT_ONLY
			}
		)
		
		for(it in listResolveInfo) {
			if(! filter(it)) continue
			val cn = "${it.activityInfo.packageName}/${it.activityInfo.name}"
			add(
				ListItem(
					it.loadIcon(pm),
					(it.loadLabel(pm)?.notEmpty() ?: cn).toString(),
					cn
				)
			)
		}
		
		if(! autoSelect) {
			val (label, icon) = CustomShare.getInfo(activity, CustomShare.CN_CLIPBOARD.cn())
			add(ListItem(icon, label.toString(), CustomShare.CN_CLIPBOARD))
		}
		sortWith(Comparator { a, b ->
			val a1 = a.text.firstOrNull() ?: '\u0000'
			val b1 = b.text.firstOrNull() ?: '\u0000'
			when {
				! a1.isAlpha() && b1.isAlpha() -> - 1
				a1.isAlpha() && ! b1.isAlpha() -> 1
				else -> a.text.compareTo(b.text, ignoreCase = true)
			}
		})
	}
	
	val dialog : AlertDialog?
	
	init {
		if(autoSelect && list.size == 1) {
			callback(list.first().componentName)
			dialog = null
		} else {
			@SuppressLint("InflateParams")
			val listView : ListView =
				activity.layoutInflater.inflate(R.layout.dlg_app_picker, null, false).cast() !!
			val adapter = MyAdapter()
			listView.adapter = adapter
			listView.onItemClickListener = adapter
			
			
			dialog = AlertDialog.Builder(activity)
				.setView(listView)
				.setNegativeButton(R.string.cancel, null)
				.create()
		}
	}
	
	@SuppressLint("InflateParams")
	fun show() {
		dialog?.run {
			window?.setLayout(
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.WRAP_CONTENT
			)
			this.show()
		}
	}
	
	private inner class MyAdapter : BaseAdapter(), AdapterView.OnItemClickListener {
		
		override fun getCount() : Int = list.size
		override fun getItem(idx : Int) = list[idx]
		override fun getItemId(p0 : Int) = 0L
		
		override fun getView(idx : Int, convertView : View?, parent : ViewGroup?) : View {
			val view : View
			val holder : MyViewHolder
			if(convertView != null) {
				view = convertView
				holder = view.tag?.cast() !!
			} else {
				view = activity.layoutInflater.inflate(R.layout.lv_app_picker, parent, false)
				holder = MyViewHolder(view)
				view.tag = holder
			}
			holder.bind(list[idx])
			return view
		}
		
		override fun onItemClick(parent : AdapterView<*>?, view : View?, idx : Int, id : Long) {
			dialog?.dismissSafe()
			callback(list[idx].componentName)
		}
	}
	
	private inner class MyViewHolder(viewRoot : View) {
		val ivImage : ImageView = viewRoot.findViewById(R.id.ivImage)
		val tvText : TextView = viewRoot.findViewById(R.id.tvText)
		var item : ListItem? = null
		
		fun bind(item : ListItem) {
			this.item = item
			if(item.icon != null) {
				ivImage.setImageDrawable(item.icon)
			} else {
				setIconDrawableId(
					activity,
					ivImage,
					R.drawable.ic_question,
					color = getAttributeColor(activity, R.attr.colorVectorDrawable),
					alphaMultiplier = 1f
				)
			}
			tvText.text = item.text
		}
	}
}


