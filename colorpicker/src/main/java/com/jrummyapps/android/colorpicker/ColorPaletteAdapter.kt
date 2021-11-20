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

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import androidx.core.graphics.ColorUtils

internal class ColorPaletteAdapter(
    val colors: IntArray,
    var selectedPosition: Int,
    @ColorShape val colorShape: Int,
    val listener: (Int)->Unit
) : BaseAdapter() {

    override fun getCount(): Int = colors.size
    override fun getItem(position: Int): Any = colors[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
        (convertView?.tag as? ViewHolder ?: ViewHolder(parent))
            .apply { bind(position) }
            .root

    fun selectNone() {
        selectedPosition = -1
        notifyDataSetChanged()
    }

    private inner class ViewHolder(parent: ViewGroup) {
        val root: View = run {
            LayoutInflater.from(parent.context).inflate(
                if (colorShape == ColorShape.SQUARE) {
                    R.layout.cpv_color_item_square
                } else {
                    R.layout.cpv_color_item_circle
                },
                parent,
                false
            ).apply { tag = this@ViewHolder }
        }

        val colorPanelView: ColorPanelView = root.findViewById(R.id.cpv_color_panel_view)

        val imageView: ImageView = root.findViewById(R.id.cpv_color_image_view)

        val originalBorderColor: Int = colorPanelView.borderColor

        fun bind(position: Int) {
            val color = colors[position]
            val alpha = Color.alpha(color)
            colorPanelView.color = color
            imageView.setImageResource(
                if (selectedPosition == position)
                    R.drawable.cpv_preset_checked
                else
                    0
            )
            when {
                alpha == 255 -> {
                    if (position == selectedPosition && ColorUtils.calculateLuminance(colors[position]) >= 0.65) {
                        imageView.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                    } else {
                        imageView.colorFilter = null
                    }
                }
                alpha <= ColorPickerDialog.ALPHA_THRESHOLD -> {
                    colorPanelView.borderColor = color or -0x1000000
                    imageView.setColorFilter( /*color | 0xFF000000*/Color.BLACK,
                        PorterDuff.Mode.SRC_IN
                    )
                }
                else -> {
                    colorPanelView.borderColor = originalBorderColor
                    imageView.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                }
            }

            colorPanelView.setOnClickListener {
                if (selectedPosition != position) {
                    selectedPosition = position
                    notifyDataSetChanged()
                }
                listener(colors[position])
            }
            colorPanelView.setOnLongClickListener {
                colorPanelView.showHint()
                true
            }
        }
    }
}