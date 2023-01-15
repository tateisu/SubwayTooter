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
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.TextWatcher
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.jrummyapps.android.colorpicker.ColorPickerView.OnColorChangedListener
import java.util.*
import kotlin.math.roundToInt

/**
 *
 * A dialog to pick a color.
 *
 *
 * The [activity][Activity] that shows this dialog should implement [ColorPickerDialogListener]
 *
 *
 * Example usage:
 *
 * <pre>
 * ColorPickerDialog.newBuilder().show(activity);
</pre> *
 */
class ColorPickerDialog :
    DialogFragment(),
    OnTouchListener,
    OnColorChangedListener,
    TextWatcher {

    companion object {
        private const val ARG_ID = "id"
        private const val ARG_TYPE = "dialogType"
        private const val ARG_COLOR = "color"
        private const val ARG_ALPHA = "alpha"
        private const val ARG_PRESETS = "presets"
        private const val ARG_ALLOW_PRESETS = "allowPresets"
        private const val ARG_ALLOW_CUSTOM = "allowCustom"
        private const val ARG_DIALOG_TITLE = "dialogTitle"
        private const val ARG_SHOW_COLOR_SHADES = "showColorShades"
        private const val ARG_COLOR_SHAPE = "colorShape"
        const val TYPE_CUSTOM = 0
        const val TYPE_PRESETS = 1
        const val ALPHA_THRESHOLD = 165

        /**
         * Material design colors used as the default color presets
         */
        @JvmField
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

        /**
         * Create a new Builder for creating a [ColorPickerDialog] instance
         *
         * @return The [builder][Builder] to create the [ColorPickerDialog].
         */
        @JvmStatic
        fun newBuilder(): Builder {
            return Builder()
        }

        fun unshiftIfNotExists(array: IntArray, value: Int): IntArray {
            if (array.any { it == value }) {
                return array
            }
            val newArray = IntArray(array.size + 1)
            newArray[0] = value
            System.arraycopy(array, 0, newArray, 1, newArray.size - 1)
            return newArray
        }

        fun pushIfNotExists(array: IntArray, value: Int): IntArray {
            if (array.any { it == value }) {
                return array
            }
            val newArray = IntArray(array.size + 1)
            newArray[newArray.size - 1] = value
            System.arraycopy(array, 0, newArray, 0, newArray.size - 1)
            return newArray
        }
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(TYPE_CUSTOM, TYPE_PRESETS)
    annotation class DialogType

    var colorPickerDialogListener: ColorPickerDialogListener? = null

    var presets: IntArray = MATERIAL_COLORS

    private var rootView: FrameLayout? = null

    @ColorInt
    var color = 0
    private var dialogType = 0
    var dialogId = 0
    var showColorShades = false
    private var colorShape = 0

    // -- PRESETS --------------------------
    internal var adapter: ColorPaletteAdapter? = null
    private var shadesLayout: LinearLayout? = null
    private var transparencySeekBar: SeekBar? = null
    private var transparencyPercText: TextView? = null

    // -- CUSTOM ---------------------------
    var colorPicker: ColorPickerView? = null
    private var newColorPanel: ColorPanelView? = null
    private var hexEditText: EditText? = null
    private var showAlphaSlider = false
    private var fromEditText = false

    private val selectedItemPosition: Int
        get() = presets.indexOf(color)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (colorPickerDialogListener == null && context is ColorPickerDialogListener) {
            colorPickerDialogListener = context
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = arguments ?: error("onCreateDialog: args is null")
        val activity = activity ?: error("onCreateDialog: activity is null")
        dialogId = args.getInt(ARG_ID)
        showAlphaSlider = args.getBoolean(ARG_ALPHA)
        showColorShades = args.getBoolean(ARG_SHOW_COLOR_SHADES)
        colorShape = args.getInt(ARG_COLOR_SHAPE)
        if (savedInstanceState == null) {
            color = args.getInt(ARG_COLOR)
            dialogType = args.getInt(ARG_TYPE)
        } else {
            color = savedInstanceState.getInt(ARG_COLOR)
            dialogType = savedInstanceState.getInt(ARG_TYPE)
        }
        val rootView = FrameLayout(activity).also { this.rootView = it }
        when (dialogType) {
            TYPE_CUSTOM -> rootView.addView(createPickerView())
            TYPE_PRESETS -> rootView.addView(createPresetsView())
        }

        val builder = AlertDialog.Builder(activity)
            .setView(rootView)
            .setPositiveButton(R.string.cpv_select) { _, _ ->
                colorPickerDialogListener?.onColorSelected(dialogId, color)
            }

        val dialogTitleStringRes = args.getInt(ARG_DIALOG_TITLE)
        if (dialogTitleStringRes != 0) {
            builder.setTitle(dialogTitleStringRes)
        }
        val neutralButtonStringRes: Int
        neutralButtonStringRes =
            if (dialogType == TYPE_CUSTOM && args.getBoolean(ARG_ALLOW_PRESETS)) {
                R.string.cpv_presets
            } else if (dialogType == TYPE_PRESETS && args.getBoolean(ARG_ALLOW_CUSTOM)) {
                R.string.cpv_custom
            } else {
                0
            }
        if (neutralButtonStringRes != 0) {
            builder.setNeutralButton(neutralButtonStringRes, null)
        }
        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as AlertDialog

        // http://stackoverflow.com/a/16972670/1048340
        dialog.window?.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        // Do not dismiss the dialog when clicking the neutral button.
        val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        neutralButton?.setOnClickListener { v: View ->
            rootView!!.removeAllViews()
            when (dialogType) {
                TYPE_CUSTOM -> {
                    dialogType = TYPE_PRESETS
                    (v as Button).setText(R.string.cpv_custom)
                    rootView!!.addView(createPresetsView())
                }
                TYPE_PRESETS -> {
                    dialogType = TYPE_CUSTOM
                    (v as Button).setText(R.string.cpv_presets)
                    rootView!!.addView(createPickerView())
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        colorPickerDialogListener!!.onDialogDismissed(dialogId)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(ARG_COLOR, color)
        outState.putInt(ARG_TYPE, dialogType)
        super.onSaveInstanceState(outState)
    }

    // region Custom Picker
    private fun createPickerView(): View {
        val args = arguments ?: error("createPickerView: args is null")
        val activity = activity ?: error("createPickerView: activity is null")
        val contentView = View.inflate(getActivity(), R.layout.cpv_dialog_color_picker, null)
        colorPicker = contentView.findViewById(R.id.cpv_color_picker_view)
        val oldColorPanel: ColorPanelView = contentView.findViewById(R.id.cpv_color_panel_old)
        newColorPanel = contentView.findViewById(R.id.cpv_color_panel_new)
        val arrowRight = contentView.findViewById<ImageView>(R.id.cpv_arrow_right)
        hexEditText = contentView.findViewById(R.id.cpv_hex)
        try {
            val value = TypedValue()
            val typedArray = activity.obtainStyledAttributes(
                value.data, intArrayOf(android.R.attr.textColorPrimary)
            )
            val arrowColor = typedArray.getColor(0, Color.BLACK)
            typedArray.recycle()
            arrowRight.setColorFilter(arrowColor)
        } catch (ignored: Exception) {
        }
        oldColorPanel.color = args.getInt(ARG_COLOR)

        val c = color

        colorPicker?.apply {
            setAlphaSliderVisible(showAlphaSlider)
            setColor(c, true)
            onColorChangedListener = this@ColorPickerDialog
        }

        newColorPanel?.apply {
            color = c
            setOnClickListener {
                if (color == c) {
                    colorPickerDialogListener?.onColorSelected(dialogId, color)
                    dismiss()
                }
            }
        }

        hexEditText?.apply {
            setHex(color)
            addTextChangedListener(this@ColorPickerDialog)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showSoftInput(true)
            }
            if (!showAlphaSlider) {
                filters = arrayOf<InputFilter>(LengthFilter(6))
            }
        }

        contentView.setOnTouchListener(this)

        return contentView
    }

    private fun View?.showSoftInput(show: Boolean) {
        this ?: return
        val imm = (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?: return
        if (show) {
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        } else {
            imm.hideSoftInputFromWindow(this.windowToken, 0)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val hexEditText = this.hexEditText
        if (hexEditText?.hasFocus() == true && v !== hexEditText) {
            hexEditText.clearFocus()
            hexEditText.showSoftInput(false)
            hexEditText.clearFocus()
            return true
        }
        return false
    }

    override fun onColorChanged(newColor: Int) {
        color = newColor
        newColorPanel!!.color = newColor
        if (!fromEditText) {
            setHex(newColor)
            val hexEditText = this.hexEditText
            if (hexEditText?.hasFocus() == true) {
                hexEditText.showSoftInput(false)
                hexEditText.clearFocus()
            }
        }
        fromEditText = false
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable) {
        if (hexEditText!!.isFocused) {
            try {
                val color = parseColorString(s.toString())
                if (color != colorPicker!!.color) {
                    fromEditText = true
                    colorPicker!!.setColor(color, true)
                }
            } catch (ex: NumberFormatException) {
                // nothing to do
            }
        }
    }

    private fun setHex(color: Int) {
        val hexText = when {
            showAlphaSlider -> "%08X".format(color)
            else -> "%06X".format(color and 0xFFFFFF)
        }
        hexEditText?.setText(hexText)
    }

    // -- endregion --
    // region Presets Picker
    private fun createPresetsView(): View {
        val contentView = View.inflate(activity, R.layout.cpv_dialog_presets, null)
        shadesLayout = contentView.findViewById(R.id.shades_layout)
        transparencySeekBar = contentView.findViewById(R.id.transparency_seekbar)
        transparencyPercText = contentView.findViewById(R.id.transparency_text)
        val gridView = contentView.findViewById<GridView>(R.id.gridView)
        loadPresets()
        if (showColorShades) {
            createColorShades(color)
        } else {
            shadesLayout?.visibility = View.GONE
            contentView.findViewById<View>(R.id.shades_divider).visibility = View.GONE
        }
        adapter = ColorPaletteAdapter(presets, selectedItemPosition, colorShape) {
            when (it) {
                color -> {
                    colorPickerDialogListener?.onColorSelected(dialogId, color)
                    dismiss()
                }
                else -> {
                    color = it
                    if (showColorShades) {
                        createColorShades(color)
                    }
                }
            }
        }

        gridView.adapter = adapter
        if (showAlphaSlider) {
            setupTransparency()
        } else {
            contentView.findViewById<View>(R.id.transparency_layout).visibility = View.GONE
            contentView.findViewById<View>(R.id.transparency_title).visibility = View.GONE
        }
        return contentView
    }

    private fun loadPresets() {
        presets = arguments?.getIntArray(ARG_PRESETS) ?: MATERIAL_COLORS
        presets = presets.copyOf()

        val isMaterialColors = presets.contentEquals(MATERIAL_COLORS)

        val alpha = Color.alpha(color)

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
        presets = unshiftIfNotExists(presets, color)
        if (isMaterialColors && presets.size == 19) {
            // Add black to have a total of 20 colors if the current color is in the material color palette
            presets = pushIfNotExists(presets, Color.argb(alpha, 0, 0, 0))
        }
    }

    private fun createColorShades(@ColorInt color: Int) {
        val colorShades = getColorShades(color)
        if (shadesLayout!!.childCount != 0) {
            for (i in 0 until shadesLayout!!.childCount) {
                val layout = shadesLayout!!.getChildAt(i) as FrameLayout
                val cpv: ColorPanelView = layout.findViewById(R.id.cpv_color_panel_view)
                val iv = layout.findViewById<ImageView>(R.id.cpv_color_image_view)
                cpv.color = colorShades[i]
                cpv.tag = false
                iv.setImageDrawable(null)
            }
            return
        }
        val horizontalPadding = resources
            .getDimensionPixelSize(R.dimen.cpv_item_horizontal_padding)
        for (colorShade in colorShades) {
            var layoutResId: Int
            layoutResId = if (colorShape == ColorShape.SQUARE) {
                R.layout.cpv_color_item_square
            } else {
                R.layout.cpv_color_item_circle
            }
            val view = View.inflate(activity, layoutResId, null)
            val colorPanelView: ColorPanelView = view.findViewById(R.id.cpv_color_panel_view)
            val params = colorPanelView
                .layoutParams as MarginLayoutParams
            params.rightMargin = horizontalPadding
            params.leftMargin = params.rightMargin
            colorPanelView.layoutParams = params
            colorPanelView.color = colorShade
            shadesLayout!!.addView(view)
            colorPanelView.post {
                // The color is black when rotating the dialog. This is a dirty fix. WTF!?
                colorPanelView.color = colorShade
            }
            colorPanelView.setOnClickListener { v: View ->
                if (v.tag is Boolean && v.tag as Boolean) {
                    colorPickerDialogListener!!.onColorSelected(
                        dialogId,
                        this@ColorPickerDialog.color
                    )
                    dismiss()
                    return@setOnClickListener   // already selected
                }
                this@ColorPickerDialog.color = colorPanelView.color
                adapter!!.selectNone()
                for (i in 0 until shadesLayout!!.childCount) {
                    val layout = shadesLayout!!.getChildAt(i) as FrameLayout
                    val cpv: ColorPanelView = layout.findViewById(R.id.cpv_color_panel_view)
                    val iv = layout.findViewById<ImageView>(R.id.cpv_color_image_view)
                    iv.setImageResource(if (cpv === v) R.drawable.cpv_preset_checked else 0)
                    if (cpv === v && ColorUtils.calculateLuminance(cpv.color) >= 0.65 ||
                        Color.alpha(cpv.color) <= ALPHA_THRESHOLD
                    ) {
                        iv.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                    } else {
                        iv.colorFilter = null
                    }
                    cpv.tag = cpv === v
                }
            }
            colorPanelView.setOnLongClickListener {
                colorPanelView.showHint()
                true
            }
        }
    }

    private fun shadeColor(@ColorInt color: Int, percent: Double): Int {
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

    private fun getColorShades(@ColorInt color: Int): IntArray {
        return intArrayOf(
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
            shadeColor(color, -0.775)
        )
    }

    private fun setupTransparency() {

        val transparency = 255 - Color.alpha(color)
        val percentage = (transparency.toDouble() * 100 / 255).toInt()

        transparencyPercText?.text =
            String.format(Locale.ENGLISH, "%d%%", percentage)

        transparencySeekBar?.apply {
            max = 255
            progress = transparency
            this.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    handleTransparencyChanged(progress)
                }
            })
        }
    }

    private fun handleTransparencyChanged(transparency: Int) {

        val percentage = (transparency.toDouble() * 100 / 255).toInt()
        val alpha = 255 - transparency

        transparencyPercText?.text =
            String.format(Locale.ENGLISH, "%d%%", percentage)

        // update color:
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        color = Color.argb(alpha, red, green, blue)

        // update items in GridView:
        adapter?.apply {
            for (i in colors.indices) {
                val color = colors[i]
                colors[i] = Color.argb(
                    alpha,
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)
                )
            }
            notifyDataSetChanged()
        }

        // update shades:
        shadesLayout?.apply {
            for (i in 0 until childCount) {
                val layout = getChildAt(i) as FrameLayout
                val cpv: ColorPanelView = layout.findViewById(R.id.cpv_color_panel_view)
                val iv = layout.findViewById<ImageView>(R.id.cpv_color_image_view)
                if (layout.tag == null) {
                    // save the original border color
                    layout.tag = cpv.borderColor
                }
                var color = cpv.color
                color = Color.argb(
                    alpha,
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)
                )
                if (alpha <= ALPHA_THRESHOLD) {
                    cpv.borderColor = color or -0x1000000
                } else {
                    cpv.borderColor = layout.tag as Int
                }
                if (cpv.tag != null && cpv.tag as Boolean) {
                    // The alpha changed on the selected shaded color. Update the checkmark color filter.
                    if (alpha <= ALPHA_THRESHOLD) {
                        iv.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                    } else {
                        if (ColorUtils.calculateLuminance(color) >= 0.65) {
                            iv.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                        } else {
                            iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                        }
                    }
                }
                cpv.color = color
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    class Builder(
        // dialog title string resource id.
        @StringRes
        var dialogTitle: Int = R.string.cpv_default_title,

        // which dialog view to show.
        // Either [ColorPickerDialog.TYPE_CUSTOM] or [ColorPickerDialog.TYPE_PRESETS].
        var dialogType: Int = TYPE_PRESETS,

        // the colors used for the presets.
        var presets: IntArray = MATERIAL_COLORS,

        // the original color.
        @ColorInt
        var color: Int = Color.BLACK,

        // the dialog id used for callbacks.
        var dialogId: Int = 0,

        // the alpha slider
        // true to show the alpha slider.
        // Currently only supported with the ColorPickerView.
        var showAlphaSlider: Boolean = false,

        // Show/Hide a neutral button to select preset colors.
        // false to disable showing the presets button.
        var allowPresets: Boolean = true,

        // Show/Hide the neutral button to select a custom color.
        // false to disable showing the custom button.
        var allowCustom: Boolean = true,

        // Show/Hide the color shades in the presets picker
        // false to hide the color shades.
        var showColorShades: Boolean = true,

        // the shape of the color panel view.
        // Either [ColorShape.CIRCLE] or [ColorShape.SQUARE].
        var colorShape: Int = ColorShape.CIRCLE,
    ) {
        fun setDialogTitle(@StringRes dialogTitle: Int): Builder {
            this.dialogTitle = dialogTitle
            return this
        }

        fun setDialogType(@DialogType dialogType: Int): Builder {
            this.dialogType = dialogType
            return this
        }

        fun setPresets(presets: IntArray): Builder {
            this.presets = presets
            return this
        }

        fun setColor(color: Int): Builder {
            this.color = color
            return this
        }

        fun setDialogId(dialogId: Int): Builder {
            this.dialogId = dialogId
            return this
        }

        fun setShowAlphaSlider(showAlphaSlider: Boolean): Builder {
            this.showAlphaSlider = showAlphaSlider
            return this
        }

        fun setAllowPresets(allowPresets: Boolean): Builder {
            this.allowPresets = allowPresets
            return this
        }

        fun setAllowCustom(allowCustom: Boolean): Builder {
            this.allowCustom = allowCustom
            return this
        }

        fun setShowColorShades(showColorShades: Boolean): Builder {
            this.showColorShades = showColorShades
            return this
        }

        fun setColorShape(colorShape: Int): Builder {
            this.colorShape = colorShape
            return this
        }

        /**
         * Create the [ColorPickerDialog] instance.
         */
        fun create(): ColorPickerDialog {
            val dialog = ColorPickerDialog()
            val args = Bundle()
            args.putInt(ARG_ID, dialogId)
            args.putInt(ARG_TYPE, dialogType)
            args.putInt(ARG_COLOR, color)
            args.putIntArray(ARG_PRESETS, presets)
            args.putBoolean(ARG_ALPHA, showAlphaSlider)
            args.putBoolean(ARG_ALLOW_CUSTOM, allowCustom)
            args.putBoolean(ARG_ALLOW_PRESETS, allowPresets)
            args.putInt(ARG_DIALOG_TITLE, dialogTitle)
            args.putBoolean(ARG_SHOW_COLOR_SHADES, showColorShades)
            args.putInt(ARG_COLOR_SHAPE, colorShape)
            dialog.arguments = args
            return dialog
        }

        /**
         * Create and show the [ColorPickerDialog] created with this builder.
         */
        fun show(activity: FragmentActivity) {
            create().show(activity.supportFragmentManager, "color-picker-dialog")
        }
    }
}
