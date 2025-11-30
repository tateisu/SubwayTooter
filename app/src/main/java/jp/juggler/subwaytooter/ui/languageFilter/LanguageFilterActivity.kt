package jp.juggler.subwaytooter.ui.languageFilter

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.pref.FILE_PROVIDER_AUTHORITY
import jp.juggler.subwaytooter.util.StColorScheme
import jp.juggler.subwaytooter.util.collectOnLifeCycle
import jp.juggler.subwaytooter.util.dummyStColorTheme
import jp.juggler.subwaytooter.util.fireBackPressed
import jp.juggler.subwaytooter.util.getStColorTheme
import jp.juggler.subwaytooter.util.provideViewModel
import jp.juggler.util.backPressed
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.checkMimeTypeAndGrant
import jp.juggler.util.data.intentOpenDocument
import jp.juggler.util.int
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showError
import jp.juggler.util.ui.ActivityResultHandler
import jp.juggler.util.ui.isNotOk
import jp.juggler.util.ui.isOk
import jp.juggler.util.ui.launch
import jp.juggler.util.ui.setContentViewAndInsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LanguageFilterActivity : ComponentActivity() {

    companion object {
        private val log = LogCategory("LanguageFilterActivity")

        fun openLanguageFilterActivity(
            launcher: ActivityResultHandler,
            columnIndex: Int,
        ) {
            Intent(
                launcher.context
                    ?: error("openLanguageFilterActivity: launcher is not registered."),
                LanguageFilterActivity::class.java
            ).apply {
                putExtra(LanguageFilterViewModel.EXTRA_COLUMN_INDEX, columnIndex)
            }.launch(launcher)
        }

        fun decodeResult(r: ActivityResult): Int? =
            when {
                r.isOk -> r.data?.int(LanguageFilterViewModel.EXTRA_COLUMN_INDEX)
                else -> null
            }
    }

    private val viewModel by lazy {
        provideViewModel(this) {
            LanguageFilterViewModel(application)
        }
    }

    private val stColorScheme by lazy {
        getStColorTheme()
    }

    private val arImport = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.checkMimeTypeAndGrant(contentResolver)
            ?.firstOrNull()?.uri?.let {
                viewModel.import2(it)
            }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        viewModel.saveState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        arImport.register(this)
        super.onCreate(savedInstanceState)
        backPressed {
            launchAndShowError {
                if (viewModel.isLanguageListChanged()) {
                    confirm(R.string.language_filter_quit_waring)
                }
                finish()
            }
        }

        viewModel.restoreOrInitialize(
            this,
            savedInstanceState,
            intent,
        )

        // ステータスバーの色にattr色を使っているので、テーマの指定は必要
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        App1.setActivityTheme(this)
        val stColorScheme = getStColorTheme()
        setContent {
            Screen(
                viewModel = viewModel,
                stColorScheme = stColorScheme,
                languageListFlow = viewModel.languageList,
                progressMessageFlow = viewModel.progressMessage,
                saveAction = {
                    setResult(RESULT_OK, viewModel.save())
                    finish()
                },
                getDisplayName = { langDesc(it, viewModel.languageNameMap) },
            )
        }
        collectOnLifeCycle(viewModel.error) {
            it ?: return@collectOnLifeCycle
            showError(it)
        }
    }

    private suspend fun edit(myItem: LanguageFilterItem?) {
        val result = dialogLanguageFilterEdit(
            myItem,
            viewModel.languageNameMap,
            stColorScheme,
        )
        viewModel.handleEditResult(result)
    }

    /**
     * 言語フィルタのエクスポート
     * - 現在のデータをjsonエンコードする
     * - 保存先アプリに渡す
     * 保存自体はすぐ終わるのでプログレス表示やFlow経由の結果受取はない
     */
    suspend fun export() {
        val file = withContext(Dispatchers.IO) {
            viewModel.createExportCacheFile()
        }
        val uri = FileProvider.getUriForFile(
            this@LanguageFilterActivity,
            FILE_PROVIDER_AUTHORITY,
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = contentResolver.getType(uri)
            putExtra(Intent.EXTRA_SUBJECT, "SubwayTooter language filter data")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    @Preview
    @Composable
    fun DefaultPreview() {
        Screen(
            viewModel = LanguageFilterViewModel(Application()),
            stColorScheme = dummyStColorTheme(),
            languageListFlow = MutableStateFlow(
                listOf(
                    LanguageFilterItem(
                        code = TootStatus.LANGUAGE_CODE_DEFAULT,
                        allow = true,
                    ),
                    LanguageFilterItem(
                        code = TootStatus.LANGUAGE_CODE_UNKNOWN,
                        allow = false,
                    ),
                    LanguageFilterItem(
                        code = "xxx",
                        allow = true,
                    ),
                )
            ),
            progressMessageFlow = MutableStateFlow(
                StringResAndArgs(R.string.wait_previous_operation)
            ),
            saveAction = {},
            getDisplayName = { "言語の名前" },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Screen(
        viewModel: LanguageFilterViewModel,
        stColorScheme: StColorScheme,
        languageListFlow: StateFlow<List<LanguageFilterItem>>,
        progressMessageFlow: StateFlow<StringResAndArgs?>,
        saveAction: () -> Unit,
        getDisplayName: (String) -> String,
    ) {
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val progressMessageState = progressMessageFlow.collectAsState()
        MaterialTheme(colorScheme = stColorScheme.materialColorScheme) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    },
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(),
                            title = {
                                Text(stringResource(R.string.language_filter))
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        fireBackPressed()
                                    }
                                ) {
                                    Icon(
                                        // imageVector = AutoMirrored.Outlined.ArrowBack,
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = stringResource(R.string.close)
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                edit(null)
                                            } catch (ex: Throwable) {
                                                showError(ex)
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.Add,
                                        contentDescription = stringResource(R.string.add),
                                    )
                                }
                                IconButton(
                                    onClick = { saveAction() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Save,
                                        contentDescription = stringResource(R.string.close)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                actionsDialog {
                                                    action(getString(R.string.clear_all)) {
                                                        viewModel.clearAllLanguage()
                                                    }
                                                    action(getString(R.string.export)) {
                                                        scope.launch {
                                                            try {
                                                                export()
                                                            } catch (ex: Throwable) {
                                                                showError(ex)
                                                            }
                                                        }
                                                    }
                                                    action(getString(R.string.import_)) {
                                                        arImport.launch(intentOpenDocument("*/*"))
                                                    }
                                                }
                                            } catch (ex: Throwable) {
                                                showError(ex)
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(R.string.more),
                                    )
                                }
                            }
                        )
                    },
                ) { innerPadding ->
                    ScrollContent(
                        scope = scope,
                        innerPadding = innerPadding,
                        languageListFlow = languageListFlow,
                        getDisplayName = getDisplayName,
                        stColorScheme = stColorScheme,
                    )
                    val progressMessage = progressMessageState.value?.let {
                        stringResource(it.stringId, *it.args)
                    }
                    if (progressMessage != null) {
                        ProgressCircleAndText(stColorScheme, progressMessage)
                    }
                }
            }
        }
    }

    @Composable
    fun ProgressCircleAndText(
        stColorScheme: StColorScheme,
        progressMessage: String,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().clickable {
                // 奥の要素をクリックできなくする
            }.background(
                color = stColorScheme.colorProgressBackground,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(160.dp),
            )
            Spacer(modifier = Modifier.size(20.dp))
            Text(progressMessage)
        }
    }

    @Composable
    fun ScrollContent(
        stColorScheme: StColorScheme,
        scope: CoroutineScope,
        innerPadding: PaddingValues,
        languageListFlow: StateFlow<List<LanguageFilterItem>>,
        getDisplayName: (String) -> String,
    ) {
        val stateLanguageList = languageListFlow.collectAsState()
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) {
            items(stateLanguageList.value) {
                LanguageItemCard(
                    it,
                    scope,
                    getDisplayName,
                )
                HorizontalDivider(
                    color = stColorScheme.colorDivider,
                    thickness = 1.dp
                )
            }
        }
    }

    @Composable
    fun LanguageItemCard(
        item: LanguageFilterItem,
        scope: CoroutineScope,
        getDisplayName: (String) -> String,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
//                .requiredHeightIn(min = 48.dp)
                .clickable {
                    scope.launch {
                        try {
                            edit(item)
                        } catch (ex: Throwable) {
                            showError(ex)
                        }
                    }
                },
        ) {
            Text(
                modifier = Modifier
                    .requiredHeightIn(min = 56.dp)
                    .wrapContentHeight()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 6.dp,
                    ),

                text = "${
                    item.code
                } ${
                    getDisplayName(item.code)
                } : ${
                    stringResource(
                        when {
                            item.allow -> R.string.language_show
                            else -> R.string.language_hide
                        }
                    )
                }",
                color = when (item.allow) {
                    true -> MaterialTheme.colorScheme.onBackground
                    false -> MaterialTheme.colorScheme.error
                }
            )
        }
    }
}
