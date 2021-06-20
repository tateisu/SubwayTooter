package jp.juggler.subwaytooter

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.util.LogCategory
import jp.juggler.util.decodeUTF8
import jp.juggler.util.withCaption
import org.apache.commons.io.IOUtils
import java.io.ByteArrayOutputStream
import java.io.InputStream

@RequiresApi(Build.VERSION_CODES.R)
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

        fun readStream(inStream: InputStream?) =
            when (inStream) {
                null -> "(null)"
                else -> try {
                    val bao = ByteArrayOutputStream()
                    IOUtils.copy(inStream, bao)
                    bao.toByteArray().decodeUTF8()
                } catch (ex: Throwable) {
                    ex.withCaption("readStream failed.")
                } finally {
                    inStream.close()
                }
            }
    }

    private lateinit var listView: ListView
    private lateinit var adapter: MyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)
        setContentView(R.layout.act_exit_reasons)
        App1.initEdgeToEdge(this)

        val am = getSystemService(ActivityManager::class.java)
        if (am == null) {
            log.e("can't find ActivityManager")
            finish()
            return
        }

        this.listView = findViewById(R.id.listView)
        this.adapter = MyAdapter()
        adapter.list = am.getHistoricalProcessExitReasons(null, 0, 200)
            .filterNotNull()
            .toList()

        listView.adapter = adapter
    }

    class MyViewHolder(val viewRoot: View) {

        private val textView: TextView = viewRoot.findViewById(R.id.textView)

        fun bind(context: Context, info: ApplicationExitInfo) {

            textView.text = """
				timestamp=${TootStatus.formatTime(context, info.timestamp, bAllowRelative = false)}
				importance=${info.importance}
				pss=${info.pss}
				rss=${info.rss}
				reason=${reasonString(info.reason)}
				status=${info.status}
				description=${info.description}
				trace=${readStream(info.traceInputStream)}
			""".trimIndent()
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
