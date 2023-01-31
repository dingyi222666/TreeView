package io.github.dingyi222666.view.treeview

import android.annotation.SuppressLint
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

/**
 * TreeView.
 *
 * TreeView based on RecyclerView implementation.
 *
 * The data in the [AbstractTree] can be displayed.
 *
 * @param T Data type of [tree]
 * @see [AbstractTree]
 * @see [Tree]
 * @see [TreeViewBinder]
 */
class TreeView<T : Any>(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    RecyclerView(context, attrs, defStyleAttr), TreeNodeEventListener<T> {

    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        0
    )

    private var horizontalOffset = 0f
    private var maxChildWidth = 0f
    private var pointerId = 0
    private var pointerLastX = 0f
    private var slopExceeded = false
    private val horizontalTouchSlop =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics)
    private val maxHorizontalOffset
        get() = (maxChildWidth - width * 0.75f).coerceAtLeast(0f)

    /**
     * Tree structure.
     *
     * Set it to allow TreeView to fetch node data.
     */
    lateinit var tree: Tree<T>

    /**
     * TreeView Binder.
     *
     * Set it to bind between node and view
     */
    lateinit var binder: TreeViewBinder<T>

    /**
     * Event listener for the node.
     *
     * Set it to listen for event on the node, such as a click on the node event.
     */
    var nodeEventListener: TreeNodeEventListener<T> =
        EmptyTreeNodeEventListener() as TreeNodeEventListener<T>

    /**
     * Whether horizontal scrolling is supported.
     *
     * In most cases, you don't need to turn it on.
     *
     * you only need to turn it on when the indentation of the node binded view is too large, or when the width of the view itself is too large for the screen to be fully displayed.
     *
     * Note: This is still an experimental feature and some problems may arise.
     */
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
    ) : RecyclerView.ViewHolder(rootView)

    private inner class Adapter(val binder: TreeViewBinder<T>) :
        ListAdapter<TreeNode<T>, ViewHolder>(binder as DiffUtil.ItemCallback<TreeNode<T>>) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemView = binder.createView(parent, viewType)
            return ViewHolder(itemView)
        }

        override fun getItemViewType(position: Int): Int {
            return binder.getItemViewType(getItem(position))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rootView = this@TreeView
            val node = getItem(position)

            holder.itemView.setOnClickListener {
                rootView.onClick(node, holder)
            }

            holder.itemView.setOnLongClickListener {
                return@setOnLongClickListener rootView.onLongClick(node, holder)
            }

            binder.bindView(holder, node, rootView)

            if (supportHorizontalScroll) {
                holder.itemView.apply {
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
                        height = holder.itemView.measuredHeight
                    }
                    // Save current measured width for later usage
                    setTag(
                        R.id.tag_measured_width,
                        holder.itemView.measuredWidth
                    )
                }
            } else {
                holder.itemView.layoutParams =
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
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

    /**
     * Bind the concurrent scope of the TreeView.
     *
     * Some operations need to run on a concurrent scope, set it to make TreeView work better.
     *
     * Note: TreeView is not responsible for closing the concurrent scope, it is up to the caller to do so.
     */
    fun bindCoroutineScope(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    /**
     * Refresh the data.
     *
     * Call this method to refresh the node data to display on the TreeView.
     *
     * @param [fastRefresh] Whether to quick refresh or not.
     * @param [node] The node to be refreshed; if the value is not null, only the child nodes under the node will be refreshed.
     *
     * If ture, only data from the cache will be fetched instead of calling the [TreeNodeGenerator]
     * @see [AbstractTree.toSortedList]
     */
    suspend fun refresh(fastRefresh: Boolean = false, node: TreeNode<T>? = null) {
        if (!this::_adapter.isInitialized) {
            initAdapter()
        }

        var fastRefreshOnLocal = fastRefresh

        if (node != null) {
            tree.refreshWithChild(node,withExpandable = true)
            fastRefreshOnLocal = true
        }

        val list = tree.toSortedList(fastVisit = fastRefreshOnLocal)

        _adapter.submitList(list)

    }


    /**
     * Switch the state of the node.
     *
     * If the node is a leaf node, then the method will do nothing.
     * Otherwise, it will switch the expand and collapse state of the node.
     *
     * @param [node] Node that need to switch node state
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun toggleNode(node: TreeNode<T>, fullRefresh: Boolean = false) {
        if (!node.isChild) {
            return
        }
        if (node.expand) {
            collapseNode(node, fullRefresh)
        } else {
            expandNode(node, fullRefresh)
        }
    }

    /**
     * Expand all nodes.
     *
     * Start from the root node and set all non leaf nodes to expanded state
     *
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun expandAll(fullRefresh: Boolean = false) {
        tree.expandAll(fullRefresh)
        refresh(true)
    }

    /**
     * Expand the given node and its children start from it
     *
     * Expand the node from the given node, which also includes all its children.
     *
     * @param [node] Node to be expanded
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun expandAll(node: TreeNode<T>, fullRefresh: Boolean = false) {
        tree.expandAll(node, fullRefresh)
        refresh(true,node)
    }

    /**
     * expand node.
     *
     * This will expand the children of the given node with no change in the state of the children.
     * This is especially different from [expandAll].
     *
     * @param [node] Node to be expanded
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun expandNode(node: TreeNode<T>, fullRefresh: Boolean = false) {
        tree.expandNode(node, fullRefresh)
        refresh(true,node)
    }

    /**
     * Collapse all nodes.
     *
     * Start from the root node and set all non leaf nodes to collapsed state
     *
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun collapseAll(fullRefresh: Boolean = false) {
        tree.collapseAll(fullRefresh)
        refresh(true)
    }

    /**
     * Collapse the given node and its children start from it
     *
     * Collapse the node from the given node, which also includes all its children.
     *
     * @param [node] Node to be collapsed
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun collapseAll(node: TreeNode<T>, fullRefresh: Boolean = false) {
        tree.collapseAll(node, fullRefresh)
        refresh(true,node)
    }

    /**
     * collapse node.
     *
     * This will collapse the children of the given node with no change in the state of the children.
     * This is especially different from [collapseAll].
     *
     * @param [node] Node to be collapsed
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun collapseNode(node: TreeNode<T>, fullRefresh: Boolean = false) {
        tree.collapseNode(node, fullRefresh)
        refresh(true,node)
    }

    /**
     * Expand nodes of the given depth
     *
     * This will expand the nodes that have the given depth and does not include its children
     *
     * @param [depth] Given depth
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
     */
    suspend fun collapseFrom(depth: Int, fullRefresh: Boolean = false) {
        tree.collapseFrom(depth, fullRefresh)
        refresh(true)
    }


    /**
     * Collapse the nodes of the given depth
     *
     * This will collapse the nodes that have the given depth and does not include its children
     *
     * @param [depth] Given depth
     * @param [fullRefresh] Whether to fetch nodes from the node generator when refreshed, if false, then nodes will be fetched from the cache
    .
     */
    suspend fun expandUntil(depth: Int, fullRefresh: Boolean = false) {
        tree.expandUntil(depth, fullRefresh)
        refresh(true)
    }


    private fun initAdapter() {
        _adapter = Adapter(binder)
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        adapter = _adapter
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Update horizontal offset
        horizontalOffset = if (supportHorizontalScroll) {
            horizontalOffset.coerceIn(0f, maxHorizontalOffset)
        } else 0f
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (supportHorizontalScroll) {
            // Fetch children sizes and update max size of children
            var maxWidth = 0
            for (i in 0 until childCount) {
                maxWidth = maxWidth.coerceAtLeast(
                    getChildAt(i).getTag(R.id.tag_measured_width) as Int? ?: 0
                )
            }
            maxChildWidth = maxWidth.toFloat()
        } else {
            maxChildWidth = 0f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
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
                            horizontalOffset =
                                (pointerLastX - ev.x + horizontalOffset).coerceAtLeast(0f)
                                    .coerceAtMost(maxHorizontalOffset)
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

    override fun onClick(node: TreeNode<T>, holder: ViewHolder) {
        if (node.isChild) {
            onToggle(node, !node.expand, holder)
        }

        nodeEventListener.onClick(node, holder)

        if (!node.isChild) {
            return
        }

        coroutineScope.launch {
            refresh(fastRefresh = true,node = node)
        }

    }

    override fun onLongClick(node: TreeNode<T>, holder: ViewHolder): Boolean {
        return nodeEventListener.onLongClick(node, holder)
    }

    override fun onToggle(node: TreeNode<T>, isExpand: Boolean, holder: ViewHolder) {
        node.expand = isExpand
        nodeEventListener.onToggle(node, isExpand, holder)
    }

}

/**
 * Binder for TreeView and nodes.
 *
 * TreeView calls this class to get the generated itemView and bind the node data to the itemView
 *
 * @see [TreeView.binder]
 */
abstract class TreeViewBinder<T : Any> : DiffUtil.ItemCallback<TreeNode<T>>(),
    TreeNodeEventListener<T> {

    /**
     * like [RecyclerView.Adapter.onCreateViewHolder].
     *
     * Simply provide View. No need to provide a ViewHolder.
     *
     * @see [RecyclerView.Adapter.onCreateViewHolder]
     */
    abstract fun createView(parent: ViewGroup, viewType: Int): View

    /**
     * like [RecyclerView.Adapter.onBindViewHolder]
     *
     * The adapter calls this method to display the data in the node to the view
     *
     *
     * @param [node] target node
     * @param [listener] The root event listener of the TreeView.
     *
     * If you need to override the itemView's click event or other action separately,
     * call this event listener after you have completed your action.
     *
     * Otherwise the listener you set will not work either.
     *
     * @see [RecyclerView.Adapter.onBindViewHolder]
     */
    abstract fun bindView(
        holder: TreeView.ViewHolder,
        node: TreeNode<T>,
        listener: TreeNodeEventListener<T>
    )

    /**
     * like [RecyclerView.Adapter.getItemViewType]
     *
     * For inter node data, the type (whether it is a leaf node or not) varies and different layouts may need to be provided.
     *
     * You can return different numbers and these return values are mapped in the viewType in the [createView]
     *
     * @see [RecyclerView.Adapter.getItemViewType]
     * @see [createView]
     */
    abstract fun getItemViewType(node: TreeNode<T>): Int

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(
        oldItem: TreeNode<T>,
        newItem: TreeNode<T>
    ): Boolean {
        return oldItem == newItem && oldItem.data == newItem.data
    }

    override fun areItemsTheSame(
        oldItem: TreeNode<T>,
        newItem: TreeNode<T>
    ): Boolean {
        return oldItem.id == newItem.id
    }

}

class EmptyTreeNodeEventListener : TreeNodeEventListener<Any>

/**
 * Event listener interface for tree nodes.
 *
 * Currently supported, [onClick], [onLongClick], and [onToggle]
 */
interface TreeNodeEventListener<T : Any> {

    /**
     * Called when a node has been clicked.
     *
     * @param node Clicked node
     * @param holder Node binding of the holder
     */
    fun onClick(node: TreeNode<T>, holder: TreeView.ViewHolder) {}

    /**
     * Called when a node has been clicked and held.
     *
     * @param node Clicked node
     * @param holder Node binding of the holder
     * @return `true` if the callback consumed the long click, false otherwise.
     */
    fun onLongClick(node: TreeNode<T>, holder: TreeView.ViewHolder): Boolean {
        return false
    }

    /**
     * Called when a node is clicked when it is a child node
     *
     * @param node Clicked node
     * @param isExpand Is the node expanded
     * @param holder Node binding of the holder
     */
    fun onToggle(node: TreeNode<T>, isExpand: Boolean, holder: TreeView.ViewHolder) {}
}
