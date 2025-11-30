package jp.juggler.subwaytooter.util

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.util.ui.resColor

class StColorScheme(
    val materialColorScheme: ColorScheme,
    val colorTextLink: Color,
    val colorTextContent: Color,
    val colorTextError: Color,
    val colorProgressBackground:Color,
    val colorDivider: Color,
)

fun Context.createStColorSchemeLight(): StColorScheme {
    val colorTextContent = Color(resColor(R.color.Light_colorTextContent))
    val colorTextError = Color(resColor(R.color.Light_colorRegexFilterError))
    val colorTextLink = Color(resColor(R.color.Light_colorLink))
    return StColorScheme(
        materialColorScheme = lightColorScheme(
            error = colorTextError,

            background = Color.White,
            onBackground = colorTextContent,

            primary = colorTextLink,
            onPrimary = Color.White,

            secondary = colorTextLink,
            onSecondary = Color.White,

            surface = Color(resColor(R.color.Light_colorReplyBackground)),
            onSurface = colorTextContent,
            onSurfaceVariant = colorTextContent,
            onTertiary = colorTextContent,
        ),
        colorTextLink = colorTextLink,
        colorTextContent = colorTextContent,
        colorTextError = colorTextError,
        colorProgressBackground = Color(0xC0000000),
        colorDivider = Color(resColor(R.color.Light_colorTextDivider)),
    )
}

fun Context.createStColorSchemeDark(): StColorScheme {
    val colorBackground = Color(resColor(R.color.Dark_colorBackground))
    val colorTextContent = Color(resColor(R.color.Dark_colorTextContent))
    val colorTextError = Color(resColor(R.color.Dark_colorRegexFilterError))
    val colorTextLink = Color(resColor(R.color.Dark_colorLink))
    return StColorScheme(
        materialColorScheme = darkColorScheme(
            error = colorTextError,

            background = colorBackground,
            onBackground = colorTextContent,

            primary = colorTextLink,
            onPrimary = colorTextContent,

            secondary = colorTextLink,
            onSecondary = colorTextContent,

            surface = Color(resColor(R.color.Dark_colorColumnHeader)),
            onSurface = colorTextContent,
            onSurfaceVariant = colorTextContent,
            onTertiary = colorTextContent,
        ),
        colorTextLink = colorTextLink,
        colorTextContent = colorTextContent,
        colorTextError = colorTextError,
        colorProgressBackground = Color(0xC0000000),
        colorDivider = Color(resColor(R.color.Dark_colorSettingDivider)),
    )
}

fun Context.createStColorSchemeMastodonDark(): StColorScheme {
    val colorBackground = Color(resColor(R.color.Mastodon_colorBackground))
    val colorTextContent = Color(resColor(R.color.Mastodon_colorTextContent))
    val colorTextError = Color(resColor(R.color.Mastodon_colorRegexFilterError))
    val colorTextLink = Color(resColor(R.color.Mastodon_colorLink))
    return StColorScheme(
        materialColorScheme = darkColorScheme(
            error = colorTextError,

            background = colorBackground,
            onBackground = colorTextContent,

            primary = Color(resColor(R.color.Mastodon_colorAppCompatAccent)),
            onPrimary = colorTextContent,

            secondary = colorTextLink,
            onSecondary = colorTextContent,

            surface = Color(resColor(R.color.Mastodon_colorColumnHeader)),
            onSurface = colorTextContent,
            onSurfaceVariant = colorTextContent,
            onTertiary = colorTextContent,
        ),
        colorTextLink = colorTextLink,
        colorTextContent = colorTextContent,
        colorTextError = colorTextError,
        colorProgressBackground = Color(0xC0000000),
        colorDivider = Color(resColor(R.color.Mastodon_colorSettingDivider)),
    )
}

fun Activity.getStColorTheme(forceDark: Boolean = false): StColorScheme {
    App1.prepare(applicationContext, "getStColorTheme")
    var nTheme = PrefI.ipUiTheme.value
    if (forceDark && nTheme == 0) nTheme = 1
    return when (nTheme) {
        2 -> createStColorSchemeMastodonDark()
        1 -> createStColorSchemeDark()
        else -> createStColorSchemeLight()
    }
}

fun dummyStColorTheme() = StColorScheme(
    materialColorScheme = darkColorScheme(),
    colorTextContent = Color.White,
    colorTextLink = Color(0xff0080ff),
    colorTextError = Color.Red,
    colorProgressBackground = Color(0x80808080),
    colorDivider = Color(0x80808080),
)

fun ComponentActivity.fireBackPressed() =
    onBackPressedDispatcher.onBackPressed()
