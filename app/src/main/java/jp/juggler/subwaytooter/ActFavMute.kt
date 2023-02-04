package jp.juggler.subwaytooter

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.databinding.ActWordListBinding
import jp.juggler.subwaytooter.databinding.LvMuteAppBinding
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.table.daoFavMute
import jp.juggler.util.backPressed
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.cast
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.setNavigationBack
import kotlinx.coroutines.withContext

class ActFavMute : AppCompatActivity() {

    companion object {
        private val log = LogCategory("ActFavMute")
    }

    private val views by lazy {
        ActWordListBinding.inflate(layoutInflater)
    }

    private val listAdapter by lazy { MyListAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)
        backPressed {
            setResult(RESULT_OK)
            finish()
        }
        initUI()
        loadData()
    }

    private fun initUI() {
        setContentView(views.root)
        setSupportActionBar(views.toolbar)
        setNavigationBack(views.toolbar)
        fixHorizontalMargin(views.llContent)
        views.tvFooterDesc.text = getString(R.string.fav_muted_user_desc)
        views.listView.adapter = listAdapter
    }

    private fun delete(item: MyItem?) {
        item ?: return
        launchAndShowError {
            confirm(R.string.delete_confirm, item.acct.pretty)
            daoFavMute.delete(item.acct)
            listAdapter.remove(item)
        }
    }

    private fun loadData() {
        launchAndShowError {
            listAdapter.items = withContext(AppDispatchers.IO) {
                daoFavMute.listAll().map {
                    MyItem(
                        id = it.id,
                        acct = Acct.parse(it.acct),
                    )
                }
            }
        }
    }

    // リスト要素のデータ
    internal class MyItem(val id: Long, val acct: Acct)

    // リスト要素のViewHolder
    private inner class MyViewHolder(parent: ViewGroup?) {
        val views = LvMuteAppBinding.inflate(layoutInflater, parent, false)

        private var lastItem: MyItem? = null

        init {
            views.root.tag = this
            views.btnDelete.setOnClickListener { delete(lastItem) }
        }

        fun bind(item: MyItem?) {
            item ?: return
            lastItem = item
            views.tvName.text = item.acct.pretty
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
