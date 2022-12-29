package io.github.dingyi222666.view.treeview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TreeView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    RecyclerView(context, attrs, defStyleAttr), TreeNodeListener {

    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        R.attr.recyclerViewStyle
    )

    lateinit var tree: Tree<Any>

    lateinit var binder: TreeViewBinder<Any>

    private lateinit var _adapter: Adapter

    var treeNodeClickListener: TreeNodeListener = EmptyTreeNodeListener()

    private lateinit var coroutineScope: CoroutineScope

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {}

    private inner class Adapter(val binder: TreeViewBinder<Any>) :
        ListAdapter<TreeNode<*>, ViewHolder>(binder as DiffUtil.ItemCallback<TreeNode<*>>) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(binder.createView(parent, viewType))
        }

        override fun getItemViewType(position: Int): Int {
            return binder.getItemViewType(getItem(position) as TreeNode<Any>)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val listener = this@TreeView
            val node = getItem(position) as TreeNode<Any>
            binder.bindView(holder, node, listener)
            holder.itemView.setOnClickListener {
                listener.onClick(node, holder)
            }
        }

        override fun getItemId(position: Int): Long {
            return getItem(position).id.toLong()
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


    suspend fun refresh() {
        if (!this::_adapter.isInitialized) {
            initAdapter()
        }

        val list = tree.toSortedList(fastGet = false)

        _adapter.submitList(list)

    }

    override fun onClick(node: TreeNode<*>, holder: ViewHolder) {
        if (node.hasChild) {
            onToggle(node, !node.expand, holder)
        }
        treeNodeClickListener.onClick(node, holder)
        coroutineScope.launch {
            tree.refresh(node as TreeNode<Any>)
            refresh()
        }

    }

    override fun onLongClick(node: TreeNode<*>, holder: ViewHolder) {

    }

    override fun onToggle(node: TreeNode<*>, isExpand: Boolean, holder: ViewHolder) {
        node.expand = isExpand
        treeNodeClickListener.onToggle(node, isExpand, holder)
    }


}

abstract class TreeViewBinder<T : Any> : DiffUtil.ItemCallback<TreeNode<T>>() {

    abstract fun createView(parent: ViewGroup, viewType: Int): View

    abstract fun bindView(
        holder: TreeView.ViewHolder,
        node: TreeNode<T>,
        listener: TreeNodeListener
    )

    abstract fun getItemViewType(node: TreeNode<T>): Int

}

class EmptyTreeNodeListener : TreeNodeListener {}

interface TreeNodeListener {
    fun onClick(node: TreeNode<*>, holder: TreeView.ViewHolder) {}
    fun onLongClick(node: TreeNode<*>, holder: TreeView.ViewHolder) {}
    fun onToggle(node: TreeNode<*>, isExpand: Boolean, holder: TreeView.ViewHolder) {}
}
