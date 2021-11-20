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
@file:Suppress("DEPRECATION")
package com.jrummyapps.android.colorpicker

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.preference.Preference
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.jrummyapps.android.colorpicker.ColorPickerDialog.Companion.newBuilder

/**
 * A Preference to select a color
 */
class ColorPreference : Preference, ColorPickerDialogListener {

    companion object {
        private const val SIZE_NORMAL = 0
        private const val SIZE_LARGE = 1
    }

    interface OnShowDialogListener {
        fun onShowColorPickerDialog(title: String?, currentColor: Int)
    }

    private var onShowDialogListener: OnShowDialogListener? = null
    private var color = Color.BLACK
    private var showDialog = false

    @ColorPickerDialog.DialogType
    private var dialogType = 0
    private var colorShape = 0
    private var allowPresets = false
    private var allowCustom = false
    private var showAlphaSlider = false
    private var showColorShades = false
    private var previewSize = 0

    // An array of color ints
    var presets: IntArray = ColorPickerDialog.MATERIAL_COLORS

    private var dialogTitle = 0

    /**
     * The tag used for the [ColorPickerDialog].
     */
    private val fragmentTag: String
        get() = "color_$key"

    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet) {
        isPersistent = true
        val a = context.obtainStyledAttributes(attrs, R.styleable.ColorPreference)
        showDialog = a.getBoolean(R.styleable.ColorPreference_cpv_showDialog, true)
        dialogType =
            a.getInt(R.styleable.ColorPreference_cpv_dialogType, ColorPickerDialog.TYPE_PRESETS)
        colorShape = a.getInt(R.styleable.ColorPreference_cpv_colorShape, ColorShape.CIRCLE)
        allowPresets = a.getBoolean(R.styleable.ColorPreference_cpv_allowPresets, true)
        allowCustom = a.getBoolean(R.styleable.ColorPreference_cpv_allowCustom, true)
        showAlphaSlider = a.getBoolean(R.styleable.ColorPreference_cpv_showAlphaSlider, false)
        showColorShades = a.getBoolean(R.styleable.ColorPreference_cpv_showColorShades, true)
        previewSize = a.getInt(R.styleable.ColorPreference_cpv_previewSize, SIZE_NORMAL)
        val presetsResId = a.getResourceId(R.styleable.ColorPreference_cpv_colorPresets, 0)
        dialogTitle =
            a.getResourceId(R.styleable.ColorPreference_cpv_dialogTitle, R.string.cpv_default_title)
        presets = if (presetsResId != 0) {
            context.resources.getIntArray(presetsResId)
        } else {
            ColorPickerDialog.MATERIAL_COLORS
        }
        widgetLayoutResource = if (colorShape == ColorShape.CIRCLE) {
            if (previewSize == SIZE_LARGE) R.layout.cpv_preference_circle_large else R.layout.cpv_preference_circle
        } else {
            if (previewSize == SIZE_LARGE) R.layout.cpv_preference_square_large else R.layout.cpv_preference_square
        }
        a.recycle()
    }

    override fun onClick() {
        super.onClick()
        if (onShowDialogListener != null) {
            onShowDialogListener!!.onShowColorPickerDialog(title as String, color)
        } else if (showDialog) {
            val dialog = newBuilder()
                .setDialogType(dialogType)
                .setDialogTitle(dialogTitle)
                .setColorShape(colorShape)
                .setPresets(presets)
                .setAllowPresets(allowPresets)
                .setAllowCustom(allowCustom)
                .setShowAlphaSlider(showAlphaSlider)
                .setShowColorShades(showColorShades)
                .setColor(color)
                .create()
            dialog.colorPickerDialogListener = this@ColorPreference
            val fm = fragmentManager
            if (fm != null) {
                dialog.show(fm, fragmentTag)
            }
        }
    }

    private val fragmentManager: FragmentManager?
        get() {
            val context = context
            return if (context is FragmentActivity) {
                context.supportFragmentManager
            } else null
        }

    override fun onAttachedToActivity() {
        super.onAttachedToActivity()
        val fm = fragmentManager
        if (showDialog && fm != null) {
            val fragment = fm.findFragmentByTag(fragmentTag) as ColorPickerDialog?
            if (fragment != null) {
                // re-bind preference to fragment
                fragment.colorPickerDialogListener = this
            }
        }
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        val preview: ColorPanelView = view.findViewById(R.id.cpv_preference_preview_color_panel)
        preview.color = color
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any) {
        if (restorePersistedValue) {
            color = getPersistedInt(-0x1000000)
        } else {
            color = defaultValue as Int
            persistInt(color)
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInteger(index, Color.BLACK)
    }

    override fun onColorSelected(dialogId: Int, @ColorInt color: Int) {
        saveValue(color)
    }

    override fun onDialogDismissed(dialogId: Int) {
        // no-op
    }

    /**
     * Set the new color
     *
     * @param color The newly selected color
     */
    private fun saveValue(@ColorInt color: Int) {
        this.color = color
        persistInt(this.color)
        notifyChanged()
        callChangeListener(color)
    }

    /**
     * The listener used for showing the [ColorPickerDialog].
     * Call [.saveValue] after the user chooses a color.
     * If this is set then it is up to you to show the dialog.
     *
     * @param listener The listener to show the dialog
     */
    fun setOnShowDialogListener(listener: OnShowDialogListener?) {
        onShowDialogListener = listener
    }
}