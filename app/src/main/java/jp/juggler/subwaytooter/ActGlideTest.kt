package jp.juggler.subwaytooter

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.databinding.ActGlideTestBinding
import jp.juggler.subwaytooter.databinding.LvGlideTestBinding
import jp.juggler.subwaytooter.span.EmojiSizeMode
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.ui.setNavigationBack
import kotlinx.coroutines.withContext

class ActGlideTest : AppCompatActivity() {
    private val views by lazy {
        ActGlideTestBinding.inflate(layoutInflater)
    }

    private val listAdapter by lazy {
        MyAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)
        setContentView(views.root)
        setSupportActionBar(views.toolbar)
        fixHorizontalMargin(views.rvImages)
        setNavigationBack(views.toolbar)

        views.rvImages.layoutManager = LinearLayoutManager(this)
        views.rvImages.adapter = listAdapter
        launchAndShowError {
            load()
        }
    }

    private suspend fun load() {
        listAdapter.items = withContext(AppDispatchers.IO) {
            buildList {
                repeat(300) {
                    arrayOf(
                        "gifAnime.gif",
                        "gif-anime-transparent.gif",
                        "jpeg.jpg",
                        "png.png",
                        "png-anime-gauge_charge.png",
                        "png-loading_blue.png",
                        "svg-anim1.svg",
                        "webp-anime-force.webp",
                        "webp-lossy-flag-off.webp",
                        "webp-lossy-flag-on.webp",
                        "webp-maker-no-flags.webp",
                        "webp-mixed-flag-on.webp",
                    ).map {
                        MyItem(name = it, url = "https://m1j.zzz.ac/tateisu/glideTest/$it")
                    }.let { addAll(it) }
                }
            }
        }
    }

    private class MyItem(
        val name: String,
        val url: String,
    )

    private val mainHandler by lazy {
        Handler(Looper.getMainLooper())
    }

    private inner class MyViewHolder(
        parent: ViewGroup,
        val views: LvGlideTestBinding =
            LvGlideTestBinding.inflate(layoutInflater, parent, false),
    ) : RecyclerView.ViewHolder(views.root) {

        private val nameInvalidator = NetworkEmojiInvalidator(mainHandler, views.tvName)

        fun bind(item: MyItem?) {
            item ?: return
            val density = views.root.context.resources.displayMetrics.density
            val r = (8f * density)
            views.nivStatic.setImageUrl(r, item.url, null)
            views.nivAnimation.setImageUrl(r, item.url, item.url)

            val text = SpannableStringBuilder().apply {
                val start = length
                append("a")
                val end = length
                val span = NetworkEmojiSpan(
                    url = item.url,
                    scale = 2f,
                    sizeMode = EmojiSizeMode.Square,
                )
                setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(" ")
                append(item.name)
            }
            nameInvalidator.text = text
        }
    }

    private inner class MyAdapter : RecyclerView.Adapter<MyViewHolder>() {
        var items: List<MyItem> = emptyList()
            set(value) {
                field = value
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            MyViewHolder(parent)

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            holder.bind(items.elementAtOrNull(position))
        }
    }
}