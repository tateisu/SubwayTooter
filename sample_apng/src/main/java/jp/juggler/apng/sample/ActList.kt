package jp.juggler.apng.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import jp.juggler.apng.ApngFrames
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class ActList : AppCompatActivity(), CoroutineScope {

    companion object {
        const val TAG = "ActList"

        const val PERMISSION_REQUEST_CODE_STORAGE = 1
    }

    class ListItem(val id: Int, val caption: String)

    private lateinit var listView: ListView
    private lateinit var listAdapter: MyAdapter
    private var timeAnimationStart: Long = 0L

    private lateinit var activityJob: Job

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + activityJob

    override fun onCreate(savedInstanceState: Bundle?) {

        activityJob = Job()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_list)
        this.listView = findViewById(R.id.listView)
        listAdapter = MyAdapter()
        listView.adapter = listAdapter
        listView.onItemClickListener = listAdapter
        timeAnimationStart = SystemClock.elapsedRealtime()

        // Assume thisActivity is the current activity
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),

                PERMISSION_REQUEST_CODE_STORAGE
            )
        }

        load()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityJob.cancel()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE_STORAGE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 特に何もしてないらしい
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun load() = launch {
        val list = withContext(Dispatchers.IO) {
            // RawリソースのIDと名前の一覧
            R.raw::class.java.fields
                .mapNotNull { it.get(null) as? Int }
                .map { id ->
                    ListItem(
                        id,
                        resources.getResourceName(id)
                            .replaceFirst(""".+/""".toRegex(), "")
                    )
                }
                .toMutableList()
                .apply { sortBy { it.caption } }
        }

        listAdapter.list.addAll(list)
        listAdapter.notifyDataSetChanged()
    }

    inner class MyAdapter : BaseAdapter(), AdapterView.OnItemClickListener {

        val list = ArrayList<ListItem>()

        override fun getCount(): Int {
            return list.size
        }

        override fun getItem(position: Int): Any {
            return list[position]
        }

        override fun getItemId(position: Int): Long {
            return list[position].id.toLong()
        }

        override fun getView(
            position: Int,
            viewArg: View?,
            parent: ViewGroup?
        ): View {
            val view: View
            val holder: MyViewHolder
            if (viewArg == null) {
                view = layoutInflater.inflate(R.layout.lv_item, parent, false)
                holder = MyViewHolder(view, this@ActList)
                view.tag = holder
            } else {
                view = viewArg
                holder = view.tag as MyViewHolder
            }
            holder.bind(list[position])
            return view
        }

        override fun onItemClick(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            val item = list[position]
            ActViewer.open(this@ActList, item.id, item.caption)
        }

    }

    inner class MyViewHolder(
        viewRoot: View,
        _activity: ActList
    ) {

        private val tvCaption: TextView = viewRoot.findViewById(R.id.tvCaption)
        private val apngView: ApngView = viewRoot.findViewById(R.id.apngView)

        init {
            apngView.timeAnimationStart = _activity.timeAnimationStart
        }

        private var lastId: Int = 0
        private var lastJob: Job? = null

        fun bind(listItem: ListItem) {
            tvCaption.text = listItem.caption

            val resId = listItem.id
            if (lastId != resId) {
                lastId = resId
                apngView.apngFrames?.dispose()
                apngView.apngFrames = null
                launch {
                    var apngFrames: ApngFrames? = null
                    try {
                        lastJob?.cancelAndJoin()

                        val job = async(Dispatchers.IO) {
                            try {
                                ApngFrames.parse(128) { resources?.openRawResource(resId) }
                            } catch (ex: Throwable) {
                                ex.printStackTrace()
                                null
                            }
                        }

                        lastJob = job
                        apngFrames = job.await()

                        if (apngFrames != null && lastId == resId) {
                            apngView.apngFrames = apngFrames
                            apngFrames = null
                        }

                    } catch (ex: Throwable) {
                        ex.printStackTrace()
                        Log.e(TAG, "load error: ${ex.javaClass.simpleName} ${ex.message}")
                    } finally {
                        apngFrames?.dispose()
                    }
                }
            }
        }
    }
}
