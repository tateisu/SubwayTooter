package jp.juggler.base

import android.annotation.SuppressLint
import android.content.Context
import androidx.startup.AppInitializer
import androidx.startup.Initializer
import jp.juggler.util.log.LogCategory

/**
 * AndroidManifest.xml の指定により、
 * ApplicationのonCreate()より前に実行される。
 */
class JugglerBaseInitializer : Initializer<JugglerBase> {
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
    override fun create(context: Context) =
        JugglerBase(context.applicationContext)
}

/**
 *
 */
class JugglerBase(
    var context: Context,
) {
    companion object {
        private val log = LogCategory("JugglerBase")

        /**
         * 最後に作成したインスタンス
         */
        @SuppressLint("StaticFieldLeak")
        var jugglerBaseNullable: JugglerBase? = null

        val jugglerBase get() = jugglerBaseNullable!!

        /**
         * JugglerBaseのインスタンスを androidx.startup.AppInitializer から取得する
         * 遅延初期化を行う場合、Contextが必要になる
         */
        val Context.prepareJugglerBase: JugglerBase
            get() = jugglerBaseNullable
                ?: AppInitializer.getInstance(applicationContext)
                    .initializeComponent(JugglerBaseInitializer::class.java)
    }

    init {
        jugglerBaseNullable = this
        log.i("ctor")
    }
}
