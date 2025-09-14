/*
 * Copyright (C) 2017 JRummy Apps Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jrummyapps.android.colorpicker

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.widget.addTextChangedListener
import com.jrummyapps.android.colorpicker.databinding.CpvDialogColorPickerBinding
import com.jrummyapps.android.colorpicker.databinding.CpvDialogPresetsBinding
import jp.juggler.util.coroutine.resumeWithCancellationException
import jp.juggler.util.log.LogCategory
import jp.juggler.util.resumeCompat
import jp.juggler.util.systemService
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.gone
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.math.roundToInt

private val log = LogCategory("ColorPickerDialog")

enum class ColorPickerDialogType { Custom, Presets, }

internal const val ALPHA_THRESHOLD = 165

/**
 * Material design colors used as the default color presets
 */
val MATERIAL_COLORS = intArrayOf(
    -0xbbcca,  // RED 500
    -0x16e19d,  // PINK 500
    -0xd36d,  // LIGHT PINK 500
    -0x63d850,  // PURPLE 500
    -0x98c549,  // DEEP PURPLE 500
    -0xc0ae4b,  // INDIGO 500
    -0xde690d,  // BLUE 500
    -0xfc560c,  // LIGHT BLUE 500
    -0xff432c,  // CYAN 500
    -0xff6978,  // TEAL 500
    -0xb350b0,  // GREEN 500
    -0x743cb6,  // LIGHT GREEN 500
    -0x3223c7,  // LIME 500
    -0x14c5,  // YELLOW 500
    -0x3ef9,  // AMBER 500
    -0x6800,  // ORANGE 500
    -0x86aab8,  // BROWN 500
    -0x9f8275,  // BLUE GREY 500
    -0x616162
)

internal fun unshiftIfNotExists(array: IntArray, value: Int): IntArray {
    if (array.any { it == value }) return array
    val newArray = IntArray(array.size + 1)
    newArray[0] = value
    System.arraycopy(array, 0, newArray, 1, newArray.size - 1)
    return newArray
}

internal fun pushIfNotExists(array: IntArray, value: Int): IntArray {
    if (array.any { it == value }) {
        return array
    }
    val newArray = IntArray(array.size + 1)
    newArray[newArray.size - 1] = value
    System.arraycopy(array, 0, newArray, 0, newArray.size - 1)
    return newArray
}

internal fun shadeColor(@ColorInt color: Int, percent: Double): Int {
    val hex = "#%06X".format(color and 0xFFFFFF)
    val f = hex.substring(1).toLong(16)
    val t = (if (percent < 0) 0 else 255).toDouble()
    val p = if (percent < 0) percent * -1 else percent
    val cR = f shr 16
    val cG = f shr 8 and 0x00FF
    val cB = f and 0x0000FF
    return Color.argb(
        Color.alpha(color),
        ((t - cR) * p).roundToInt() + cR.toInt(),
        ((t - cG) * p).roundToInt() + cG.toInt(),
        ((t - cB) * p).roundToInt() + cB.toInt(),
    )
}

internal fun getColorShades(@ColorInt color: Int) = intArrayOf(
    shadeColor(color, 0.9),
    shadeColor(color, 0.7),
    shadeColor(color, 0.5),
    shadeColor(color, 0.333),
    shadeColor(color, 0.166),
    shadeColor(color, -0.125),
    shadeColor(color, -0.25),
    shadeColor(color, -0.375),
    shadeColor(color, -0.5),
    shadeColor(color, -0.675),
    shadeColor(color, -0.7),
    shadeColor(color, -0.775),
)

internal fun View.showSoftInput(show: Boolean) {
    val imm: InputMethodManager = systemService(context) ?: return
    if (show) {
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    } else {
        imm.hideSoftInputFromWindow(this.windowToken, 0)
    }
}

fun Context.getTextColorPrimary() = try {
    val value = TypedValue()
    val typedArray = obtainStyledAttributes(
        value.data,
        intArrayOf(android.R.attr.textColorPrimary)
    )
    try {
        typedArray.getColor(0, Color.BLACK)
    } finally {
        typedArray.recycle()
    }
} catch (_: Throwable) {
    null
}

internal fun loadPresets(from: IntArray, newColor: Int): IntArray {
    var presets = from.copyOf()

    val isMaterialColors = presets.contentEquals(MATERIAL_COLORS)

    val alpha = Color.alpha(newColor)

    // don't update the original array when modifying alpha
    if (alpha != 255) {
        // add alpha to the presets
        for (i in presets.indices) {
            val color = presets[i]
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            presets[i] = Color.argb(alpha, red, green, blue)
        }
    }
    presets = unshiftIfNotExists(presets, newColor)
    if (isMaterialColors && presets.size == 19) {
        // Add black to have a total of 20 colors if the current color is in the material color palette
        presets = pushIfNotExists(
            presets,
            Color.argb(alpha, 0, 0, 0)
        )
    }
    return presets
}

@SuppressLint("ClickableViewAccessibility")
suspend fun Activity.dialogColorPicker(
    // the original color.
    @ColorInt
    colorInitial: Int?,

    // the alpha slider
    // true to show the alpha slider.
    // Currently only supported with the ColorPickerView.
    alphaEnabled: Boolean,
): Int = suspendCancellableCoroutine { cont ->
    // dialog title string resource id.
    @StringRes
    val dialogTitle = R.string.cpv_default_title

    val initialDialogType = ColorPickerDialogType.Custom

    // the colors used for the presets.
    val initialPresets: IntArray = MATERIAL_COLORS

    // true if showing a neutral button to switch preset/custom.
    val dialogTypeSwitcher = true

    // Show/Hide the color shades in the presets picker
    // false to hide the color shades.
    val useColorShade = true

    // the shape of the color panel view.
    // Either [ColorShape.CIRCLE] or [ColorShape.SQUARE].
    val panelShape = ColorShape.Circle

    val activity = this
    val rootView = FrameLayout(activity)

    var currentColor = colorInitial ?: Color.BLACK
    var dialogType = initialDialogType
    var presets = initialPresets

    var dialog: AlertDialog? = null
    var colorPaletteAdapter: ColorPaletteAdapter? = null
    var shadesLayout: ViewGroup? = null
    var tvPercent: TextView? = null

    fun dismiss() = dialog?.dismissSafe()

    fun complete() {
        if (cont.isActive) cont.resumeCompat(currentColor)
        dismiss()
    }

    fun addPickerView() {
        CpvDialogColorPickerBinding.inflate(layoutInflater, rootView, true).apply {
            // ColorPickerDialog.colorPicker = contentView.findViewById(R.id.cpv_color_picker_view)
            // ColorPickerDialog.newColorPanel = contentView.findViewById(R.id.cpv_color_panel_new)
            // ColorPickerDialog.hexEditText = contentView.findViewById(R.id.cpv_hex)
            // val oldColorPanel: ColorPanelView = contentView.findViewById(R.id.cpv_color_panel_old)
            // val arrowRight = contentView.findViewById<ImageView>(R.id.cpv_arrow_right)
            activity.getTextColorPrimary()
                ?.let { cpvArrowRight.setColorFilter(it) }

            var fromEditText = false
            fun setHex(color: Int, fromColorPicker: Boolean = false) {
                if (fromColorPicker) {
                    if (fromEditText) {
                        fromEditText = false
                        return
                    }
                    if (cpvHex.hasFocus()) {
                        cpvHex.showSoftInput(false)
                        cpvHex.clearFocus()
                    }
                }
                val hexText = when {
                    alphaEnabled -> "%08X".format(color)
                    else -> "%06X".format(color and 0xFFFFFF)
                }
                cpvHex.setText(hexText)
            }

            setHex(currentColor)
            cpvColorPanelOld.color = currentColor
            cpvColorPickerView.apply {
                setAlphaSliderVisible(alphaEnabled)
                setColor(currentColor, true)
                onColorChangedListener = ColorPickerView.OnColorChangedListener { newColor ->
                    currentColor = newColor
                    cpvColorPanelNew.color = newColor
                    setHex(newColor, fromColorPicker = true)
                }
            }
            cpvColorPanelNew.apply {
                this.color = currentColor
                setOnClickListener {
                    if (currentColor == this.color) complete()
                }
            }
            cpvHex.apply {
                addTextChangedListener { editable ->
                    if (cpvHex.isFocused) {
                        try {
                            currentColor = editable.toString().parseColor()
                            if (currentColor != cpvColorPickerView.color) {
                                fromEditText = true
                                cpvColorPickerView.setColor(currentColor, true)
                            }
                        } catch (_: NumberFormatException) {
                        } catch (ex: Throwable) {
                            log.e(ex, "parseColorString failed.")
                        }
                    }
                }
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) showSoftInput(true)
                }
                if (!alphaEnabled) {
                    filters = arrayOf<InputFilter>(LengthFilter(6))
                }
            }
            root.setOnTouchListener { v, _ ->
                cpvHex.run {
                    when {
                        hasFocus() && v !== this -> {
                            clearFocus()
                            showSoftInput(false)
                            clearFocus()
                            true
                        }

                        else -> false
                    }
                }
            }
        }
    }

    fun createColorShades() {
        val colorShades = getColorShades(currentColor)
        shadesLayout?.takeIf { it.childCount > 0 }?.run {
            children.forEachIndexed { i, child ->
                val layout = child as FrameLayout
                val cpv: ColorPanelView = layout.findViewById(R.id.cpv_color_panel_view)
                val iv = layout.findViewById<ImageView>(R.id.cpv_color_image_view)
                cpv.color = colorShades[i]
                cpv.tag = false
                iv.setImageDrawable(null)
            }
            return
        }
        val horizontalPadding =
            activity.resources.getDimensionPixelSize(R.dimen.cpv_item_horizontal_padding)
        for (colorShade in colorShades) {
            var layoutResId: Int
            layoutResId = when (panelShape) {
                ColorShape.Square -> R.layout.cpv_color_item_square
                ColorShape.Circle -> R.layout.cpv_color_item_circle
            }
            val view = View.inflate(activity, layoutResId, null)
            val colorPanelView: ColorPanelView = view.findViewById(R.id.cpv_color_panel_view)
            val params = colorPanelView
                .layoutParams as MarginLayoutParams
            params.rightMargin = horizontalPadding
            params.leftMargin = params.rightMargin
            colorPanelView.layoutParams = params
            colorPanelView.color = colorShade
            shadesLayout?.addView(view)
            colorPanelView.post {
                // The color is black when rotating the dialog. This is a dirty fix. WTF!?
                colorPanelView.color = colorShade
            }
            colorPanelView.setOnClickListener { v: View ->
                when {
                    (v.tag as? Boolean) == true -> complete()
                    else -> {
                        currentColor = colorPanelView.color
                        colorPaletteAdapter?.selectNone()
                        shadesLayout?.children?.forEach { child ->
                            val layout = child as FrameLayout
                            val cpv: ColorPanelView = layout.findViewById(R.id.cpv_color_panel_view)
                            val iv = layout.findViewById<ImageView>(R.id.cpv_color_image_view)
                            iv.setImageResource(if (cpv === v) R.drawable.cpv_preset_checked else 0)
                            when {
                                cpv === v && ColorUtils.calculateLuminance(cpv.color) >= 0.65 ||
                                        Color.alpha(cpv.color) <= ALPHA_THRESHOLD -> {
                                    iv.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                                }

                                else -> iv.colorFilter = null
                            }
                            cpv.tag = cpv === v
                        }
                    }
                }
            }
            colorPanelView.setOnLongClickListener {
                colorPanelView.showHint()
                true
            }
        }
    }

    fun handleTransparencyChanged(transparency: Int) {
        val percentage = (transparency.toDouble() * 100 / 255).toInt()
        val alpha = 255 - transparency

        tvPercent?.text = String.format(Locale.ENGLISH, "%d%%", percentage)

        // update color:
        val red = Color.red(currentColor)
        val green = Color.green(currentColor)
        val blue = Color.blue(currentColor)
        currentColor = Color.argb(alpha, red, green, blue)

        // update items in GridView:
        colorPaletteAdapter?.apply {
            for (i in colors.indices) {
                val c = colors[i]
                colors[i] = Color.argb(
                    alpha,
                    Color.red(c),
                    Color.green(c),
                    Color.blue(c)
                )
            }
            notifyDataSetChanged()
        }

        // update shades:
        shadesLayout?.children?.forEach { child ->
            val layout = child as FrameLayout
            val cpv: ColorPanelView = layout.findViewById(R.id.cpv_color_panel_view)
            val iv = layout.findViewById<ImageView>(R.id.cpv_color_image_view)
            if (layout.tag == null) {
                // save the original border color
                layout.tag = cpv.borderColor
            }
            val c = Color.argb(
                alpha,
                Color.red(cpv.color),
                Color.green(cpv.color),
                Color.blue(cpv.color)
            )
            if (alpha <= ALPHA_THRESHOLD) {
                cpv.borderColor = c or -0x1000000
            } else {
                cpv.borderColor = layout.tag as Int
            }
            if (cpv.tag != null && cpv.tag as Boolean) {
                // The alpha changed on the selected shaded color. Update the checkmark color filter.
                if (alpha <= ALPHA_THRESHOLD) {
                    iv.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                } else {
                    if (ColorUtils.calculateLuminance(c) >= 0.65) {
                        iv.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                    } else {
                        iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                    }
                }
            }
            cpv.color = c
        }
    }

    fun addPresetView() {
        CpvDialogPresetsBinding.inflate(layoutInflater, rootView, true).apply {
            shadesLayout = this.shadesLayout
            tvPercent = this.transparencyText
            // ColorPickerDialog.shadesLayout = contentView.findViewById(R.id.shades_layout)
            // ColorPickerDialog.transparencySeekBar = contentView.findViewById(R.id.transparency_seekbar)
            // ColorPickerDialog.transparencyPercText = contentView.findViewById(R.id.transparency_text)
            // val gridView = contentView.findViewById<GridView>(R.id.gridView)
            presets = loadPresets(presets, currentColor)
            if (useColorShade) {
                createColorShades()
            } else {
                shadesLayout?.gone()
                shadesDivider.gone()
            }
            ColorPaletteAdapter(
                presets,
                presets.indexOf(currentColor),
                panelShape,
                listener = {
                    when (it) {
                        currentColor -> {
                            if (cont.isActive) cont.resumeCompat(currentColor)
                            dismiss()
                        }

                        else -> {
                            currentColor = it
                            if (useColorShade) createColorShades()
                        }
                    }
                },
            ).also {
                gridView.adapter = it
                colorPaletteAdapter = it
            }

            when {
                alphaEnabled -> {
                    val transparency = 255 - Color.alpha(currentColor)
                    val percentage = (transparency.toDouble() * 100 / 255).toInt()
                    transparencyText.text = String.format(Locale.ENGLISH, "%d%%", percentage)
                    transparencySeekbar.apply {
                        max = 255
                        progress = transparency
                        this.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                            override fun onStartTrackingTouch(seekBar: SeekBar) {}
                            override fun onStopTrackingTouch(seekBar: SeekBar) {}
                            override fun onProgressChanged(
                                seekBar: SeekBar,
                                progress: Int,
                                fromUser: Boolean,
                            ) {
                                handleTransparencyChanged(transparency = progress)
                            }
                        })
                    }
                }

                else -> {
                    transparencyLayout.gone()
                    transparencyTitle.gone()
                }
            }
        }
    }

    fun addViewByType() {
        when (dialogType) {
            ColorPickerDialogType.Custom -> addPickerView()
            ColorPickerDialogType.Presets -> addPresetView()
        }
    }
    addViewByType()

    dialog = AlertDialog.Builder(activity).apply {
        setView(rootView)
        if (dialogTitle != 0) {
            setTitle(dialogTitle)
        }
        setPositiveButton(R.string.cpv_select) { _, _ ->
            if (cont.isActive) cont.resumeCompat(currentColor)
        }
        val neutralButtonStringRes = when {
            !dialogTypeSwitcher -> 0
            else -> when (dialogType) {
                ColorPickerDialogType.Custom -> R.string.cpv_presets
                ColorPickerDialogType.Presets -> R.string.cpv_custom
            }
        }
        if (neutralButtonStringRes != 0) {
            setNeutralButton(neutralButtonStringRes, null)
            // ビルダーでボタンを指定するとダイアログを閉じるボタンになってしまうが、
            // このボタンではダイアログを閉じないので、リスナは後で設定する。
        }
    }.create()

    dialog.setOnDismissListener {
        log.i("onDismissListener. isActive=${cont.isActive}")
        if (cont.isActive) cont.resumeWithCancellationException()
    }
    cont.invokeOnCancellation { dismiss() }

    // http://stackoverflow.com/a/16972670/1048340
    dialog.window?.clearFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
    )
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
    dialog.show()

    // プリセット切り替えボタンのリスナでダイアログを閉じないようにするため、後から上書きする
    // dialog.getButton の呼び出しは show()より後に行う必要がある
    val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
    log.i("neutralButton=$neutralButton")
    neutralButton?.setOnClickListener {
        log.i("(neutralButton?.setOnClickListener. isActive=${cont.isActive}")
        rootView.removeAllViews()
        dialogType = when (dialogType) {
            ColorPickerDialogType.Custom -> {
                neutralButton.setText(R.string.cpv_custom)
                ColorPickerDialogType.Presets
            }

            ColorPickerDialogType.Presets -> {
                neutralButton.setText(R.string.cpv_presets)
                ColorPickerDialogType.Custom
            }
        }
        addViewByType()
        log.i(")neutralButton?.setOnClickListener. isActive=${cont.isActive}")
    }
}
