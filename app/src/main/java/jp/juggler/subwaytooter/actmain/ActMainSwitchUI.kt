package jp.juggler.subwaytooter.actmain

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.view.MyViewPager

// スマホモードならラムダを実行する。タブレットモードならnullを返す
inline fun <R : Any?> ActMain.phoneOnly(code: (ActMainPhoneViews) -> R): R? = phoneViews?.let { code(it) }

// タブレットモードならラムダを実行する。スマホモードならnullを返す
inline fun <R : Any?> ActMain.tabOnly(code: (ActMainTabletViews) -> R): R? = tabletViews?.let { code(it) }

// スマホモードとタブレットモードでコードを切り替える
inline fun <R : Any?> ActMain.phoneTab(codePhone: (ActMainPhoneViews) -> R, codeTablet: (ActMainTabletViews) -> R): R {
    phoneViews?.let { return codePhone(it) }
    tabletViews?.let { return codeTablet(it) }
    error("missing phoneViews/tabletViews")
}

fun ActMain.initPhoneTablet() {
    val columnWMin = loadColumnMin()
    val sw = resources.displayMetrics.widthPixels
    val tmpPhonePager: MyViewPager = findViewById(R.id.viewPager)
    val tmpTabletPager: RecyclerView = findViewById(R.id.rvPager)

    // スマホモードとタブレットモードの切り替え
    if (PrefB.bpDisableTabletMode(pref) || sw < columnWMin * 2) {
        tmpTabletPager.visibility = View.GONE
        phoneViews = ActMainPhoneViews(this).apply {
            initUI(tmpPhonePager)
        }
        resizeAutoCW(sw)
    } else {
        tmpPhonePager.visibility = View.GONE
        tabletViews = ActMainTabletViews(this).apply {
            initUI(tmpTabletPager)
        }
    }
}
