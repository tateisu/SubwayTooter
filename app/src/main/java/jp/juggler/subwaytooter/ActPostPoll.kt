package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.ActPost.Companion.finiteOrZero
import jp.juggler.util.notEmpty
import jp.juggler.util.vg

fun ActPost.showPoll() {
    val i = spEnquete.selectedItemPosition
    llEnquete.vg(i != 0)
    llExpire.vg(i == 1)
    cbHideTotals.vg(i == 1)
    cbMultipleChoice.vg(i == 1)
}

// 投票が有効で何か入力済みなら真
fun ActPost.hasPoll():Boolean{
    if( spEnquete.selectedItemPosition <= 0) return false
    return etChoices.any{ it.text.toString().isNotBlank()}
}

fun ActPost.pollChoiceList() = ArrayList<String>().apply {
    for (et in etChoices) {
        et.text.toString().trim { it <= ' ' }.notEmpty()?.let { add(it) }
    }
}

fun ActPost.pollExpireSeconds(): Int {
    val d = etExpireDays.text.toString().trim().toDoubleOrNull().finiteOrZero()
    val h = etExpireHours.text.toString().trim().toDoubleOrNull().finiteOrZero()
    val m = etExpireMinutes.text.toString().trim().toDoubleOrNull().finiteOrZero()
    return (d * 86400.0 + h * 3600.0 + m * 60.0).toInt()
}

