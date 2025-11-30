package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.database.Cursor
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actpost.DRAFT_CONTENT
import jp.juggler.subwaytooter.actpost.DRAFT_CONTENT_WARNING
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.databinding.LvDraftPickerBinding
import jp.juggler.subwaytooter.table.PostDraft
import jp.juggler.subwaytooter.table.daoPostDraft
import jp.juggler.util.*
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.cast
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.dismissSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

class DlgDraftPicker : AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener,
    DialogInterface.OnDismissListener {

    companion object {

        private val log = LogCategory("DlgDraftPicker")
    }

    private lateinit var activity: ActPost
    private lateinit var callback: (draft: JsonObject) -> Unit
    private lateinit var lvDraft: ListView
    private lateinit var adapter: MyAdapter
    private lateinit var dialog: AlertDialog

    private var listCursor: Cursor? = null
    private var colIdx: PostDraft.ColIdx? = null

    private var task: Job? = null

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val json = getPostDraft(position)?.json
        if (json != null) {
            callback(json)
            dialog.dismissSafe()
        }
    }

    override fun onItemLongClick(
        parent: AdapterView<*>,
        view: View,
        position: Int,
        id: Long,
    ): Boolean {
        activity.launchAndShowError {
            getPostDraft(position)?.let {
                daoPostDraft.delete(it)
                reload()
                activity.showToast(false, R.string.draft_deleted)
            }
        }
        return true
    }

    override fun onDismiss(dialog: DialogInterface) {
        task?.cancel()
        task = null
        lvDraft.adapter = null
        listCursor?.close()
        listCursor = null
    }

    @SuppressLint("InflateParams")
    fun open(activityArg: ActPost, callbackArg: (draft: JsonObject) -> Unit) {
        this.activity = activityArg
        this.callback = callbackArg

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
        task?.cancel()

        task = activity.launchAndShowError {
            val newCursor = try {
                withContext(AppDispatchers.IO) {
                    daoPostDraft.createCursor()
                }
            } catch (ignored: CancellationException) {
                return@launchAndShowError
            } catch (ex: Throwable) {
                log.e(ex, "failed to loading drafts.")
                activity.showToast(ex, "failed to loading drafts.")
                return@launchAndShowError
            }

            if (!dialog.isShowing) {
                // dialog is already closed.
                newCursor.close()
            } else {
                val old = listCursor
                listCursor = newCursor
                colIdx = PostDraft.ColIdx(newCursor)
                adapter.notifyDataSetChanged()
                old?.close()
            }
        }
    }

    private fun getPostDraft(position: Int): PostDraft? =
        listCursor?.let {
            daoPostDraft.loadFromCursor(it, colIdx, position)
        }

    private inner class MyViewHolder(
        parent: ViewGroup?,
    ) {
        val views = LvDraftPickerBinding.inflate(activity.layoutInflater, parent, false)
            .also { it.root.tag = this }

        fun bind(draft: PostDraft?) {
            draft ?: return
            val context = views.root.context
            views.tvTime.text =
                TootStatus.formatTime(context, draft.time_save, false)

            val json = draft.json
            if (json != null) {
                val cw = json.string(DRAFT_CONTENT_WARNING)
                val c = json.string(DRAFT_CONTENT)
                val sb = StringBuilder()
                if (cw?.trim { it <= ' ' }?.isNotEmpty() == true) {
                    sb.append(cw)
                }
                if (c?.trim { it <= ' ' }?.isNotEmpty() == true) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(c)
                }
                views.tvText.text = sb
            }
        }
    }

    private inner class MyAdapter : BaseAdapter() {
        override fun getCount() = listCursor?.count ?: 0
        override fun getItemId(position: Int) = 0L
        override fun getItem(position: Int) = getPostDraft(position)
        override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
            (convertView?.tag?.cast() ?: MyViewHolder(parent))
                .also { it.bind(getItem(position)) }
                .views.root
    }
}
