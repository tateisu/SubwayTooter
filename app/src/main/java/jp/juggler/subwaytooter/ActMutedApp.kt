package jp.juggler.subwaytooter

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.databinding.ActWordListBinding
import jp.juggler.subwaytooter.databinding.LvMuteAppBinding
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.table.MutedApp
import jp.juggler.util.backPressed
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.cast
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.setNavigationBack

class ActMutedApp : AppCompatActivity() {

    companion object {
        private val log = LogCategory("ActMutedApp")
    }

    private val views by lazy {
        ActWordListBinding.inflate(layoutInflater)
    }

    private val listAdapter by lazy { MyListAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backPressed {
            setResult(RESULT_OK)
            finish()
        }
        App1.setActivityTheme(this)
        initUI()
        loadData()
    }

    private fun initUI() {
        setContentView(views.root)
        setSupportActionBar(views.toolbar)
        setNavigationBack(views.toolbar)
        fixHorizontalMargin(views.llContent)
        views.listView.adapter = listAdapter
    }

    private fun loadData() {
        listAdapter.items = buildList {
            try {
                MutedApp.createCursor().use { cursor ->
                    val idxId = cursor.getColumnIndex(MutedApp.COL_ID)
                    val idxName = cursor.getColumnIndex(MutedApp.COL_NAME)
                    while (cursor.moveToNext()) {
                        val item = MyItem(
                            id = cursor.getLong(idxId),
                            name = cursor.getString(idxName)
                        )
                        add(item)
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "loadData failed.")
            }
        }
    }

    private fun delete(item: MyItem?) {
        item ?: return
        launchAndShowError {
            confirm(R.string.delete_confirm, item.name)
            MutedApp.delete(item.name)
            listAdapter.remove(item)
        }
    }

    // リスト要素のデータ
    private class MyItem(val id: Long, val name: String)

    // リスト要素のViewHolder
    private inner class MyViewHolder(parent: ViewGroup?) {
        val views = LvMuteAppBinding.inflate(layoutInflater, parent, false)
        var lastItem: MyItem? = null

        init {
            views.root.tag = this
            views.btnDelete.setOnClickListener { delete(lastItem) }
        }

        fun bind(item: MyItem?) {
            item ?: return
            lastItem = item
            views.tvName.text = item.name
        }
    }

    private inner class MyListAdapter : BaseAdapter() {
        var items: List<MyItem> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        fun remove(item: MyItem) {
            items = items.filter { it != item }
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items.elementAtOrNull(position)
        override fun getItemId(position: Int) = 0L
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?) =
            (convertView?.tag?.cast() ?: MyViewHolder(parent))
                .also { it.bind(items.elementAtOrNull(position)) }
                .views.root

        override fun isEnabled(position: Int) = false
    }
}
