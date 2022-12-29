package io.github.dingyi222666.view.treeview

import android.util.SparseArray
import com.google.common.collect.HashMultimap

class Tree<T : Any> internal constructor() : TreeVisitable<T>, TreeIdGenerator {

    private val allNode = SparseArray<TreeNode<*>>()

    private val allNodeAndChildWithId = HashMultimap.create<Int, Int>()

    lateinit var generator: TreeNodeGenerator<T>

    @Synchronized
    override fun generateId(): Int {
        idx++
        return idx
    }

    fun createRootNode(): TreeNode<*> {
        val rootNode = createRootNodeWithGenerator() ?: DefaultTreeNode(
            extra = null,
            level = 0,
            name = "Root",
            id = 0
        )
        rootNode.hasChild = true
        rootNode.expand = true
        allNode.put(rootNode.id, rootNode)
        return rootNode
    }

    fun createRootNodeWithGenerator(): TreeNode<T>? {
        return generator.createRootNode()
    }

    fun initTree() {
        createRootNode()
    }

    private fun addAllChild(parentNode: TreeNode<*>, currentNodes: Iterable<TreeNode<*>>) {
        parentNode.hasChild = true
        allNode.put(parentNode.id, parentNode)
        currentNodes.forEach {
            allNode.put(it.id, it)
            allNodeAndChildWithId.put(parentNode.id, it.id)
        }
        if (allNodeAndChildWithId.get(parentNode.id).isEmpty()) {
            parentNode.hasChild = false
        }
    }

    private fun addChild(parentNode: TreeNode<*>, currentNode: TreeNode<*>) {
        parentNode.hasChild = true
        allNode.put(currentNode.id, currentNode)
        allNodeAndChildWithId.put(parentNode.id, currentNode.id)
    }

    private fun removeAllChild(currentNode: TreeNode<*>) {
        currentNode.hasChild = false
        allNodeAndChildWithId.removeAll(currentNode.id)
    }

    private fun remove(currentNode: TreeNode<*>) {
        allNode.remove(currentNode.id)
        removeAllChild(currentNode)
    }

    suspend fun getChildNodes(currentNode: TreeNode<*>): Set<Int> {
        return getChildNodes(currentNode.id)
    }

    suspend fun getChildNodes(currentId: Int): Set<Int> {
        refresh(allNode.get(currentId) as TreeNode<T>)
        return allNodeAndChildWithId.get(currentId)
    }


    suspend fun refresh(node: TreeNode<T>): TreeNode<T> {
        val nodeList = generator.refreshNode(node, allNodeAndChildWithId.get(node.id), false, this)
        removeAllChild(node)
        addAllChild(node, nodeList)
        return node
    }


    companion object {
        @get:Synchronized
        @set:Synchronized
        private var idx = 0

        fun <T : Any> createTree(): Tree<T> {
            return Tree()
        }

        const val ROOT_NODE_ID = 0
    }

    override suspend fun visit(visitor: TreeVisitor<T>, fastGet: Boolean) {
        val rootNode = allNode.get(0) as TreeNode<T>

        // visitor.visitRootNode(rootNode)

        val nodeQueue = ArrayDeque<TreeNode<T>>()

        nodeQueue.add(rootNode)



        while (nodeQueue.isNotEmpty()) {
            val currentNode = nodeQueue.removeFirst()

            if (!currentNode.hasChild) {
                visitor.visitLeafNode(currentNode)
                continue
            }

            if (!visitor.visitChildNode(currentNode)) {
                continue
            }

            val children: Set<Int> =
                if (fastGet) allNodeAndChildWithId.get(currentNode.id) else getChildNodes(
                    currentNode.id
                )
            if (children.isNullOrEmpty()) {
                // Leaf node
                continue
            }

            children.sorted().forEach {
                val childNode = allNode.get(it) as TreeNode<T>
               // if (visitor.visitChildNode(childNode)) {
                    nodeQueue.addFirst(childNode)
                //}
            }

        }
    }

    fun getNodes(oldList: Set<Int>): List<TreeNode<T>> {
        return mutableListOf<TreeNode<T>>().apply {
            oldList.forEach {
                add(allNode[it] as TreeNode<T>)
            }
        }
    }
}


interface TreeNodeGenerator<T : Any> {

    // only result child list
    suspend fun refreshNode(
        targetNode: TreeNode<T>,
        oldNodeSet: Set<Int>,
        withChild: Boolean = false,
        tree: Tree<T>
    ): List<TreeNode<T>>

    fun createRootNode(): TreeNode<T>? = null


}

interface TreeIdGenerator {
    fun generateId(): Int
}


interface TreeVisitor<T : Any> {
    fun visitChildNode(node: TreeNode<T>): Boolean
    fun visitLeafNode(node: TreeNode<T>)
    // fun visitRootNode(node: TreeNode<T>): Boolean
}

interface TreeVisitable<T : Any> {
    suspend fun visit(visitor: TreeVisitor<T>, fastGet: Boolean)
}

class DefaultTreeNode(
    extra: Any?,
    level: Int,
    name: String?,
    id: Int
) : TreeNode<Any>(extra, level, name, id)

open class TreeNode<T : Any>(
    var extra: T?,
    var level: Int,
    var name: String?,
    var id: Int,
    var hasChild: Boolean = false,
    var expand: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TreeNode<*>

        if (level != other.level) return false
        if (name != other.name) return false
        if (id != other.id) return false
        if (hasChild != other.hasChild) return false
        if (expand != other.expand) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 31 + level
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + id
        result = 31 * result + hasChild.hashCode()
        result = 31 * result + expand.hashCode()
        return result
    }
}

suspend fun <T : Any> Tree<T>.toSortedList(
    withExpandable: Boolean = true,
    fastGet: Boolean = true
): List<TreeNode<T>> {
    val result = mutableListOf<TreeNode<T>>()
    val visitor = object : TreeVisitor<T> {
        override fun visitChildNode(node: TreeNode<T>): Boolean {
            result.add(node)
            return if (withExpandable) {
                node.expand
            } else {
                true
            }
        }

        override fun visitLeafNode(node: TreeNode<T>) {
            result.add(node)
        }

    }

    visit(visitor, fastGet)

    return result
}