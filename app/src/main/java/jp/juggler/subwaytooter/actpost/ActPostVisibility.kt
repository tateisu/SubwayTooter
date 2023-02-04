package jp.juggler.subwaytooter.actpost

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.InstanceCapability
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.getVisibilityCaption
import jp.juggler.subwaytooter.getVisibilityIconId

fun ActPost.showVisibility() {
    val iconId = (states.visibility ?: TootVisibility.Public)
        .getVisibilityIconId(account?.isMisskey == true)
    views.btnVisibility.setImageResource(iconId)
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
        .map { getVisibilityCaption(this, account?.isMisskey == true, it) }
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
