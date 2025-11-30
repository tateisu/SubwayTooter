package jp.juggler.subwaytooter.ui.ossLicense

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.util.StColorScheme
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.data.eventFlow
import jp.juggler.util.data.setEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActOSSLicenseViewModel(
    application: Application,
) : AndroidViewModel(application) {
    val context: Context
        get() = getApplication<Application>().applicationContext

    private val loadError = Channel<Throwable>(capacity = Channel.CONFLATED)

    private val _libraries = MutableStateFlow<List<LibText>>(emptyList())
    val libraries = _libraries.asStateFlow()

    private val _isProgressShown = MutableStateFlow(false)
    val isProgressShown = _isProgressShown.asStateFlow()

    private val _linkEvent = eventFlow<Uri>()
    val linkEvent = _linkEvent.asStateFlow()

    private fun fireUriEvent(uri:Uri) = _linkEvent.setEvent(uri)

    fun load(stColorScheme: StColorScheme) = viewModelScope.launch {
        try {
            _isProgressShown.value = true
            _libraries.value = withContext(AppDispatchers.IO) {
                val root = context.resources.openRawResource(R.raw.dep_list)
                    .use { it.readBytes() }
                    .decodeToString()
                    .decodeJsonObject()
                val licenses = root.jsonArray("licenses")
                    ?.objectList()
                    ?.associateBy { it.string("shortName") }
                    ?: emptyMap()
                root.jsonArray("libs")
                    ?.objectList()
                    ?.map {
                        parseLibText(
                            it,
                            licenses,
                            stColorScheme = stColorScheme,
                            linkOpener = ::fireUriEvent,
                        )
                    }
                    ?.sortedBy { it.nameSort }
                    ?: emptyList()
            }
        } catch (ex: Throwable) {
            if (ex is CancellationException) return@launch
            loadError.send(ex)
        } finally {
            _isProgressShown.value = false
        }
    }
}
