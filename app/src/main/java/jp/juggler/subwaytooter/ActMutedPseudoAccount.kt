package jp.juggler.subwaytooter

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.databinding.ActWordListBinding
import jp.juggler.subwaytooter.databinding.LvMuteAppBinding
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.table.daoUserRelation
import jp.juggler.subwaytooter.view.wrapTitleTextView
import jp.juggler.util.backPressed
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.cast
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.setContentViewAndInsets
import jp.juggler.util.ui.setNavigationBack
import kotlinx.coroutines.withContext

class ActMutedPseudoAccount : AppCompatActivity() {

    companion object {
        private val log = LogCategory("ActMutedPseudoAccount")
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
        setContentViewAndInsets(views.root)
        setSupportActionBar(views.toolbar)
        wrapTitleTextView()
        setNavigationBack(views.toolbar)
        fixHorizontalMargin(views.llContent)
        views.listView.adapter = listAdapter

        launchAndShowError {
            listAdapter.items = withContext(AppDispatchers.IO) {
                daoUserRelation.listPseudoMuted()
            }
        }
    }

    private fun delete(item: UserRelation?) {
        item ?: return
        launchAndShowError {
            confirm(R.string.delete_confirm, item.whoId)
            daoUserRelation.deletePseudo(item.id)
            listAdapter.remove(item)
        }
    }

    // リスト要素のViewHolder
    private inner class MyViewHolder(parent: ViewGroup?) {
        val views = LvMuteAppBinding.inflate(layoutInflater, parent, false)
        private var lastItem: UserRelation? = null

        init {
            views.root.tag = this
            views.btnDelete.setOnClickListener { delete(lastItem) }
        }

        fun bind(item: UserRelation?) {
            item ?: return
            lastItem = item
            views.tvName.text = item.whoId
        }
    }

    private inner class MyListAdapter : BaseAdapter() {
        var items: List<UserRelation> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        fun remove(item: UserRelation) {
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
