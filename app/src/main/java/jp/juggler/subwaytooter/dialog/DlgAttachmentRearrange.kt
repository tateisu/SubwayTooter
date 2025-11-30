package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.databinding.AttachmentRearrangeDialogBinding
import jp.juggler.subwaytooter.databinding.AttachmentsRearrangeItemBinding
import jp.juggler.subwaytooter.defaultColorIcon
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.util.coroutine.cancellationException
import jp.juggler.util.data.ellipsizeDot3
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.dp
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.anko.backgroundColor
import kotlin.coroutines.resumeWithException

private val log = LogCategory("DlgAttachmentRearrange")

/**
 * 投稿画面で添付メディアを並べ替えるダイアログを開き、OKボタンが押されるまで非同期待機する。
 * OK以外の方法で閉じたらCancellationExceptionを投げる。
 */
suspend fun AppCompatActivity.dialogAttachmentRearrange(
    initialList: List<PostAttachment>,
): List<PostAttachment> = suspendCancellableCoroutine { cont ->
    val views = AttachmentRearrangeDialogBinding.inflate(layoutInflater)
    val dialog = Dialog(this).apply {
        setContentView(views.root)
        setOnDismissListener {
            if (cont.isActive) cont.resumeWithException(cancellationException())
        }
    }

    cont.invokeOnCancellation { dialog.dismissSafe() }

    val myAdapter = RearrangeAdapter(layoutInflater, initialList)

    views.btnCancel.setOnClickListener {
        dialog.dismissSafe()
    }

    views.btnOk.setOnClickListener {
        if (cont.isActive) cont.resume(myAdapter.list) { _, _, _ -> }
        dialog.dismissSafe()
    }

    views.listView.apply {
        layoutManager = LinearLayoutManager(context)
        adapter = myAdapter
        myAdapter.itemTouchHelper.attachToRecyclerView(this)
    }

    dialog.window?.setLayout(dp(300), dp(440))
    dialog.show()
}

/**
 * 並べ替えダイアログ内部のRecyclerViewに使うAdapter
 */
private class RearrangeAdapter(
    private val inflater: LayoutInflater,
    initialList: List<PostAttachment>,
) : RecyclerView.Adapter<RearrangeAdapter.MyViewHolder>(), MyDragCallback.Changer {

    val list = ArrayList(initialList)

    private var lastStateViewHolder: MyViewHolder? = null
    private var draggingItem: PostAttachment? = null

    val itemTouchHelper = ItemTouchHelper(MyDragCallback(this))

    override fun getItemCount() = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MyViewHolder(parent)

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.bind(list.elementAtOrNull(position))
    }

    // implements MyDragCallback.Changer
    override fun onMove(posFrom: Int, posTo: Int): Boolean {
        val item = list.removeAt(posFrom)
        list.add(posTo, item)
        notifyItemMoved(posFrom, posTo)
        return true
    }

    // implements MyDragCallback.Changer
    override fun onState(
        caller: String,
        viewHolder: RecyclerView.ViewHolder?,
        actionState: Int,
    ) {
        log.d("onState: caller=$caller, viewHolder=$viewHolder, actionState=$actionState")

        val holder = (viewHolder as? MyViewHolder)
        // 最後にドラッグ対象となったViewHolderを覚えておく
        holder?.let { lastStateViewHolder = it }
        // 現在ドラッグ対象のPostAttachmentを覚えておく
        val pa = holder?.lastItem
        draggingItem = when {
            pa != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG -> pa
            else -> null
        }
        // 表示の更新
        holder?.bind()
        lastStateViewHolder?.takeIf { it != holder }?.bind()
    }

    private val iconPlaceHolder = defaultColorIcon(inflater.context, R.drawable.ic_hourglass)
    private val iconError = defaultColorIcon(inflater.context, R.drawable.ic_error)
    private val iconFallback = defaultColorIcon(inflater.context, R.drawable.ic_clip)

    @SuppressLint("ClickableViewAccessibility")
    inner class MyViewHolder(
        parent: ViewGroup,
        val views: AttachmentsRearrangeItemBinding =
            AttachmentsRearrangeItemBinding.inflate(inflater, parent, false),
    ) : RecyclerView.ViewHolder(views.root) {

        var lastItem: PostAttachment? = null

        init {
            // リスト項目のタッチですぐにドラッグを開始する
            views.root.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(this)
                }
                false
            }
        }

        fun bind(item: PostAttachment? = lastItem) {
            item ?: return
            lastItem = item

            val context = views.root.context

            // ドラッグ中は背景色を変える
            views.root.apply {
                when {
                    draggingItem === item -> backgroundColor =
                        context.attrColor(R.attr.colorSearchFormBackground)

                    else -> background = null
                }
            }

            // サムネイルのロード開始
            views.ivThumbnail.apply {
                when (val imageUrl = item.attachment?.preview_url) {
                    null, "" -> {
                        val iconDrawable = when (item.status) {
                            PostAttachment.Status.Progress -> iconPlaceHolder
                            PostAttachment.Status.Error -> iconError
                            else -> iconFallback
                        }
                        Glide.with(context).clear(this)
                        setImageDrawable(iconDrawable)
                    }

                    else -> {
                        Glide.with(context)
                            .load(imageUrl)
                            .placeholder(iconPlaceHolder)
                            .error(iconError)
                            .fallback(iconFallback)
                            .into(this)
                    }
                }
            }

            // テキストの表示
            views.tvText.text = item.attachment?.run {
                "${type.id} ${description?.ellipsizeDot3(40) ?: ""}"
            } ?: ""
        }
    }
}

/**
 * RectclerViewのDrag&Drop操作に関するコールバック
 */
private class MyDragCallback(
    private val changer: Changer,
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    0 // no swipe
) {
    // アダプタに行わせたい処理のinterface
    interface Changer {
        fun onMove(posFrom: Int, posTo: Int): Boolean
        fun onState(caller: String, viewHolder: RecyclerView.ViewHolder?, actionState: Int)
    }

    override fun isLongPressDragEnabled() = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = changer.onMove(
        // position of drag from
        viewHolder.bindingAdapterPosition,
        // position of drag to
        target.bindingAdapterPosition,
    )

    override fun onSelectedChanged(
        viewHolder: RecyclerView.ViewHolder?,
        actionState: Int,
    ) {
        super.onSelectedChanged(viewHolder, actionState)
        changer.onState("onSelectedChanged", viewHolder, actionState)
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ) {
        super.clearView(recyclerView, viewHolder)
        changer.onState("clearView", viewHolder, ItemTouchHelper.ACTION_STATE_IDLE)
    }
}
