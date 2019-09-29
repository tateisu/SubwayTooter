package jp.juggler.subwaytooter.view

import android.content.Context
import android.database.DataSetObservable
import android.database.DataSetObserver
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ListAdapter
import android.widget.WrapperListAdapter
import java.util.ArrayList

class HeaderGridView : GridView {
	
	private inner class FullWidthFixedViewLayout(context : Context) : FrameLayout(context) {
		override fun onMeasure(widthMeasureSpecArg : Int, heightMeasureSpec : Int) {
			val targetWidth = (this@HeaderGridView.measuredWidth
				- this@HeaderGridView.paddingLeft
				- this@HeaderGridView.paddingRight)
			
			val widthMeasureSpec = MeasureSpec.makeMeasureSpec(
				targetWidth,
				MeasureSpec.getMode(widthMeasureSpecArg)
			)
			
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
	}
	
	class Header(
		val rangeStart : Int,
		val rangeLength : Int,
		val itemHeight : Int,
		val view : View,
		val viewContainer : ViewGroup,
		val data : Any?,
		val isSelectable : Boolean
	) {
		
		var rowStart = 0
		var rowLength = 0 // includes header itself
	}
	
	companion object {
		@Suppress("unused")
		private val TAG = "HeaderGridView"
		
		private fun areAllListInfosSelectable(infos : ArrayList<Header>?) : Boolean {
			val hasNotSelectable = null != infos?.find { ! it.isSelectable }
			return ! hasNotSelectable
		}
	}
	
	private val headers = ArrayList<Header>()
	private var willCalculateRows = true
	private var lastNumColumns = -1
	private var rowEnd = 0
	
	private fun updateRows() :Boolean{
		if(!willCalculateRows) return true
		
		val numColumns = numColumns
		if( numColumns < 1) return false
		
		willCalculateRows = false
		var row = 0
		for(header in headers) {
			header.rowStart = row
			val rowLength = 1 + (header.rangeLength + numColumns - 1) / numColumns
			header.rowLength = rowLength
			row += rowLength
		}
		rowEnd = row
		lastNumColumns = numColumns
		return true
	}
	
	private fun findHeader(pos : Int) : Pair<Header, Int> {
		val row = pos / lastNumColumns
		var start = 0
		var end = headers.size
		while(end - start > 0) {
			val mid = (start + end) shr 1
			val header = headers[mid]
			when {
				row < header.rowStart -> end = mid
				row >= header.rowStart + header.rowLength -> start = mid + 1
				else -> {
					val offset = pos - header.rowStart * lastNumColumns
					return Pair(header, offset)
				}
			}
		}
		throw ArrayIndexOutOfBoundsException("pos=$pos,row=$row,start=$start,end=$end,headers.size=${headers.size}")
	}
	
	fun findListItemIndex(position : Int) : Int {
		return if(adapter is HeaderViewGridAdapter) {
			if(!updateRows()) return -1
			val (header, idx) = findHeader(position)
			val offset = idx - lastNumColumns
			if(offset in (0 until header.rangeLength)) {
				offset + header.rangeStart
			} else {
				- 1
			}
		} else {
			position
		}
	}
	
	constructor(context : Context) : super(context) {
		init()
	}
	
	constructor(context : Context, attrs : AttributeSet) : super(context, attrs) {
		init()
	}
	
	constructor(context : Context, attrs : AttributeSet, defStyle : Int) :
		super(context, attrs, defStyle) {
		init()
	}
	
	override fun setClipChildren(clipChildren : Boolean) {
		// Ignore, since the header rows depend on not being clipped
	}
	
	private fun init() {
		super.setClipChildren(false)
	}
	
	override fun onMeasure(widthMeasureSpec : Int, heightMeasureSpec : Int) {
		val oldNumColumns = numColumns
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		val numColumns = numColumns
		if(numColumns != oldNumColumns) {
			(adapter as? HeaderViewGridAdapter)?.update()
		}
	}
	
	/**
	 * Add a fixed view to appear at the top of the grid. If addHeaderView is
	 * called more than once, the views will appear in the order they were
	 * added. Views added using this call can take focus if they want.
	 *
	 *
	 * NOTE: Call this before calling setAdapter. This is so HeaderGridView can wrap
	 * the supplied cursor with one that will also account for header views.
	 *
	 * @param v The view to add.
	 * @param data Data to associate with this view
	 * @param isSelectable whether the item is selectable
	 */
	@JvmOverloads
	@Suppress("unused")
	fun addHeaderView(
		rangeStart : Int,
		rangeLength : Int,
		itemHeight : Int,
		v : View,
		data : Any? = null,
		isSelectable : Boolean = true
	) {
		
		if(adapter != null && adapter !is HeaderViewGridAdapter) {
			error("Cannot add header view to grid -- setAdapter has already been called.")
		}
		
		headers.add(
			Header(
				rangeStart = rangeStart,
				rangeLength = rangeLength,
				itemHeight = itemHeight,
				view = v,
				viewContainer = FullWidthFixedViewLayout(context)
					.apply { addView(v) },
				data = data,
				isSelectable = isSelectable
			)
		)
		
		(adapter as? HeaderViewGridAdapter)?.update()
	}
	
	/**
	 * Removes a previously-added header view.
	 *
	 * @param v The view to remove
	 * @return true if the view was removed, false if the view was not a header
	 * view
	 */
	
	@Suppress("unused")
	fun removeHeaderView(v : View) : Boolean {
		willCalculateRows = true
		
		val it = headers.iterator()
		while(it.hasNext()) {
			val info = it.next()
			if(info.view === v) {
				it.remove()
				(adapter as? HeaderViewGridAdapter)?.update()
				return true
			}
		}
		return false
	}
	
	fun reset() {
		headers.clear()
		adapter = null
	}
	
	override fun setAdapter(adapter : ListAdapter?) {
		if(adapter !=null && headers.size > 0) {
			willCalculateRows = true
			super.setAdapter(HeaderViewGridAdapter(adapter))
		} else {
			super.setAdapter(adapter)
		}
	}
	
	/**
	 * ListAdapter used when a HeaderGridView has header views. This ListAdapter
	 * wraps another one and also keeps track of the header views and their
	 * associated data objects.
	 *
	 * This is intended as a base class; you will probably not need to
	 * use this class directly in your own code.
	 */
	private inner class HeaderViewGridAdapter(private val mAdapter : ListAdapter) :
		WrapperListAdapter, Filterable {
		
		// This is used to notify the container of updates relating to number of columns
		// or headers changing, which changes the number of placeholders needed
		private val mDataSetObservable = DataSetObservable()
		internal var mAreAllFixedViewsSelectable : Boolean = false
		private val mIsFilterable : Boolean
		
		init {
			mIsFilterable = mAdapter is Filterable
			mAreAllFixedViewsSelectable = areAllListInfosSelectable(headers)
		}
		
		override fun isEmpty() : Boolean {
			return (mAdapter.isEmpty) && headers.size == 0
		}
		
		override fun areAllItemsEnabled() : Boolean {
			return mAreAllFixedViewsSelectable && mAdapter.areAllItemsEnabled()
		}
		
		override fun hasStableIds() : Boolean {
			return mAdapter.hasStableIds()
		}
		
		override fun registerDataSetObserver(observer : DataSetObserver) {
			mDataSetObservable.registerObserver(observer)
			mAdapter.registerDataSetObserver(observer)
		}
		
		override fun unregisterDataSetObserver(observer : DataSetObserver) {
			mDataSetObservable.unregisterObserver(observer)
			mAdapter.unregisterDataSetObserver(observer)
		}
		
		override fun getFilter() : Filter? {
			return if(mIsFilterable) {
				(mAdapter as Filterable).filter
			} else null
		}
		
		override fun getWrappedAdapter() : ListAdapter? {
			return mAdapter
		}
		
		fun update() {
			willCalculateRows = true
			mAreAllFixedViewsSelectable = areAllListInfosSelectable(headers)
			mDataSetObservable.notifyChanged()
		}
		
		override fun getCount() : Int {
			if(!updateRows()) return 0
			return rowEnd * lastNumColumns
		}
		
		override fun isEnabled(position : Int) : Boolean {
			if(!updateRows()) return false
			val (header, idx) = findHeader(position)
			return when {
				
				// Header
				idx == 0 -> header.isSelectable
				
				// right of header
				idx < lastNumColumns -> false
				
				// data
				else -> {
					val offset = idx - lastNumColumns
					if(offset in (0 until header.rangeLength)) {
						mAdapter.isEnabled(offset + header.rangeStart)
					} else {
						false
					}
				}
			}
		}
		
		override fun getItem(position : Int) : Any? {
			if(!updateRows()) return null
			val (header, idx) = findHeader(position)
			return when {
				
				// Header
				idx == 0 -> header.data
				
				// right of header
				idx < lastNumColumns -> null
				
				// data
				else -> {
					val offset = idx - lastNumColumns
					if(offset in (0 until header.rangeLength)) {
						mAdapter.getItem(offset + header.rangeStart)
					} else {
						null
					}
				}
			}
		}
		
		override fun getItemId(position : Int) : Long {
			if(!updateRows()) return -1L
			val (header, idx) = findHeader(position)
			return when {
				// Header, right of header
				idx < lastNumColumns -> - 1L
				
				// data
				else -> {
					val offset = idx - lastNumColumns
					if(offset in (0 until header.rangeLength)) {
						mAdapter.getItemId(offset + header.rangeStart)
					} else {
						- 1L
					}
				}
			}
		}
		
		override fun getViewTypeCount() : Int {
			return mAdapter.viewTypeCount + 1
		}
		
		override fun getItemViewType(position : Int) : Int {
			if(!updateRows()) error("view required before layout")
			val (header, idx) = findHeader(position)
			return when {
				// Header
				idx == 0 -> AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER
				
				// right of header
				// Placeholders get the last view type number
				idx < lastNumColumns -> mAdapter.viewTypeCount
				
				// data
				else -> {
					val offset = idx - lastNumColumns
					if(offset in (0 until header.rangeLength)) {
						mAdapter.getItemViewType(offset + header.rangeStart)
					} else {
						// Placeholders get the last view type number
						mAdapter.viewTypeCount
					}
				}
			}
			
		}
		
		override fun getView(position : Int, convertViewArg : View?, parent : ViewGroup) : View? {
			if(!updateRows()) error("view required before layout.")
			val (header, idx) = findHeader(position)
			return when {
				// Header
				idx == 0 -> header.viewContainer
				
				// right of header
				// Placeholders get the last view type number
				idx < lastNumColumns -> (convertViewArg ?: View(parent.context))
					.apply {
						// We need to do this because GridView uses the height of the last item
						// in a row to determine the height for the entire row.
						visibility = View.INVISIBLE
						minimumHeight = header.viewContainer.height
					}
				
				// data
				else -> {
					val offset = idx - lastNumColumns
					if(offset in (0 until header.rangeLength)) {
						mAdapter.getView(offset + header.rangeStart, convertViewArg, parent)
					} else {
						// Placeholders get the last view type number
						(convertViewArg ?: View(parent.context))
							.apply {
								// We need to do this because GridView uses the height of the last item
								// in a row to determine the height for the entire row.
								visibility = View.INVISIBLE
								minimumHeight = header.itemHeight
							}
					}
				}
			}
		}
	}
}
