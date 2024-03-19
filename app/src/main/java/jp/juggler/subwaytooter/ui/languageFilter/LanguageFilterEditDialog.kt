package jp.juggler.subwaytooter.ui.languageFilter

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.graphics.toArgb
import androidx.core.widget.addTextChangedListener
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.databinding.DlgLanguageFilterBinding
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.util.StColorScheme
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.isEnabledAlpha
import jp.juggler.util.ui.setEnabledColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

sealed interface LanguageFilterEditResult {
    class Update(val code: String, val allow: Boolean) : LanguageFilterEditResult
    class Delete(val code: String) : LanguageFilterEditResult
}

/**
 * 言語コード1つを追加/編集/削除するダイアログ
 */
suspend fun ComponentActivity.dialogLanguageFilterEdit(
    // 既存項目の編集時は非null
    item: LanguageFilterItem?,
    // 言語コード→表示名のマップ
    nameMap: Map<String, LanguageInfo>,
    // 色スキーマ
    stColorScheme: StColorScheme,
): LanguageFilterEditResult = suspendCancellableCoroutine { cont ->
    val views = DlgLanguageFilterBinding.inflate(layoutInflater, null, false)

    views.apply {
        fun updateDesc() {
            val code = etLanguage.text.toString().trim()
            tvLanguage.text = nameMap[code]?.displayName ?: getString(R.string.custom)
        }
        when (item?.allow ?: true) {
            true -> rbShow.isChecked = true
            else -> rbHide.isChecked = true
        }
        btnPresets.setOnClickListener {
            launchAndShowError {
                actionsDialog(getString(R.string.presets)) {
                    val languageList = nameMap.map {
                        LanguageFilterItem(it.key, true)
                    }.sortedWith(languageFilterItemComparator)
                    for (a in languageList) {
                        action("${a.code} ${langDesc(a.code, nameMap)}") {
                            etLanguage.setText(a.code)
                            updateDesc()
                        }
                    }
                }
            }
        }
        etLanguage.addTextChangedListener { updateDesc() }
        etLanguage.setText(item?.code ?: "")
        updateDesc()
        // 編集時は言語コードを変更できない
        etLanguage.isEnabledAlpha = item == null
        btnPresets.setEnabledColor(
            btnPresets.context,
            R.drawable.ic_edit,
            stColorScheme.colorTextContent.toArgb(),
            item == null
        )
        fun getCode() = etLanguage.text.toString().trim()
        fun isAllow() = rbShow.isChecked
        AlertDialog.Builder(this@dialogLanguageFilterEdit).apply {
            setView(views.root)
            setCancelable(true)
            setNegativeButton(R.string.cancel, null)
            setPositiveButton(R.string.ok) { _, _ ->
                if (cont.isActive) cont.resume(
                    LanguageFilterEditResult.Update(getCode(), isAllow())
                ) {}
            }
            if (item != null && item.code != TootStatus.LANGUAGE_CODE_DEFAULT) {
                setNeutralButton(R.string.delete) { _, _ ->
                    if (cont.isActive) cont.resume(
                        LanguageFilterEditResult.Delete(item.code)
                    ) {}
                }
            }
        }.create().also { dialog ->
            dialog.setOnDismissListener {
                if (cont.isActive) cont.resumeWithException(CancellationException())
            }
            cont.invokeOnCancellation { dialog.dismissSafe() }
            dialog.show()
        }
    }
}
