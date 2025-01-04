package jp.juggler.subwaytooter

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.databinding.ActExitReasonsBinding
import jp.juggler.subwaytooter.view.wrapTitleTextView
import jp.juggler.util.data.decodeUTF8
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.withCaption
import jp.juggler.util.ui.setContentViewAndInsets
import jp.juggler.util.ui.setNavigationBack

class ActExitReasons : AppCompatActivity() {

    companion object {

        val log = LogCategory("ActExitReasons")

        fun reasonString(v: Int) = when (v) {
            ApplicationExitInfo.REASON_ANR ->
                "REASON_ANR Application process was killed due to being unresponsive (ANR)."

            ApplicationExitInfo.REASON_CRASH ->
                "REASON_CRASH Application process died because of an unhandled exception in Java code."

            ApplicationExitInfo.REASON_CRASH_NATIVE ->
                "REASON_CRASH_NATIVE Application process died because of a native code crash."

            ApplicationExitInfo.REASON_DEPENDENCY_DIED ->
                "REASON_DEPENDENCY_DIED Application process was killed because its dependency was going away, for example, a stable content provider connection's client will be killed if the provider is killed."

            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ->
                "REASON_EXCESSIVE_RESOURCE_USAGE Application process was killed by the system due to excessive resource usage."

            ApplicationExitInfo.REASON_EXIT_SELF ->
                "REASON_EXIT_SELF Application process exit normally by itself, for example, via java.lang.System#exit; getStatus will specify the exit code."

            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ->
                "REASON_INITIALIZATION_FAILURE Application process was killed because of initialization failure, for example, it took too long to attach to the system during the start, or there was an error during initialization."

            ApplicationExitInfo.REASON_LOW_MEMORY ->
                "REASON_LOW_MEMORY Application process was killed by the system low memory killer, meaning the system was under memory pressure at the time of kill."

            ApplicationExitInfo.REASON_OTHER ->
                "REASON_OTHER Application process was killed by the system for various other reasons which are not by problems in apps and not actionable by apps, for example, the system just finished updates; getDescription will specify the cause given by the system."

            ApplicationExitInfo.REASON_PERMISSION_CHANGE ->
                "REASON_PERMISSION_CHANGE Application process was killed due to a runtime permission change."

            ApplicationExitInfo.REASON_SIGNALED ->
                "REASON_SIGNALED Application process died due to the result of an OS signal; for example, android.system.OsConstants#SIGKILL; getStatus will specify the signal number."

            ApplicationExitInfo.REASON_UNKNOWN ->
                "REASON_UNKNOWN Application process died due to unknown reason."

            ApplicationExitInfo.REASON_USER_REQUESTED ->
                "REASON_USER_REQUESTED Application process was killed because of the user request, for example, user clicked the \"Force stop\" button of the application in the Settings, or removed the application away from Recents."

            ApplicationExitInfo.REASON_USER_STOPPED ->
                "REASON_USER_STOPPED Application process was killed, because the user it is running as on devices with mutlple users, was stopped."

            else -> "?($v)"
        }
    }

    private val views by lazy {
        ActExitReasonsBinding.inflate(layoutInflater)
    }

    private lateinit var adapter: MyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)
        setContentViewAndInsets(views.root)
        setSupportActionBar(views.toolbar)
        wrapTitleTextView()
        setNavigationBack(views.toolbar)
        fixHorizontalPadding(views.listView)

        val am = getSystemService(ActivityManager::class.java)
        if (am == null) {
            log.e("can't find ActivityManager")
            finish()
            return
        }

        this.adapter = MyAdapter()
        adapter.list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            am.getHistoricalProcessExitReasons(null, 0, 200)
                .filterNotNull()
                .toList()
        } else {
            emptyList()
        }

        views.listView.adapter = adapter
    }

    class MyViewHolder(val viewRoot: View) {

        private val textView: TextView = viewRoot.findViewById(R.id.textView)

        fun bind(context: Context, info: ApplicationExitInfo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val trace = try {
                    info.traceInputStream?.use {
                        it.readBytes().decodeUTF8()
                    } ?: "(null)"
                } catch (ex: Throwable) {
                    ex.withCaption("can't read traceInputStream")
                }

                textView.text = """
                timestamp=${TootStatus.formatTime(context, info.timestamp, bAllowRelative = false)}
                importance=${info.importance}
                pss=${info.pss}
                rss=${info.rss}
                reason=${reasonString(info.reason)}
                status=${info.status}
                description=${info.description}
                trace=$trace
                """.trimIndent()
            }
        }
    }

    fun genView(parent: ViewGroup?): View =
        layoutInflater.inflate(R.layout.lv_exit_reason, parent, false)
            .also { it.tag = MyViewHolder(it) }

    inner class MyAdapter : BaseAdapter() {

        var list: List<ApplicationExitInfo> = emptyList()

        override fun getCount(): Int = list.size
        override fun getItem(position: Int): Any = list[position]
        override fun getItemId(position: Int): Long = 0
        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup?,
        ): View =
            (convertView ?: genView(parent)).also { view ->
                (view.tag as? MyViewHolder)?.bind(this@ActExitReasons, list[position])
            }
    }
}
