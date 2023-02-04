package jp.juggler.subwaytooter

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.databinding.ActHighlightListBinding
import jp.juggler.subwaytooter.databinding.LvHighlightWordBinding
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.daoHighlightWord
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.cast
import jp.juggler.util.data.mayUri
import jp.juggler.util.data.notBlank
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.*
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class ActHighlightWordList : AppCompatActivity() {

    companion object {
        private val log = LogCategory("ActHighlightWordList")

        private var lastRingtone: WeakReference<Ringtone>? = null

        private fun stopLastRingtone() {
            try {
                lastRingtone?.get()?.stop()
            } catch (ex: Throwable) {
                log.e(ex, "stopLastRingtone failed.")
            } finally {
                lastRingtone = null
            }
        }

        private fun tryRingTone(context: Context, uri: Uri?): Boolean {
            try {
                uri?.let { RingtoneManager.getRingtone(context, it) }
                    ?.let { ringtone ->
                        stopLastRingtone()
                        lastRingtone = WeakReference(ringtone)
                        ringtone.play()
                        return true
                    }
            } catch (ex: Throwable) {
                log.e(ex, "tryRingTone failed.")
            }
            // fall null case
            return false
        }

        fun sound(context: Context, item: HighlightWord?) {
            if (lastRingtone?.get()?.isPlaying == true) {
                stopLastRingtone()
                return
            }

            item ?: return
            val soundType = item.sound_type
            when {
                // サウンドなし
                soundType == HighlightWord.SOUND_TYPE_NONE -> Unit

                // カスタムサウンドを鳴らせた
                soundType == HighlightWord.SOUND_TYPE_CUSTOM && tryRingTone(
                    context,
                    item.sound_uri.mayUri()
                ) -> Unit

                // 失敗した場合も通常の音を鳴らす
                else -> tryRingTone(
                    context,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                )
            }
        }
    }

    private val views by lazy {
        ActHighlightListBinding.inflate(layoutInflater)
    }

    private lateinit var listAdapter: MyListAdapter

    private val arEdit = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        loadData()
    }

    //	@Override public void onBackPressed(){
    //		setResult( RESULT_OK );
    //		super.onBackPressed();
    //	}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arEdit.register(this)
        App1.setActivityTheme(this)
        initUI()
        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLastRingtone()
    }

    private fun initUI() {
        setContentView(views.root)
        setSupportActionBar(views.toolbar)
        setNavigationBack(views.toolbar)
        fixHorizontalMargin(views.llContent)

        // リストのアダプター
        listAdapter = MyListAdapter()

        // ハンドル部分をドラッグで並べ替えできるRecyclerView

        views.listView.adapter = listAdapter
        views.listView.onItemClickListener = listAdapter
        views.btnAdd.setOnClickListener { create() }
    }

    private fun loadData() {
        launchAndShowError {
            listAdapter.items = withContext(AppDispatchers.IO) {
                daoHighlightWord.listAll()
            }
        }
    }

    private fun create() {
        arEdit.launch(ActHighlightWordEdit.createIntent(this, ""))
    }

    private fun edit(item: HighlightWord?) {
        item ?: return
        arEdit.launch(ActHighlightWordEdit.createIntent(this, item.id))
    }

    private fun delete(item: HighlightWord?) {
        item ?: return
        val activity = this
        launchAndShowError {
            confirm(getString(R.string.delete_confirm, item.name))
            daoHighlightWord.delete(applicationContext, item)
            listAdapter.remove(item)
            App1.getAppState(activity).enableSpeech()
        }
    }

    private fun speech(item: HighlightWord?) {
        item?.name?.notBlank()?.let {
            App1.getAppState(this@ActHighlightWordList)
                .addSpeech(it, dedupMode = DedupMode.None)
        }
    }

    // リスト要素のViewHolder
    private inner class MyViewHolder(
        parent: ViewGroup?,
    ) {
        val views = LvHighlightWordBinding.inflate(layoutInflater, parent, false)

        private var lastItem: HighlightWord? = null

        init {
            views.root.tag = this
            views.btnSound.setOnClickListener { sound(this@ActHighlightWordList, lastItem) }
            views.btnSpeech.setOnClickListener { speech(lastItem) }
            views.btnDelete.setOnClickListener { delete(lastItem) }
        }

        fun bind(item: HighlightWord?) {
            item ?: return
            lastItem = item

            views.tvName.text = item.name
            views.tvName.setBackgroundColor(item.color_bg)
            views.tvName.setTextColor(
                item.color_fg.notZero()
                    ?: attrColor(android.R.attr.textColorPrimary)
            )

            views.btnSound.isEnabledAlpha = item.sound_type != HighlightWord.SOUND_TYPE_NONE
            views.btnSpeech.isEnabledAlpha = item.speech != 0
        }
    }

    private inner class MyListAdapter : BaseAdapter(), AdapterView.OnItemClickListener {

        var items: List<HighlightWord> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        fun remove(item: HighlightWord) {
            items = items.filter { it != item }
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items.elementAtOrNull(position)
        override fun getItemId(position: Int) = 0L

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?) =
            (convertView?.tag?.cast() ?: MyViewHolder(parent))
                .also { it.bind(items.elementAtOrNull(position)) }
                .views.root

        override fun onItemClick(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) {
            edit(items.elementAtOrNull(position))
        }
    }
}
