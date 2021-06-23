package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.view.MyViewPager

class PhoneViews(val actMain: ActMain) {
    internal lateinit var pager: MyViewPager
    internal lateinit var pagerAdapter: ColumnPagerAdapter

    fun initUI(viewPager: MyViewPager) {
        this.pager = viewPager
        this.pagerAdapter = ColumnPagerAdapter(actMain)
        this.pager.adapter = this.pagerAdapter
        this.pager.addOnPageChangeListener(actMain)
    }
}
