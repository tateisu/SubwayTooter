package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.DatePicker
import android.widget.TimePicker
import jp.juggler.subwaytooter.R
import jp.juggler.util.dismissSafe
import java.util.*

class DlgDateTime(
    val activity: Activity,
) : DatePicker.OnDateChangedListener, View.OnClickListener {

    private lateinit var datePicker: DatePicker
    private lateinit var timePicker: TimePicker
    private lateinit var btnCancel: Button
    private lateinit var btnOk: Button
    private lateinit var dialog: Dialog

    private lateinit var callback: (Long) -> Unit

    @SuppressLint("InflateParams")
    fun open(initialValue: Long, callback: (Long) -> Unit) {
        this.callback = callback

        val view = activity.layoutInflater.inflate(R.layout.dlg_date_time, null, false)

        datePicker = view.findViewById(R.id.datePicker)
        timePicker = view.findViewById(R.id.timePicker)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnOk = view.findViewById(R.id.btnOk)

        val c = GregorianCalendar.getInstance(TimeZone.getDefault())
        c.timeInMillis = when (initialValue) {
            0L -> System.currentTimeMillis() + 10 * 60000L
            else -> initialValue
        }
        datePicker.firstDayOfWeek = Calendar.MONDAY
        datePicker.init(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH),
            this
        )

        timePicker.hour = c.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = c.get(Calendar.MINUTE)

        timePicker.setIs24HourView(
            when (Settings.System.getString(activity.contentResolver, Settings.System.TIME_12_24)) {
                "12" -> false
                else -> true
            }
        )

        btnCancel.setOnClickListener(this)
        btnOk.setOnClickListener(this)

        dialog = Dialog(activity)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnCancel -> dialog.cancel()

            R.id.btnOk -> {
                dialog.dismissSafe()
                callback(getTime())
            }
        }
    }

    override fun onDateChanged(
        view: DatePicker,
        year: Int,
        monthOfYear: Int,
        dayOfMonth: Int,
    ) {
        // nothing to do
    }

    private fun getTime(): Long {
        val c = GregorianCalendar.getInstance(TimeZone.getDefault())
        c.set(
            datePicker.year,
            datePicker.month,
            datePicker.dayOfMonth,
            timePicker.hour,
            timePicker.minute,
            0,
        )
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
