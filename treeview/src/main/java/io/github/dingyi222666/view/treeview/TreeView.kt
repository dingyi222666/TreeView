package io.github.dingyi222666.view.treeview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.HorizontalScrollView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.properties.Delegates
import kotlin.properties.ObservableProperty

class TreeView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    RecyclerView(context, attrs, defStyleAttr), TreeNodeListener<Any> {

    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        0
    )

    lateinit var tree: Tree<Any>

    lateinit var binder: TreeViewBinder<Any>

    var nodeClickListener: TreeNodeListener<Any> = EmptyTreeNodeListener()

    var supportHorizontalScroll by Delegates.observable(false) { _, old, new ->
        if (!this::coroutineScope.isInitialized) {
            return@observable
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
    ) : RecyclerView.ViewHolder(rootView) {

        internal var isLayoutFinish = false

        fun requireCustomHorizontalScrollView(): CustomHorizontalScrollView {
            return itemView as CustomHorizontalScrollView
        }
    }


    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
    }

    private inner class Adapter(val binder: TreeViewBinder<Any>) :
        ListAdapter<TreeNode<*>, ViewHolder>(binder as DiffUtil.ItemCallback<TreeNode<*>>) {

        internal val viewHolderList = arrayListOf<ViewHolder>()

        private val viewHolderListLock = ReentrantReadWriteLock()

        private var offsetX = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemView = binder.createView(parent, viewType)
            if (supportHorizontalScroll) {
                val rootScrollView = CustomHorizontalScrollView(parent.context)
                rootScrollView.isHorizontalScrollBarEnabled = false
                rootScrollView.overScrollMode = OVER_SCROLL_NEVER

                rootScrollView.layoutParams = MarginLayoutParams(itemView.layoutParams.width, itemView.layoutParams.height)
                rootScrollView.addView(itemView, itemView.layoutParams.width, itemView.layoutParams.height)
                return ViewHolder(rootScrollView, itemView)
            }
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

            if (!supportHorizontalScroll) {
                return
            }

            viewHolderListLock.write {
                if (!viewHolderList.contains(holder)) {
                    viewHolderList.add(holder)
                }
            }

            val rootScrollView = holder.requireCustomHorizontalScrollView()

            rootScrollView.setOnCustomScrollChangeListener { _,
                                                             scrollX,
                                                             _,
                                                             _,
                                                             _ ->
                offsetX = scrollX
                viewHolderListLock.read {
                    viewHolderList.forEach { scrollViewHolder ->
                        if (scrollViewHolder !== holder /*&& !scrollViewHolder.isRecyclable*/) {
                            scrollViewHolder.requireCustomHorizontalScrollView()
                                .scrollTo(scrollX, 0)
                        }
                    }
                }
            }

            rootScrollView.post {
                if (!holder.isLayoutFinish) {
                    rootScrollView.scrollTo(offsetX, 0);
                    holder.isLayoutFinish = true
                }
            }

            holder.itemView
                .viewTreeObserver
                .addOnGlobalLayoutListener {
                    if (!holder.isLayoutFinish) {
                        rootScrollView.scrollTo(offsetX, 0);
                        holder.isLayoutFinish = true
                    }
                }
        }

        override fun onViewDetachedFromWindow(holder: ViewHolder) {
            super.onViewDetachedFromWindow(holder)

            if (!supportHorizontalScroll) {
                return
            }
            viewHolderListLock.write {
                holder.isLayoutFinish = false
                viewHolderList.remove(holder)
            }
        }

        override fun onViewAttachedToWindow(holder: ViewHolder) {
            super.onViewAttachedToWindow(holder)

            if (!supportHorizontalScroll) {
                return
            }

            viewHolderListLock.write {
                if (!viewHolderList.contains(holder)) {
                    viewHolderList.add(holder)
                }
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            if (!supportHorizontalScroll) {
                return
            }

            viewHolderListLock.write {
                holder.isLayoutFinish = false
                viewHolderList.remove(holder)
            }
        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            if (!supportHorizontalScroll) {
                return
            }
            viewHolderListLock.write {
                viewHolderList.clear()
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


class CustomHorizontalScrollView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    HorizontalScrollView(context, attrs, defStyleAttr) {

    var listener: OnCustomScrollChangeListener? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)


    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        listener?.onCustomScrollChange(this, l, t, oldl, oldt)
    }

    internal fun setOnCustomScrollChangeListener(listener: OnCustomScrollChangeListener) {
        this.listener = listener
    }

    fun interface OnCustomScrollChangeListener {
        fun onCustomScrollChange(
            view: CustomHorizontalScrollView,
            scrollX: Int,
            scrollY: Int,
            oldScrollX: Int,
            oldScrollY: Int
        )
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
