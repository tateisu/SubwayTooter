package jp.juggler.base

import android.content.Context
import androidx.startup.Initializer
import jp.juggler.util.log.LogCategory

class JugglerBaseInitializer : Initializer<Boolean> {
    companion object {
        private val log = LogCategory("JugglerBaseInitializer")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    override fun create(context: Context): Boolean {
        log.i("create")
        return true
    }
}
