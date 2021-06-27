package jp.juggler.subwaytooter

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.api.entity.InstanceCapability
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootVisibility

fun ActPost.showVisibility() {
    val iconId = Styler.getVisibilityIconId(account?.isMisskey == true, states.visibility ?: TootVisibility.Public)
    btnVisibility.setImageResource(iconId)
}

fun ActPost.openVisibilityPicker() {
    val ti = TootInstance.getCached(account)

    val list = when {
        account?.isMisskey == true -> arrayOf(
            //	TootVisibility.WebSetting,
            TootVisibility.Public,
            TootVisibility.UnlistedHome,
            TootVisibility.PrivateFollowers,
            TootVisibility.LocalPublic,
            TootVisibility.LocalHome,
            TootVisibility.LocalFollowers,
            TootVisibility.DirectSpecified,
            TootVisibility.DirectPrivate
        )

        InstanceCapability.visibilityMutual(ti) -> arrayOf(
            TootVisibility.WebSetting,
            TootVisibility.Public,
            TootVisibility.UnlistedHome,
            TootVisibility.PrivateFollowers,
            TootVisibility.Limited,
            TootVisibility.Mutual,
            TootVisibility.DirectSpecified
        )

        InstanceCapability.visibilityLimited(ti) -> arrayOf(
            TootVisibility.WebSetting,
            TootVisibility.Public,
            TootVisibility.UnlistedHome,
            TootVisibility.PrivateFollowers,
            TootVisibility.Limited,
            TootVisibility.DirectSpecified
        )

        else -> arrayOf(
            TootVisibility.WebSetting,
            TootVisibility.Public,
            TootVisibility.UnlistedHome,
            TootVisibility.PrivateFollowers,
            TootVisibility.DirectSpecified
        )
    }
    val captionList = list
        .map { Styler.getVisibilityCaption(this, account?.isMisskey == true, it) }
        .toTypedArray()

    AlertDialog.Builder(this)
        .setTitle(R.string.choose_visibility)
        .setNegativeButton(R.string.cancel, null)
        .setItems(captionList) { _, which ->
            list.elementAtOrNull(which)?.let {
                states.visibility = it
                showVisibility()
            }
        }
        .show()
}
