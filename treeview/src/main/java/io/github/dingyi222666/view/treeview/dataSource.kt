package io.github.dingyi222666.view.treeview

import android.util.SparseArray
import androidx.core.util.size
import androidx.core.util.valueIterator


interface DataSource<T : Any> {
    val name: String
    val data: T?
    var index: Int
    fun requireData(): T {
        return checkNotNull(data)
    }

}

interface MultipleDataSourceSupport<T : Any> {
    fun add(child: DataSource<T>)

    fun remove(child: DataSource<T>)

    fun indexOf(child: DataSource<T>): Int

    fun get(index: Int): DataSource<T>

    fun list(): List<DataSource<T>>

    fun size(): Int
}

private class MultipleDataSourceSupportHandler<T : Any> : MultipleDataSourceSupport<T> {

    internal val childList = SparseArray<DataSource<T>>()

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

open class SingleDataSource<T : Any> internal constructor(
    override val name: String,
    override val data: T?
) : DataSource<T> {
    override var index = 0
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SingleDataSource<*>

        if (name != other.name) return false
        if (data != other.data) return false
        if (index != other.index) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + index
        return result
    }


}


open class MultipleDataSource<T : Any> internal constructor(
    override val name: String,
    override val data: T?,
) : DataSource<T>, MultipleDataSourceSupport<T> by MultipleDataSourceSupportHandler() {
    override var index = 0
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MultipleDataSource<*>

        if (name != other.name) return false
        if (data != other.data) return false
        if (index != other.index) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + index
        return result
    }

}

class DataSourceNodeGenerator<T : Any>(
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

@DslMarker
annotation class DataSourceMarker

typealias CreateDataScope<T> = (String, DataSource<T>) -> T

@DataSourceMarker
class DataSourceScope<T : Any>(
    internal val currentDataSource: DataSource<T>
) {
    internal var createDataScope: CreateDataScope<T> = { _, _ -> error("Not supproted") }
}

fun <T : Any> DataSourceScope<T>.Branch(
    name: String,
    data: T? = null,
    scope: DataSourceScope<T>.() -> Unit
) {
    val newData = MultipleDataSource(name, data ?: createDataScope.invoke(name, currentDataSource))
    if (currentDataSource is MultipleDataSourceSupport<*>) {
        (currentDataSource as MultipleDataSourceSupport<T>).add(newData)
    }
    val childScope = DataSourceScope(newData)
    childScope.createDataScope = createDataScope
    scope.invoke(childScope)
}


fun <T : Any> DataSourceScope<T>.Leaf(
    name: String,
    data: T? = null
) {
    val newData = SingleDataSource(name, data ?: createDataScope.invoke(name, currentDataSource))
    if (currentDataSource is MultipleDataSourceSupport<*>) {
        (currentDataSource as MultipleDataSourceSupport<T>).add(newData)
    }
}


fun <T : Any> buildTree(
    dataCreator: CreateDataScope<T>? = null,
    scope: DataSourceScope<T>.() -> Unit
): Tree<DataSource<T>> {

    val root = MultipleDataSource<T>("root", null)
    val rootScope = DataSourceScope(root)
    if (dataCreator != null) {
        rootScope.createDataScope = dataCreator
    }
    scope.invoke(rootScope)

    val tree = Tree<DataSource<T>>()

    tree.generator = DataSourceNodeGenerator(root)

    tree.initTree()

    return tree

}

