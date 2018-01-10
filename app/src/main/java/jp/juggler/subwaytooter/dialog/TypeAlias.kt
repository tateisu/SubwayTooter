package jp.juggler.subwaytooter.dialog

import android.app.Dialog

typealias LoginFormCallback = (dialog : Dialog, instance : String, bPseudoAccount : Boolean, bInputAccessToken : Boolean) -> Unit
typealias ReportFormCallback = (dialog : Dialog, comment : String) -> Unit
