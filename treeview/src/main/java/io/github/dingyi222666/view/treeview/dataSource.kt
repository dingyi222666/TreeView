package io.github.dingyi222666.view.treeview

import android.util.SparseArray
import androidx.core.util.size
import androidx.core.util.valueIterator


interface DataSource<T : Any?> {
    val name: String
    val data: T
    var index: Int
}

interface MultipleDataSourceSupport<T : Any?> {
    fun add(child: DataSource<T>)

    fun remove(child: DataSource<T>)

    fun indexOf(child: DataSource<T>): Int

    fun get(index: Int): DataSource<T>

    fun list(): List<DataSource<T>>

    fun size(): Int
}

private class MultipleDataSourceSupportHandler<T : Any?> : MultipleDataSourceSupport<T> {

    private val childList = SparseArray<DataSource<T>>()

    private var lastIndex = 0

    override fun add(child: DataSource<T>) {
        child.index = ++lastIndex
        childList.append(child.index, child)
    }

    override fun remove(child: DataSource<T>) {
        childList.remove(child.index)
    }

    override fun indexOf(child: DataSource<T>): Int {
        return childList.indexOfValue(child)
    }

    override fun get(index: Int): DataSource<T> {
        return childList.get(index)
    }

    override fun list(): List<DataSource<T>> {
        return childList.valueIterator().asSequence()
            .toList()
    }


    override fun size(): Int {
        return childList.size
    }

}

open class SingleDataSource<T : Any?> internal constructor(
    override val name: String,
    override val data: T
) : DataSource<T> {
    override var index = 0
}


open class MultipleDataSource<T : Any?> internal constructor(
    override val name: String,
    override val data: T,
) : DataSource<T>, MultipleDataSourceSupport<T> by MultipleDataSourceSupportHandler() {
    override var index = 0
}

class DataSourceNodeGenerator<T : Any?>(
    private val rootData: MultipleDataSource<T>
) : TreeNodeGenerator<DataSource<T>> {
    override suspend fun fetchNodeChildData(targetNode: TreeNode<DataSource<T>>): Set<DataSource<T>> {
        return (targetNode.requireData() as MultipleDataSourceSupport<T>).list().toSet()
    }

    override fun createNode(
        parentNode: TreeNode<DataSource<T>>,
        currentData: DataSource<T>,
        tree: AbstractTree<DataSource<T>>
    ): TreeNode<DataSource<T>> {
        return TreeNode(
            data = currentData,
            depth = parentNode.depth + 1,
            name = currentData.name,
            id = tree.generateId(),
            hasChild = if (currentData is MultipleDataSource<T>) {
                currentData.size() > 0
            } else false,
            isChild = currentData is MultipleDataSource<T>,
            expand = false
        )
    }

    override fun createRootNode(): TreeNode<DataSource<T>> {
        return TreeNode(
            data = rootData,
            depth = -1,
            name = rootData.name,
            id = Tree.ROOT_NODE_ID,
            hasChild = true,
            isChild = true,
        )
    }
}