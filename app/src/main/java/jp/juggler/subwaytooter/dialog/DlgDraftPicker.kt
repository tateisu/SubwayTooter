package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.database.Cursor
import android.os.AsyncTask
import androidx.appcompat.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.PostDraft
import jp.juggler.util.dismissSafe
import jp.juggler.util.parseString
import jp.juggler.util.showToast
import org.json.JSONObject

class DlgDraftPicker : AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener,
	DialogInterface.OnDismissListener {
	
	private lateinit var activity : ActPost
	private lateinit var callback : (draft : JSONObject) -> Unit
	private lateinit var lvDraft : ListView
	private lateinit var adapter : MyAdapter
	private lateinit var dialog : AlertDialog
	
	private var cursor : Cursor? = null
	private var colIdx : PostDraft.ColIdx? = null
	
	private var task : AsyncTask<Void, Void, Cursor?>? = null
	
	override fun onItemClick(parent : AdapterView<*>, view : View, position : Int, id : Long) {
		val json = getPostDraft(position)?.json
		if(json != null) {
			callback(json)
			dialog.dismissSafe()
		}
	}
	
	override fun onItemLongClick(
		parent : AdapterView<*>,
		view : View,
		position : Int,
		id : Long
	) : Boolean {
		
		val draft = getPostDraft(position)
		if(draft != null) {
			showToast(activity, false, R.string.draft_deleted)
			draft.delete()
			reload()
			return true
		}
		
		return false
	}
	
	override fun onDismiss(dialog : DialogInterface) {
		task?.cancel(true)
		task = null
		
		lvDraft.adapter = null
		
		cursor?.close()
	}
	
	@SuppressLint("InflateParams")
	fun open(_activity : ActPost, _callback : (draft : JSONObject) -> Unit) {
		this.activity = _activity
		this.callback = _callback
		
		adapter = MyAdapter()
		
		val viewRoot = activity.layoutInflater.inflate(R.layout.dlg_draft_picker, null, false)
		
		lvDraft = viewRoot.findViewById(R.id.lvDraft)
		lvDraft.onItemClickListener = this
		lvDraft.onItemLongClickListener = this
		lvDraft.adapter = adapter
		
		this.dialog = AlertDialog.Builder(activity)
			.setTitle(R.string.select_draft)
			.setNegativeButton(R.string.cancel, null)
			.setView(viewRoot)
			.create()
		dialog.setOnDismissListener(this)
		
		dialog.show()
		
		reload()
	}
	
	private fun reload() {
		
		// cancel old task
		task?.cancel(true)
		
		val new_task = @SuppressLint("StaticFieldLeak")
		object : AsyncTask<Void, Void, Cursor?>() {
			override fun doInBackground(vararg params : Void) : Cursor? {
				return PostDraft.createCursor()
			}
			
			override fun onCancelled(cursor : Cursor?) {
				onPostExecute(cursor)
			}
			
			override fun onPostExecute(cursor : Cursor?) {
				if(! dialog.isShowing) {
					// dialog is already closed.
					cursor?.close()
					return
				}
				
				if(cursor == null) {
					// load failed.
					showToast(activity, true, "failed to loading drafts.")
				} else {
					this@DlgDraftPicker.cursor = cursor
					colIdx = PostDraft.ColIdx(cursor)
					adapter.notifyDataSetChanged()
				}
			}
		}
		this.task = new_task
		new_task.executeOnExecutor(App1.task_executor)
	}
	
	private fun getPostDraft(position : Int) : PostDraft? {
		val cursor = this.cursor
		return if(cursor == null) null else PostDraft.loadFromCursor(cursor, colIdx, position)
	}
	
	private inner class MyViewHolder internal constructor(view : View) {
		internal val tvTime : TextView
		internal val tvText : TextView
		
		init {
			tvTime = view.findViewById(R.id.tvTime)
			tvText = view.findViewById(R.id.tvText)
		}
		
		fun bind(position : Int) {
			val draft = getPostDraft(position) ?: return
			
			tvTime.text = TootStatus.formatTime(tvTime.context, draft.time_save, false)
			
			val json = draft.json
			if(json != null) {
				val cw = json.parseString(ActPost.DRAFT_CONTENT_WARNING)
				val c = json.parseString(ActPost.DRAFT_CONTENT)
				val sb = StringBuilder()
				if(cw?.trim { it <= ' ' }?.isNotEmpty() == true) {
					sb.append(cw)
				}
				if(c?.trim { it <= ' ' }?.isNotEmpty() == true) {
					if(sb.isNotEmpty()) sb.append("\n")
					sb.append(c)
				}
				tvText.text = sb
			}
		}
	}
	
	private inner class MyAdapter : BaseAdapter() {
		
		override fun getCount() : Int {
			return cursor?.count ?: 0
		}
		
		override fun getItem(position : Int) : Any? {
			return getPostDraft(position)
		}
		
		override fun getItemId(position : Int) : Long {
			return 0
		}
		
		override fun getView(position : Int, viewOld : View?, parent : ViewGroup) : View {
			val view : View
			val holder : MyViewHolder
			if(viewOld == null) {
				view = activity.layoutInflater.inflate(R.layout.lv_draft_picker, parent, false)
				holder = MyViewHolder(view)
				view.tag = holder
			} else {
				view = viewOld
				holder = view.tag as MyViewHolder
			}
			holder.bind(position)
			return view
		}
	}
}
