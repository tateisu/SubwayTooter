package jp.juggler.subwaytooter.mfm

import android.text.SpannableStringBuilder
import jp.juggler.subwaytooter.api.entity.TootMention
import java.util.ArrayList

// デコード結果にはメンションの配列を含む。TootStatusのパーサがこれを回収する。
class SpannableStringBuilderEx(
    var mentions: ArrayList<TootMention>? = null,
) : SpannableStringBuilder()
