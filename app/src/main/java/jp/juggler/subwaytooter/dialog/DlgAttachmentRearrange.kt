package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.databinding.AttachmentRearrangeDialogBinding
import jp.juggler.subwaytooter.databinding.AttachmentsRearrangeItemBinding
import jp.juggler.subwaytooter.defaultColorIcon
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.util.data.ellipsizeDot3
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.anko.backgroundColor
import kotlin.coroutines.resumeWithException

suspend fun ActPost.dialogArrachmentRearrange(
    initialList: List<PostAttachment>,
): List<PostAttachment> = suspendCancellableCoroutine { cont ->
    val views = AttachmentRearrangeDialogBinding.inflate(layoutInflater)
    val dialog = Dialog(this)
    dialog.setContentView(views.root)

    cont.invokeOnCancellation { dialog.dismissSafe() }

    dialog.setOnDismissListener {
        if (cont.isActive) cont.resumeWithException(CancellationException())
    }

    val rearrangeAdapter = RearrangeAdapter(layoutInflater, initialList)

    views.btnCancel.setOnClickListener {
        dialog.dismissSafe()
    }

    views.btnOk.setOnClickListener {
        if (cont.isActive) {
            cont.resume(rearrangeAdapter.list) {}
        }
        dialog.dismissSafe()
    }

    views.listView.apply {
        layoutManager = LinearLayoutManager(context)
        adapter = rearrangeAdapter
        rearrangeAdapter.itemTouchHelper.attachToRecyclerView(this)
    }

    dialog.window?.setLayout(dp(300), dp(440))
    dialog.show()
}

private class RearrangeAdapter(
    private val inflater: LayoutInflater,
    initialList: List<PostAttachment>,
) : RecyclerView.Adapter<RearrangeAdapter.MyViewHolder>(),
    MyDragCallback.Changer {

    val list = ArrayList(initialList)

    private var lastStateViewHolder: MyViewHolder? = null
    private var draggingItem: PostAttachment? = null

    val itemTouchHelper by lazy {
        ItemTouchHelper(MyDragCallback(this))
    }

    override fun getItemCount() = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MyViewHolder(parent)

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.bind(list.elementAtOrNull(position))
    }

    override fun onMove(posFrom: Int, posTo: Int): Boolean {
        val item = list.removeAt(posFrom)
        list.add(posTo, item)
        notifyItemMoved(posFrom, posTo)
        return true
    }

    override fun onState(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        val holder = (viewHolder as? MyViewHolder)
        holder?.let { lastStateViewHolder = it }
        val pa = holder?.lastItem
        draggingItem = when {
            pa != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG -> pa
            else -> null
        }
        holder?.bind()
        lastStateViewHolder?.takeIf { it != holder }?.bind()
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class MyViewHolder(
        parent: ViewGroup,
        val views: AttachmentsRearrangeItemBinding =
            AttachmentsRearrangeItemBinding.inflate(inflater, parent, false),
    ) : RecyclerView.ViewHolder(views.root) {

        var lastItem: PostAttachment? = null

        init {
            views.root.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(this)
                }
                false
            }
        }

        fun bind(pa: PostAttachment? = lastItem) {
            pa ?: return
            lastItem = pa

            val context = views.root.context

            views.root.apply {
                when {
                    draggingItem === pa -> backgroundColor =
                        context.attrColor(R.attr.colorSearchFormBackground)

                    else -> background = null
                }
            }

            views.ivThumbnail.apply {
                val imageUrl = pa.attachment?.preview_url
                if (imageUrl.isNullOrEmpty()) {
                    val imageId = when (pa.status) {
                        PostAttachment.Status.Progress -> R.drawable.ic_upload
                        PostAttachment.Status.Error -> R.drawable.ic_error
                        else -> R.drawable.ic_clip
                    }
                    Glide.with(context).clear(this)
                    setImageDrawable(defaultColorIcon(context, imageId))
                } else {
                    Glide.with(context)
                        .load(imageUrl)
                        .placeholder(defaultColorIcon(context, R.drawable.ic_hourglass))
                        .error(defaultColorIcon(context, R.drawable.ic_error))
                        .fallback(defaultColorIcon(context, R.drawable.ic_clip))
                        .into(this)
                }
            }
            views.tvText.text = pa.attachment?.run {
                "$type ${description?.ellipsizeDot3(40) ?: ""}"
            } ?: context.getString(R.string.attachment_uploading)
        }
    }
}

private class MyDragCallback(
    private val changer: Changer,
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    0 // no swipe
) {
    interface Changer {
        fun onMove(posFrom: Int, posTo: Int): Boolean
        fun onState(viewHolder: RecyclerView.ViewHolder?, actionState: Int)
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
        changer.onState(viewHolder, actionState)
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ) {
        super.clearView(recyclerView, viewHolder)
        changer.onState(null, ItemTouchHelper.ACTION_STATE_IDLE)
    }
}
