package jp.juggler.subwaytooter.ui.ossLicense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.databinding.ActOssLicenseBinding
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.subwaytooter.util.provideViewModel
import jp.juggler.subwaytooter.util.toAnnotatedString
import jp.juggler.util.data.mayUri
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ActOSSLicense : ComponentActivity() {

    companion object {
        private val log = LogCategory("ActOSSLicense")
    }

    private val views by lazy {
        ActOssLicenseBinding.inflate(layoutInflater)
    }
    private val viewModel by lazy {
        provideViewModel(this) {
            ActOSSLicenseViewModel(application)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)
        setContent {
            Screen(
                librariesFlow = viewModel.libraries,
                isProgressShownFlow = viewModel.isProgressShown,
            )
        }

        try {
            viewModel.load()
        } catch (ex: Throwable) {
            log.e(ex, "dependency in fo loading failed.")
        }
    }

    @Preview
    @Composable
    fun DefaultPreview() {
        Screen(
            isProgressShownFlow = MutableStateFlow(false),
            librariesFlow = MutableStateFlow(
                listOf(
                    LibText(
                        nameBig = "nameBig1".toAnnotatedString(),
                        nameSmall = "nameSmall".toAnnotatedString(),
                        desc = "desc".toAnnotatedString(),
                    ),
                    LibText(
                        nameBig = "nameBig2".toAnnotatedString(),
                        nameSmall = "nameSmall".toAnnotatedString(),
                        desc = "desc".toAnnotatedString(),
                    ),
                    LibText(
                        nameBig = "nameBig3".toAnnotatedString(),
                        nameSmall = "nameSmall".toAnnotatedString(),
                        desc = "desc".toAnnotatedString(),
                    ),
                )
            ),
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen(
        librariesFlow: Flow<List<LibText>>,
        isProgressShownFlow: Flow<Boolean>,
    ) {
        val isProgressShown = isProgressShownFlow.collectAsState(false)
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.oss_license))
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { finish() }
                        ) {
                            Icon(
                                // imageVector = AutoMirrored.Outlined.ArrowBack,
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
//                        containerColor = : Color = Color.Unspecified,
//                    scrolledContainerColor: Color = Color.Unspecified,
//                navigationIconContentColor: Color = Color.Unspecified,
//                titleContentColor: Color = Color.Unspecified,
//                actionIconContentColor: Color = Color.Unspecified,

                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
        ) { innerPadding ->
            when (isProgressShown.value) {
                true -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(64.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                else -> ScrollContent(innerPadding, librariesFlow)
            }
        }
    }

    @Composable
    fun ScrollContent(
        innerPadding: PaddingValues,
        librariesFlow: Flow<List<LibText>>,
    ) {
        val libraries = librariesFlow.collectAsState(emptyList())
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),

            ) {
            items(libraries.value) { LibTextCard(it) }
        }
    }

    @Composable
    fun LibTextCard(src: LibText) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 12.dp)
        ) {
            src.nameBig.notEmpty()?.let {
                ClickableText(
                    text = it,
                    style = MaterialTheme.typography.headlineMedium,
                ) { offset ->
                    it.getStringAnnotations(
                        tag = "URL",
                        start = offset,
                        end = offset,
                    ).firstOrNull()?.let { a ->
                        openBrowser(a.item.mayUri())
                    }
                }
            }
            src.nameSmall.notEmpty()?.let {
                ClickableText(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                ) { offset ->
                    it.getStringAnnotations(
                        tag = "URL",
                        start = offset,
                        end = offset,
                    ).firstOrNull()?.let { a ->
                        openBrowser(a.item.mayUri())
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            src.desc.notEmpty()?.let {
                ClickableText(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                ) { offset ->
                    it.getStringAnnotations(
                        tag = "URL",
                        start = offset,
                        end = offset,
                    ).firstOrNull()?.let { a ->
                        openBrowser(a.item.mayUri())
                    }
                }
            }
        }
    }
}
