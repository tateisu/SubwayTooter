package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import jp.juggler.subwaytooter.api.entity.NotificationType.Companion.toNotificationType
import jp.juggler.subwaytooter.databinding.ActPushMessageListBinding
import jp.juggler.subwaytooter.databinding.LvPushMessageBinding
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.dialog.runInProgress
import jp.juggler.subwaytooter.push.PushMessageIconColor
import jp.juggler.subwaytooter.push.pushMessageIconAndColor
import jp.juggler.subwaytooter.push.pushRepo
import jp.juggler.subwaytooter.table.PushMessage
import jp.juggler.subwaytooter.table.daoAccountNotificationStatus
import jp.juggler.subwaytooter.table.daoPushMessage
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.util.permissionSpecNotification
import jp.juggler.subwaytooter.util.requester
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.encodeBase64Url
import jp.juggler.util.data.notBlank
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.dialogOrToast
import jp.juggler.util.os.saveToDownload
import jp.juggler.util.time.formatLocalTime
import jp.juggler.util.ui.setContentViewAndInsets
import jp.juggler.util.ui.setNavigationBack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter

class ActPushMessageList : AppCompatActivity() {
    companion object {
        private val log = LogCategory("ActPushMessageList")
    }

    private val views by lazy {
        ActPushMessageListBinding.inflate(layoutInflater)
    }

    private val listAdapter = MyAdapter()

    private val layoutManager by lazy {
        LinearLayoutManager(this)
    }

    private val prNotification = permissionSpecNotification.requester {
        // 特に何もしない
    }
    private val acctMap by lazy {
        daoSavedAccount.loadRealAccounts().associateBy { it.acct }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prNotification.register(this)
        prNotification.checkOrLaunch()
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)
        setContentViewAndInsets(views.root)
        setSupportActionBar(views.toolbar)
        setNavigationBack(views.toolbar)

        views.rvMessages.also {
            val dividerItemDecoration = DividerItemDecoration(
                this,
                LinearLayout.VERTICAL,
            )
            it.addItemDecoration(dividerItemDecoration)
            it.adapter = listAdapter
            it.layoutManager = layoutManager
        }

        lifecycleScope.launch {
            PushMessage.flowDataChanged.collect {
                try {
                    listAdapter.items = withContext(AppDispatchers.IO) {
                        daoPushMessage.listAll()
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "load failed.")
                }
            }
        }
    }

    fun itemActions(pm: PushMessage) {
        launchAndShowError {
            actionsDialog {
                action(getString(R.string.push_message_re_decode)) {
                    pushRepo.reprocess(pm)
                }
                action(getString(R.string.push_message_save_to_download_folder)) {
                    export(pm)
                }
                action(getString(R.string.push_message_save_to_download_folder_with_secret_key)) {
                    export(pm, exportKeys = true)
                }
            }
        }
    }

    /**
     * エクスポート、というか端末のダウンロードフォルダに保存する
     */
    private suspend fun export(pm: PushMessage, exportKeys: Boolean = false) {
        val path = runInProgress {
            withContext(AppDispatchers.DEFAULT) {
                saveToDownload(
                    displayName = "PushMessageDump-${pm.id}.txt",
                ) { PrintWriter(it).apply { dumpMessage(pm, exportKeys) }.flush() }
            }
        }
        if (!path.isNullOrEmpty()) {
            dialogOrToast(R.string.saved_to, path)
        }
    }

    private fun PrintWriter.dumpMessage(pm: PushMessage, exportKeys: Boolean) {
        println("timestamp: ${pm.timestamp.formatLocalTime()}")
        println("timeSave: ${pm.timeSave.formatLocalTime()}")
        println("timeDismiss: ${pm.timeDismiss.formatLocalTime()}")
        println("to: ${pm.loginAcct}")
        println("type: ${pm.notificationType}")
        println("id: ${pm.notificationId}")
        println("text: ${pm.textExpand}")
        println("formatJson=${pm.formatJson.toString(1, sort = true)}")
        println("messageJson=${pm.messageJson?.toString(1, sort = true)}")
        println("dataSize: ${pm.rawBody?.size}")
        if (exportKeys) {
            val acct = pm.loginAcct
            if (acct == null) {
                println("!!secret key is not exported because missing recepients acct.")
            } else {
                val status = daoAccountNotificationStatus.load(acct)
                if (status == null) {
                    println("!!secret key is not exported because missing status for acct $acct .")
                } else {
                    println("receiverPrivateBytes=${status.pushKeyPrivate?.encodeBase64Url()}")
                    println("receiverPublicBytes=${status.pushKeyPublic?.encodeBase64Url()}")
                    println("senderPublicBytes=${status.pushServerKey?.encodeBase64Url()}")
                    println("authSecret=${status.pushAuthSecret?.encodeBase64Url()}")
                }
            }
        }
        println("headerJson=${pm.headerJson}")
        println("rawBody=${pm.rawBody?.encodeBase64Url()}")
    }

    private val tintIconMap = HashMap<String, Drawable>()

    fun tintIcon(pm: PushMessage, ic: PushMessageIconColor) =
        tintIconMap.getOrPut("${ic.name}-${pm.loginAcct}") {
            val context = this
            val src = ContextCompat.getDrawable(context, ic.iconId)!!
            DrawableCompat.wrap(src).also { d ->
                val a = acctMap[pm.loginAcct]
                val c = ic.colorRes.notZero()?.let { ContextCompat.getColor(context, it) }
                    ?: a?.notificationAccentColor?.notZero()
                    ?: ContextCompat.getColor(this, R.color.colorOsNotificationAccent)
                DrawableCompat.setTint(d, c)
            }
        }

    @SuppressLint("SetTextI18n")
    private inner class MyViewHolder(
        parent: ViewGroup,
        val views: LvPushMessageBinding =
            LvPushMessageBinding.inflate(layoutInflater, parent, false),
    ) : RecyclerView.ViewHolder(views.root) {

        var lastItem: PushMessage? = null

        init {
            views.root.setOnClickListener { lastItem?.let { itemActions(it) } }
        }

        fun bind(pm: PushMessage?) {
            pm ?: return
            lastItem = pm
            val type = pm.notificationType?.toNotificationType()
            val iconColor = type.pushMessageIconAndColor()

            Glide.with(views.ivSmall)
                .load(pm.iconSmall)
                .error(tintIcon(pm, iconColor))
                .into(views.ivSmall)

            Glide.with(views.ivLarge)
                .load(pm.iconLarge)
                .into(views.ivLarge)

            views.tvText.text = arrayOf(
                "when: ${pm.timestamp.formatLocalTime()}",
                pm.timeDismiss.takeIf { it > 0L }?.let { "既読: ${it.formatLocalTime()}" },
                "to: ${pm.loginAcct}",
                "type: ${pm.notificationType}",
                "id: ${pm.notificationId}",
                "dataSize: ${pm.rawBody?.size}",
                pm.textExpand,
                pm.formatError?.let { "error: $it" },
            ).mapNotNull { it.notBlank() }.joinToString("\n")
        }
    }

    private inner class MyAdapter : RecyclerView.Adapter<MyViewHolder>() {
        var items: List<PushMessage> = emptyList()
            set(value) {
                val oldScrollPos = layoutManager.findFirstVisibleItemPosition()
                    .takeIf { it != RecyclerView.NO_POSITION }
                val oldItems = field
                field = value
                DiffUtil.calculateDiff(
                    object : DiffUtil.Callback() {
                        override fun getOldListSize() = oldItems.size
                        override fun getNewListSize() = value.size

                        override fun areItemsTheSame(
                            oldItemPosition: Int,
                            newItemPosition: Int,
                        ) = oldItems[oldItemPosition] == value[newItemPosition]

                        override fun areContentsTheSame(
                            oldItemPosition: Int,
                            newItemPosition: Int,
                        ) = false
                    },
                    true
                ).dispatchUpdatesTo(this)
                if (oldScrollPos == 0) {
                    launchAndShowError {
                        delay(50L)
                        views.rvMessages.smoothScrollToPosition(0)
                    }
                }
            }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MyViewHolder(parent)

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            holder.bind(items.elementAtOrNull(position))
        }
    }
}
