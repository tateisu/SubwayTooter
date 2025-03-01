package jp.juggler.subwaytooter.actmain

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.view.MyViewPager
import jp.juggler.util.data.notZero
import jp.juggler.util.ui.attrColor
import org.jetbrains.anko.backgroundColor

class ActMainPhoneViews(private val actMain: ActMain) {
    internal lateinit var pager: MyViewPager
    internal lateinit var pagerAdapter: ColumnPagerAdapter

    fun initUI(viewPager: MyViewPager) {
        this.pager = viewPager
        this.pagerAdapter = ColumnPagerAdapter(actMain)
        this.pager.adapter = this.pagerAdapter
        this.pager.addOnPageChangeListener(actMain)
        viewPager.backgroundColor = PrefI.ipCcdContentBg.value
            .notZero() ?: viewPager.context.attrColor(R.attr.colorMainBackground)
    }
}
