package jp.juggler.subwaytooter

// スマホモードならラムダを実行する。タブレットモードならnullを返す
inline fun <R:Any?> ActMain.phoneOnly(code: (PhoneViews) -> R): R? = phoneViews?.let { code(it) }

// タブレットモードならラムダを実行する。スマホモードならnullを返す
inline fun <R:Any?> ActMain.tabOnly(code: (TabletViews) -> R): R? = tabletViews?.let { code(it) }

// スマホモードとタブレットモードでコードを切り替える
inline fun <R:Any?> ActMain.phoneTab(codePhone: (PhoneViews) -> R, codeTablet: (TabletViews) -> R): R {
    phoneViews?.let { return codePhone(it) }
    tabletViews?.let { return codeTablet(it) }
    error("missing phoneViews/tabletViews")
}
