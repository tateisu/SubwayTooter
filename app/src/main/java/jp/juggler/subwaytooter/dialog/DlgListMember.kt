package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.*
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.calcIconRound
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.accountListNonPseudo
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.view.MyListView
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.*
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.*
import jp.juggler.util.log.*
import jp.juggler.util.network.toPostRequestBuilder
import jp.juggler.util.ui.*
import org.jetbrains.anko.textColor
import java.util.*

@SuppressLint("InflateParams")
class DlgListMember(
    private val activity: ActMain,
    who: TootAccount,
    listOwnerArg: SavedAccount,
) : View.OnClickListener {

    private val dialog: Dialog

    private val btnListOwner: Button
    private val btnCreateList: Button

    private val accountList: MutableList<SavedAccount>
    private val targetUserFullAcct: Acct

    private var listOwner: SavedAccount? = null
    private var whoLocal: TootAccount? = null

    private val adapter: MyListAdapter

    init {
        this.accountList = accountListNonPseudo(null)
        this.targetUserFullAcct = listOwnerArg.getFullAcct(who)

        this.listOwner = if (listOwnerArg.isPseudo) {
            null
        } else {
            listOwnerArg
        }

        val view = activity.layoutInflater.inflate(R.layout.dlg_list_member, null, false)

        val ivUser = view.findViewById<MyNetworkImageView>(R.id.ivUser)
        val tvUserName = view.findViewById<TextView>(R.id.tvUserName)
        val tvUserAcct = view.findViewById<TextView>(R.id.tvUserAcct)
        btnListOwner = view.findViewById(R.id.btnListOwner)
        btnCreateList = view.findViewById(R.id.btnCreateList)
        val listView = view.findViewById<MyListView>(R.id.listView)

        this.adapter = MyListAdapter()
        listView.adapter = adapter

        btnCreateList.setOnClickListener(this)
        btnListOwner.setOnClickListener(this)
        view.findViewById<View>(R.id.btnClose).setOnClickListener(this)

        ivUser.setImageUrl(
            calcIconRound(ivUser.layoutParams),
            who.avatar_static,
            who.avatar
        )
        val userNameInvalidator = NetworkEmojiInvalidator(activity.handler, tvUserName)
        val name = who.decodeDisplayName(activity)
        tvUserName.text = name
        userNameInvalidator.register(name)
        tvUserAcct.text = targetUserFullAcct.pretty

        setListOwner(listOwner)

        dialog = Dialog(activity).apply {
            window?.apply {
                setFlags(0, Window.FEATURE_NO_TITLE)
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            }
            setTitle(R.string.your_lists)
            setContentView(view)
        }
    }

    fun show() = dialog.apply {
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        show()
    }

    override fun onClick(v: View) {
        when (v.id) {

            R.id.btnClose -> dialog.dismissSafe()

            R.id.btnListOwner ->
                launchMain {
                    activity.pickAccount(
                        bAllowPseudo = false,
                        bAuto = false,
                        accountListArg = accountList
                    )?.let { setListOwner(it) }
                }

            R.id.btnCreateList -> openListCreator()
        }
    }

    // リストオーナボタンの文字列を更新する
    // リスト一覧を取得する
    private fun setListOwner(a: SavedAccount?) {
        this.listOwner = a
        if (a == null) {
            btnListOwner.setText(R.string.not_selected)
            btnListOwner.setTextColor(activity.attrColor(android.R.attr.textColorPrimary))
            btnListOwner.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
            //
        } else {
            val ac = daoAcctColor.load(a)
            btnListOwner.text = ac.nickname

            if (daoAcctColor.hasColorBackground(ac)) {
                btnListOwner.setBackgroundColor(ac.colorBg)
            } else {
                btnListOwner.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
            }
            btnListOwner.textColor = ac.colorFg.notZero()
                ?: activity.attrColor(android.R.attr.textColorPrimary)
        }

        loadLists()
    }

    // リストの一覧とターゲットユーザの登録状況を取得する
    private fun loadLists() {
        val listOwner = this.listOwner

        if (listOwner == null) {
            showList(null)
            return
        }

        launchMain {
            var resultList: ArrayList<TootList>? = null
            val result = activity.runApiTask(listOwner) { client ->

                // 現在の登録状況を知るため、対象ユーザの自タンスでのアカウントIDを取得する
                // ドメインブロックなどの影響で同期できない場合があるが、
                // 一覧そのものは取得できるのでこの段階ではエラーにはしない
                val (r1, ar) = client.syncAccountByAcct(listOwner, targetUserFullAcct)
                r1 ?: return@runApiTask null // cancelled.

                val whoLocal = ar?.get() // may null
                if (whoLocal == null) activity.showToast(true, r1.error)

                this@DlgListMember.whoLocal = whoLocal

                if (listOwner.isMisskey) {
                    // 今のmisskeyではリスト全スキャンしないとユーザの登録状況が分からない
                    client.request(
                        "/api/users/lists/list",
                        listOwner
                            .putMisskeyApiToken()
                            .toPostRequestBuilder()
                    )?.also { result ->
                        resultList = parseList(result.jsonArray) {
                            TootList(
                                TootParser(activity, listOwner),
                                it
                            )
                        }.apply {
                            if (whoLocal != null) {
                                forEach { list ->
                                    list.isRegistered =
                                        list.userIds?.any { it == whoLocal.id } ?: false
                                }
                            }
                        }
                    }
                } else {
                    val registeredSet = HashSet<EntityId>()

                    // メンバーを指定してリスト登録状況を取得
                    if (whoLocal != null) client.request(
                        "/api/v1/accounts/${whoLocal.id}/lists"
                    )?.also { result ->
                        parseList(result.jsonArray) {
                            TootList(
                                TootParser(activity, listOwner),
                                it
                            )
                        }.forEach {
                            registeredSet.add(it.id)
                        }
                    }

                    // リスト一覧を取得
                    client.request("/api/v1/lists")?.also { result ->
                        resultList = parseList(result.jsonArray) {
                            TootList(
                                TootParser(activity, listOwner),
                                it
                            )
                        }.apply {
                            sort()
                            forEach {
                                it.isRegistered = registeredSet.contains(it.id)
                            }
                        }
                    }
                }
            }
            result?.error?.let { activity.showToast(true, it) }
            showList(resultList)
        }
    }

    private fun showList(list: ArrayList<TootList>?) {
        btnCreateList.isEnabledAlpha = list != null
        adapter.itemList.clear()
        when {
            list == null -> adapter.itemList.add(ErrorItem(activity.getString(R.string.cant_access_list)))
            list.isEmpty() -> adapter.itemList.add(ErrorItem(activity.getString(R.string.list_not_created)))
            else -> adapter.itemList.addAll(list)
        }
        adapter.notifyDataSetChanged()
    }

    private fun openListCreator() {
        DlgTextInput.show(
            activity,
            activity.getString(R.string.list_create),
            null,
            callback = object : DlgTextInput.Callback {

                override fun onEmptyError() {
                    activity.showToast(false, R.string.list_name_empty)
                }

                override fun onOK(dialog: Dialog, text: String) {
                    val list_owner = this@DlgListMember.listOwner

                    if (list_owner == null) {
                        activity.showToast(false, "list owner is not selected.")
                        return
                    }

                    activity.listCreate(list_owner, text) {
                        dialog.dismissSafe()
                        loadLists()
                    }
                }
            })
    }

    internal class ErrorItem(val message: String)

    private inner class MyListAdapter : BaseAdapter() {

        val itemList = ArrayList<Any>()

        override fun getCount(): Int = itemList.size

        override fun getItem(position: Int): Any? =
            if (position >= 0 && position < itemList.size) itemList[position] else null

        override fun getItemId(position: Int): Long =
            0L

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int =
            when (getItem(position)) {
                is TootList -> 0
                else -> 1
            }

        override fun getView(position: Int, viewOld: View?, parent: ViewGroup): View =
            when (val o = getItem(position)) {
                is TootList ->
                    if (viewOld != null) {
                        viewOld.apply { (tag as VH_List).bind(o) }
                    } else {
                        val view: View = activity.layoutInflater.inflate(
                            R.layout.lv_list_member_list,
                            parent,
                            false
                        )
                        view.apply { tag = VH_List(this).bind(o) }
                    }

                is ErrorItem ->
                    if (viewOld != null) {
                        viewOld.apply { (tag as VH_Error).bind(o) }
                    } else {
                        val view: View = activity.layoutInflater.inflate(
                            R.layout.lv_list_member_error,
                            parent,
                            false
                        )
                        view.apply { tag = VH_Error(view).bind(o) }
                    }

                else -> activity.layoutInflater.inflate(
                    R.layout.lv_list_member_error,
                    parent,
                    false
                )
            }
    }

    @Suppress("ClassNaming")
    internal inner class VH_List(view: View) : CompoundButton.OnCheckedChangeListener {

        private val cbItem: CheckBox
        private var bBusy: Boolean = false
        var item: TootList? = null

        init {
            this.cbItem = view.findViewById(R.id.cbItem)
            cbItem.setOnCheckedChangeListener(this)
        }

        fun bind(item: TootList) = this.apply {
            bBusy = true

            this.item = item
            cbItem.text = item.title
            cbItem.isChecked = item.isRegistered

            bBusy = false
        }

        override fun onCheckedChanged(view: CompoundButton, isChecked: Boolean) {
            // ユーザ操作以外で変更されたなら何もしない
            if (bBusy) return

            val listOwner = this@DlgListMember.listOwner
            if (listOwner == null) {
                activity.showToast(false, "list owner is not selected")
                revokeCheckedChanged(isChecked)
                return
            }

            val whoLocal = this@DlgListMember.whoLocal
            if (whoLocal == null) {
                activity.showToast(false, "target user is not synchronized")
                revokeCheckedChanged(isChecked)
                return
            }

            // 状態をサーバに伝える
            val item = this.item ?: return
            if (isChecked) {
                activity.listMemberAdd(listOwner, item.id, whoLocal) { willRegistered, bSuccess ->
                    if (!bSuccess) revokeCheckedChanged(willRegistered)
                }
            } else {
                activity.listMemberDelete(
                    listOwner,
                    item.id,
                    whoLocal
                ) { willRegistered, bSuccess ->
                    if (!bSuccess) revokeCheckedChanged(willRegistered)
                }
            }
        }

        private fun revokeCheckedChanged(willRegistered: Boolean) {
            item?.isRegistered = !willRegistered
            adapter.notifyDataSetChanged()
        }
    }

    @Suppress("ClassNaming")
    internal inner class VH_Error(view: View) {
        private val tvError: TextView = view.findViewById(R.id.tvError)

        fun bind(o: ErrorItem) = this.apply {
            tvError.text = o.message
        }
    }
}
