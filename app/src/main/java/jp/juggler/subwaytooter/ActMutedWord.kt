package jp.juggler.subwaytooter

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.databinding.ActWordListBinding
import jp.juggler.subwaytooter.databinding.LvMuteAppBinding
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.table.MutedWord
import jp.juggler.subwaytooter.table.daoMutedWord
import jp.juggler.util.backPressed
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.cast
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.setNavigationBack
import kotlinx.coroutines.withContext

class ActMutedWord : AppCompatActivity() {

    companion object {
        private val log = LogCategory("ActMutedWord")
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
        launchAndShowError {
            listAdapter.items = withContext(AppDispatchers.IO) {
                daoMutedWord.listAll()
            }
        }
    }

    private fun delete(item: MutedWord?) {
        item ?: return
        launchAndShowError {
            confirm(R.string.delete_confirm, item.name)
            daoMutedWord.delete(item.name)
            listAdapter.remove(item)
        }
    }

    // リスト要素のViewHolder
    private inner class MyViewHolder(parent: ViewGroup?) {
        val views = LvMuteAppBinding.inflate(layoutInflater, parent, false)
        private var lastItem: MutedWord? = null

        init {
            views.root.tag = this
            views.btnDelete.setOnClickListener { delete(lastItem) }
        }

        fun bind(item: MutedWord?) {
            item ?: return
            lastItem = item
            views.tvName.text = item.name
        }
    }

    private inner class MyListAdapter : BaseAdapter() {
        var items: List<MutedWord> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        fun remove(item: MutedWord?) {
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
