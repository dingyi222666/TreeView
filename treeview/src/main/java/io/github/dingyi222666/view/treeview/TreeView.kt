package io.github.dingyi222666.view.treeview

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.properties.Delegates

class TreeView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    RecyclerView(context, attrs, defStyleAttr), TreeNodeListener<Any> {

    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        0
    )

    lateinit var tree: Tree<Any>

    lateinit var binder: TreeViewBinder<Any>

    private var horizontalOffset = 0f
    private var maxChildWidth = 0f
    private var pointerId = 0
    private var pointerLastX = 0f
    private var slopExceeded = false
    private val horizontalTouchSlop = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics)
    private val maxHorizontalOffset
        get() = (maxChildWidth - width * 0.75f).coerceAtLeast(0f)


    var nodeClickListener: TreeNodeListener<Any> = EmptyTreeNodeListener()

    var supportHorizontalScroll by Delegates.observable(false) { _, old, new ->
        if (!this::coroutineScope.isInitialized || old == new) {
            return@observable
        }
        if (!new) {
            horizontalOffset = 0f
        }
        coroutineScope.launch {
            _adapter.refresh()
        }
    }

    private lateinit var _adapter: Adapter

    private lateinit var coroutineScope: CoroutineScope

    class ViewHolder(
        rootView: View,
        /**
         * Please do not use the itemView field, use this field to get the real itemView.
         * in some cases the itemView may wrap a parent View.
         */
        val currentItemView: View
    ) : RecyclerView.ViewHolder(rootView)

    private inner class Adapter(val binder: TreeViewBinder<Any>) :
        ListAdapter<TreeNode<*>, ViewHolder>(binder as DiffUtil.ItemCallback<TreeNode<*>>) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemView = binder.createView(parent, viewType)
            return ViewHolder(itemView, itemView)
        }

        override fun getItemViewType(position: Int): Int {
            return binder.getItemViewType(getItem(position) as TreeNode<Any>)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rootView = this@TreeView
            val node = getItem(position) as TreeNode<Any>
            holder.currentItemView.setOnClickListener {
                rootView.onClick(node, holder)
            }
            binder.bindView(holder, node, rootView as TreeNodeListener<Any>)

            if (supportHorizontalScroll) {
                holder.currentItemView.apply {
                    // Get child's preferred size
                    layoutParams =
                        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    measure(
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    )
                    // Apply a large width and measured height
                    layoutParams.apply {
                        width = 1000000
                        height = holder.currentItemView.measuredHeight
                    }
                    // Save current measured width for later usage
                    setTag(
                        R.id.tag_measured_width,
                        holder.currentItemView.measuredWidth
                    )
                }
            } else {
                holder.currentItemView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
        }

        override fun getItemId(position: Int): Long {
            return getItem(position).id.toLong()
        }

        internal fun refresh() {
            val currentData = currentList.toMutableList()
            submitList(listOf())
            submitList(currentData)
        }

    }

    private fun initAdapter() {
        _adapter = Adapter(binder)
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        adapter = _adapter
    }

    fun bindCoroutineScope(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Update horizontal offset
        if (supportHorizontalScroll) {
            horizontalOffset = horizontalOffset.coerceIn(0f, maxHorizontalOffset)
        } else {
            horizontalOffset = 0f
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (supportHorizontalScroll) {
            // Fetch children sizes and update max size of children
            var maxWidth = 0
            for (i in 0 until childCount) {
                maxWidth = maxWidth.coerceAtLeast(
                    (getChildAt(i).getTag(R.id.tag_measured_width) as Int?) ?: 0
                )
            }
            maxChildWidth = maxWidth.toFloat()
        } else {
            maxChildWidth = 0f
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Called by super's dispatchTouchEvent
        if (supportHorizontalScroll && horizontalOffset != 0f) {
            // Use original event for self
            return super.onTouchEvent(generateTranslatedMotionEvent(e, -horizontalOffset, 0f))
        }
        return super.onTouchEvent(e)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount == 1 && supportHorizontalScroll) {
            // Check for horizontal scrolling
            // This should be done with original coordinates
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Take down the pointer id
                    pointerId = ev.getPointerId(0)
                    pointerLastX = ev.x
                    slopExceeded = false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (ev.getPointerId(ev.actionIndex) == pointerId) {
                        if (abs(ev.x - pointerLastX) > horizontalTouchSlop) {
                            slopExceeded = true
                        }
                        if (slopExceeded) {
                            horizontalOffset = (pointerLastX - ev.x + horizontalOffset).coerceAtLeast(0f).coerceAtMost(maxHorizontalOffset)
                            pointerLastX = ev.x
                            invalidate()
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pointerId = 0
                }
            }
        }
        if (supportHorizontalScroll && horizontalOffset != 0f) {
            // Use fake coordinates for children
            return super.dispatchTouchEvent(generateTranslatedMotionEvent(ev, horizontalOffset, 0f))
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        // Translate canvas for rendering children
        canvas.translate(-horizontalOffset, 0f)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    suspend fun refresh(fastRefresh: Boolean = false) {
        if (!this::_adapter.isInitialized) {
            initAdapter()
        }

        val list = tree.toSortedList(fastGet = fastRefresh)

        _adapter.submitList(list)

    }

    override fun onClick(node: TreeNode<Any>, holder: ViewHolder) {
        if (node.hasChild) {
            onToggle(node, !node.expand, holder)
        }
        nodeClickListener.onClick(node, holder)
        coroutineScope.launch {
            tree.refresh(node as TreeNode<Any>)
            refresh(fastRefresh = true)
        }

    }

    override fun onLongClick(node: TreeNode<Any>, holder: ViewHolder) {
        nodeClickListener.onLongClick(node, holder)
    }

    override fun onToggle(node: TreeNode<Any>, isExpand: Boolean, holder: ViewHolder) {
        node.expand = isExpand
        nodeClickListener.onToggle(node, isExpand, holder)
    }


}

abstract class TreeViewBinder<T : Any> : DiffUtil.ItemCallback<TreeNode<T>>(),
    TreeNodeListener<T> {

    abstract fun createView(parent: ViewGroup, viewType: Int): View

    abstract fun bindView(
        holder: TreeView.ViewHolder,
        node: TreeNode<T>,
        listener: TreeNodeListener<T>
    )

    abstract fun getItemViewType(node: TreeNode<T>): Int


}

class EmptyTreeNodeListener : TreeNodeListener<Any>

interface TreeNodeListener<T : Any> {
    fun onClick(node: TreeNode<T>, holder: TreeView.ViewHolder) {}
    fun onLongClick(node: TreeNode<T>, holder: TreeView.ViewHolder) {}
    fun onToggle(node: TreeNode<T>, isExpand: Boolean, holder: TreeView.ViewHolder) {}
}
