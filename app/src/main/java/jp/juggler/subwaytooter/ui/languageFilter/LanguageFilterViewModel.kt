package jp.juggler.subwaytooter.ui.languageFilter

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.AppState
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.Column
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.data.decodeUTF8
import jp.juggler.util.data.encodeUTF8
import jp.juggler.util.int
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LanguageFilterViewModel(
    application: Application,
) : AndroidViewModel(application) {
    companion object {
        private val log = LogCategory("LanguageFilterViewModel")
        private const val STATE_LANGUAGE_LIST = "language_list"
        const val EXTRA_COLUMN_INDEX = "column_index"
    }

    private var mastodonLanguageJsonCache: String? = null

    private val context: Context
        get() = getApplication()

    lateinit var appState: AppState

    private var columnIndex: Int = 0

    private lateinit var column: Column

    // 編集中の言語リスト
    private val _languageList = MutableStateFlow<List<LanguageFilterItem>>(emptyList())
    val languageList = _languageList.asStateFlow()

    // 言語コードと名前の対応表
    var languageNameMap: Map<String, LanguageInfo> = emptyMap()

    // エラーイベント
    private val _error = Channel<Throwable?>(capacity = Channel.CONFLATED)
    val error = _error.receiveAsFlow()

    // ロード中表示の文字列
    private val _progressMessage = MutableStateFlow<StringResAndArgs?>(null)
    val progressMessage = _progressMessage.asStateFlow()

    fun saveState(outState: Bundle) {
        outState.putString(STATE_LANGUAGE_LIST, encodeLanguageList().toString())
    }

    fun restoreOrInitialize(
        activityContext: Context,
        savedInstanceState: Bundle?,
        intent: Intent?,
    ) {
        appState = App1.getAppState(context)
        columnIndex = intent?.int(EXTRA_COLUMN_INDEX) ?: 0
        column = appState.column(columnIndex) ?: error("missing column[$columnIndex]")
        viewModelScope.launch {
            try {
                if (mastodonLanguageJsonCache == null) {
                    _progressMessage.value = StringResAndArgs(R.string.language_filter_loading)
                    mastodonLanguageJsonCache = try {
                        val accessInfo = column.accessInfo
                        if (accessInfo.isNA) {
                            "na"
                        } else if (accessInfo.isPseudo) {
                            loadMastodonLanguages(accessInfo.apiHost)
                        } else {
                            loadMastodonLanguages(accessInfo.apiHost, accessInfo.bearerAccessToken)
                        }
                    } catch (ex: Throwable) {
                        "error"
                    }
                }
                // 端末のconfiguration changeがありうるので、言語マップはActivityのonCreateのたびに再取得する
                languageNameMap = withContext(Dispatchers.IO) {
                    getLanguageNames(context, mastodonLanguageJsonCache).apply {
                        val specDefault = LanguageInfo(
                            code = TootStatus.LANGUAGE_CODE_DEFAULT,
                            name = TootStatus.LANGUAGE_CODE_DEFAULT,
                            displayName = activityContext.getString(R.string.language_code_default),
                        )
                        val specUnknown = LanguageInfo(
                            code = TootStatus.LANGUAGE_CODE_UNKNOWN,
                            name = TootStatus.LANGUAGE_CODE_UNKNOWN,
                            displayName = activityContext.getString(R.string.language_code_unknown)
                        )
                        put(specDefault.code, specDefault)
                        put(specUnknown.code, specUnknown)
                    }
                }

                // 状態の復元
                try {
                    savedInstanceState?.getString(STATE_LANGUAGE_LIST, null)
                        ?.decodeJsonObject()
                        ?.let { load(it) }
                } catch (ex: Throwable) {
                    log.e(ex, "restore failed.")
                }
                // 未初期化なら初期データのロード
                if (languageList.value.isEmpty()) {
                    load(column.languageFilter)
                }
            } catch (ex: Throwable) {
                _error.send(ex)
            } finally {
                _progressMessage.value = null
            }
        }
    }

    private fun load(src: JsonObject?) {
        _languageList.value = buildList<LanguageFilterItem> {
            if (src != null) {
                for (key in src.keys) {
                    add(LanguageFilterItem(key, src.boolean(key) ?: true))
                }
            }
            if (none { it.code == TootStatus.LANGUAGE_CODE_DEFAULT }) {
                add(LanguageFilterItem(TootStatus.LANGUAGE_CODE_DEFAULT, true))
            }
            log.i("load: list size=$size")
        }.sortedWith(languageFilterItemComparator)
    }

    fun createExportCacheFile(): File {
        val bytes = JsonObject().apply {
            for (item in _languageList.value) {
                put(item.code, item.allow)
            }
        }.toString().encodeUTF8()

        val cacheDir = context.cacheDir
        cacheDir.mkdirs()
        val file = File(
            cacheDir,
            "SubwayTooter-language-filter.${Process.myPid()}.${Process.myTid()}.json"
        )
        FileOutputStream(file).use {
            it.write(bytes)
        }
        return file
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun import2(uri: Uri) {
        viewModelScope.launch {
            try {
                _progressMessage.value = StringResAndArgs(R.string.language_filter_importing)
                val jsonObject = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    log.i("import2 type=${resolver.getType(uri)}")
                    (resolver.openInputStream(uri) ?: error("openInputStream returns null"))
                        .use {
                            it.readBytes().decodeUTF8().decodeJsonObject()
                        }
                }
                load(jsonObject)
            } catch (ex: Throwable) {
                log.e(ex, "import2 failed.")
                _error.send(ex)
            } finally {
                _progressMessage.value = null
            }
        }
    }

    fun save(): Intent {
        column.languageFilter = encodeLanguageList()
        return Intent().apply {
            putExtra(EXTRA_COLUMN_INDEX, columnIndex)
        }
    }

    // UIのデータをJsonObjectにエンコード
    private fun encodeLanguageList() = buildJsonObject {
        for (item in languageList.value) {
            put(item.code, item.allow)
        }
    }

    fun isLanguageListChanged(): Boolean {
        fun JsonObject.encodeSorted() = toString().decodeJsonObject().run {
            if (!contains(TootStatus.LANGUAGE_CODE_DEFAULT)) {
                put(TootStatus.LANGUAGE_CODE_DEFAULT, true)
            }
            entries.sortedBy { it.key }
                .joinToString(",") { "${it.key}=${it.value}" }
        }

        val current = (encodeLanguageList()).encodeSorted()
        val initial = (column.languageFilter ?: JsonObject()).encodeSorted()
        return current != initial
    }

    fun handleEditResult(result: LanguageFilterEditResult) {
        _languageList.value = when (result) {
            is LanguageFilterEditResult.Delete ->
                _languageList.value.filter { it.code != result.code }

            is LanguageFilterEditResult.Update -> when (
                val item = languageList.value.find { it.code == result.code }
            ) {
                null -> (_languageList.value + listOf(
                    LanguageFilterItem(
                        result.code,
                        result.allow
                    )
                ))
                    .sortedWith(languageFilterItemComparator)

                else -> {
                    item.allow = result.allow
                    _languageList.value
                }
            }
        }
    }

    // 言語フィルタを初期状態に戻す
    fun clearAllLanguage() {
        viewModelScope.launch {
            try {
                _languageList.value = listOf(
                    LanguageFilterItem(TootStatus.LANGUAGE_CODE_DEFAULT, true)
                )
            } catch (ex: Throwable) {
                log.e(ex, "clearAllLanguage failed.")
                _error.send(ex)
            } finally {
                _progressMessage.value = null
            }
        }
    }
}
