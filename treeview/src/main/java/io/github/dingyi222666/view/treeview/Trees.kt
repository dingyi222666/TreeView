package io.github.dingyi222666.view.treeview

import android.util.SparseArray
import androidx.annotation.CallSuper

/**
 * Abstract interface for tree structure.
 *
 * You can implement your own tree structure.
 */
interface AbstractTree<T : Any> : TreeVisitable<T>, TreeIdGenerator {

    /**
     * Node generator.
     *
     * The object is required for each tree structure so that the node data can be retrieved.
     *
     * @see [TreeNodeGenerator]
     */
    var generator: TreeNodeGenerator<T>


    /**
     * Root node.
     *
     * All data for the tree structure is obtained based on the root node and the node generator.
     */
    var rootNode: TreeNode<T>

    /**
     * Create the root node.
     *
     * This method is called first when the init tree is created.
     *
     * In this method you need to call the [createRootNodeUseGenerator] method first
     * and then create an empty root node if the [TreeNodeGenerator] does not return a root node.
     *
     * @see [createRootNodeUseGenerator]
     * @see [TreeNodeGenerator]
     */
    fun createRootNode(): TreeNode<*>

    /**
     * Use [TreeNodeGenerator] to create the root node.
     * This method will actually call [TreeNodeGenerator.createRootNode]
     *
     * @see [TreeNodeGenerator.createRootNode]
     */
    fun createRootNodeUseGenerator(): TreeNode<T>? {
        return generator.createRootNode()
    }

    /**
     * Initializing the tree.
     *
     * Subclass overrides can do something when that method is called.
     *
     * Note: Before initializing the tree, make sure that the node generator is set up.
     */
    @CallSuper
    fun initTree() {
        createRootNode()
    }

    /**
     * Get the list of children of the current node.
     *
     * This method returns a list of the ids of the child nodes,
     * you may need to do further operations to get the list of child nodes
     *
     * @param currentNode current node
     * @return List of id of child nodes
     *
     * @see [TreeNodeGenerator]
     */
    suspend fun getChildNodes(currentNode: TreeNode<*>): Set<Int>

    /**
     * Get the child list of the current node pointed to by the id.
     *
     * This method returns a list of the ids of the child nodes,
     * you may need to do further operations to get the list of child nodes
     *
     * @param currentNodeId Need to get the id of a node in the child node list
     * @return List of id of child nodes
     *
     * @see [TreeNodeGenerator]
     */
    suspend fun getChildNodes(currentNodeId: Int): Set<Int>

    /**
     * Refresh the current node.
     *
     * Refresh the node, this will update the list of children of the current node.
     *
     * Note: Refreshing a node does not update all the children under the node, the method will only update one level (the children under the node). You can call this method repeatedly if you need to update all the child nodes
     *
     * @see [TreeNodeGenerator]
     */
    suspend fun refresh(node: TreeNode<T>): TreeNode<T>

    /**
     * Refresh the current node and it‘s child.
     *
     * Refreshing the current node and also refreshes its children.
     *
     * @param [withExpandable] Whether to refresh only the expanded child nodes, otherwise all will be refreshed.
     *
     * @see [TreeNodeGenerator]
     */
    suspend fun refreshWithChild(node: TreeNode<T>, withExpandable: Boolean = true): TreeNode<T>


    /**
     * Get the list of node from the given list of id
     */
    fun getNodes(nodeIdList: Set<Int>): List<TreeNode<T>>

    /**
     * Get node pointed to from id
     */
    fun getNode(id: Int): TreeNode<T>

}

/**
 * Default tree structure implementation
 */
class Tree<T : Any> internal constructor() : AbstractTree<T> {

    private val allNode = SparseArray<TreeNode<*>>()

    private val allNodeAndChild = HashMultimap<Int, Int, HashSet<Int>> {
        HashSet()
    }

    private lateinit var _rootNode: TreeNode<T>

    override var rootNode: TreeNode<T>
        set(value) {
            if (this::_rootNode.isInitialized) {
                removeNode(_rootNode.id)
            }
            _rootNode = value
            putNode(value.id, value)
        }
        get() = _rootNode

    override lateinit var generator: TreeNodeGenerator<T>

    @Synchronized
    override fun generateId(): Int {
        idx++
        return idx
    }

    private fun removeAllChildNode(currentNodeId: Int) {
        allNodeAndChild.remove(currentNodeId)
    }

    private fun removeNode(currentNodeId: Int) {
        allNode.remove(currentNodeId)
        removeAllChildNode(currentNodeId)
    }

    private fun putNode(id: Int, node: TreeNode<*>) {
        allNode.put(id, node)
    }

    private fun putChildNode(nodeId: Int, childNodeId: Int) {
        allNodeAndChild.put(nodeId, childNodeId)
    }

    private fun putNodeAndBindParentNode(node: TreeNode<*>, parentNodeId: Int) {
        putNode(node.id, node)
        putChildNode(parentNodeId, node.id)
    }

    private fun getChildNodeForCache(nodeId: Int): Set<Int> {
        return allNodeAndChild[nodeId] ?: emptySet()
    }


    override fun createRootNode(): TreeNode<*> {
        val rootNode = createRootNodeUseGenerator() ?: DefaultTreeNode(
            extra = null, level = 0, name = "Root", id = 0
        )
        rootNode.isChild = true
        rootNode.expand = true
        this.rootNode = rootNode as TreeNode<T>
        return rootNode
    }


    private fun addAllChild(parentNode: TreeNode<*>, currentNodes: Iterable<TreeNode<*>>) {
        parentNode.isChild = true
        parentNode.hasChild = true

        putNode(parentNode.id, parentNode)

        currentNodes.forEach {
            putNodeAndBindParentNode(it, parentNode.id)
        }

        if (getChildNodeForCache(parentNode.id).isEmpty()) {
            parentNode.hasChild = false
        }
    }

    private fun removeAllChild(currentNode: TreeNode<*>) {
        currentNode.hasChild = false

        removeAllChildNode(currentNode.id)
    }

    override suspend fun getChildNodes(currentNode: TreeNode<*>): Set<Int> {
        return getChildNodes(currentNode.id)
    }

    override suspend fun getChildNodes(currentNodeId: Int): Set<Int> {
        refresh(getNode(currentNodeId))
        return getChildNodeForCache(currentNodeId)
    }

    override fun getNode(id: Int): TreeNode<T> {
        return allNode.get(id) as TreeNode<T>
    }

    internal suspend fun refreshInternal(node: TreeNode<T>): Set<TreeNode<T>> {
        val nodeList = generator.refreshNode(node, getChildNodeForCache(node.id), this)
        removeAllChild(node)
        addAllChild(node, nodeList)
        return nodeList
    }

    override suspend fun refresh(node: TreeNode<T>): TreeNode<T> {
        refreshInternal(node)
        return node
    }

    override suspend fun refreshWithChild(node: TreeNode<T>, withExpandable: Boolean): TreeNode<T> {
        val willRefreshNodes = ArrayDeque<TreeNode<T>>()

        willRefreshNodes.add(node)

        while (willRefreshNodes.isNotEmpty()) {
            val currentRefreshNode = willRefreshNodes.removeFirst()
            val childNodes = refreshInternal(currentRefreshNode)
            for (childNode in childNodes) {
                if (withExpandable && !childNode.expand) {
                    continue
                }
                willRefreshNodes.add(childNode)
            }
        }

        return node
    }


    companion object {
        @get:Synchronized
        @set:Synchronized
        private var idx = 0

        /**
         * Create a new tree structure to store data of type [T]
         */
        fun <T : Any> createTree(): Tree<T> {
            return Tree()
        }

        /**
         * The root node ID, we recommend using this ID to mark the root node
         */
        const val ROOT_NODE_ID = 0
    }

    override suspend fun visit(visitor: TreeVisitor<T>, fastVisit: Boolean) {
        val rootNode = rootNode

        val nodeQueue = ArrayDeque<TreeNode<T>>()

        nodeQueue.add(rootNode)

        while (nodeQueue.isNotEmpty()) {
            val currentNode = nodeQueue.removeFirst()

            if (!currentNode.isChild) {
                visitor.visitLeafNode(currentNode)
                continue
            }

            if (!visitor.visitChildNode(currentNode)) {
                continue
            }

            val children = if (fastVisit)
                getChildNodeForCache(currentNode.id)
            else getChildNodes(currentNode.id)

            if (children.isEmpty()) {
                continue
            }

            children.sortedDescending().forEach {
                val childNode = getNode(it)
                nodeQueue.addFirst(childNode)
            }

        }
    }

    override fun getNodes(nodeIdList: Set<Int>): List<TreeNode<T>> {
        return mutableListOf<TreeNode<T>>().apply {
            nodeIdList.forEach {
                add(getNode(it))
            }
        }
    }
}

/**
 * The node generator is the correlate for the generation of child nodes (the current node).
 *
 * The tree structure allows access to node data through this.
 *
 *
 *
 * @param T data type stored in node
 */
interface TreeNodeGenerator<T : Any> {

    /**
     * Refreshes the (child) data of the node.
     *
     * Implement this method to refresh the child node data of the [targetNode].
     *
     * You will need to fetch the data yourself (which can be asynchronous) and
     * convert them into a list of child nodes to return
     *
     * @param [targetNode] Need to get target node data for child nodes (incoming node)
     * @param [oldChildNodeSet] A list of old child nodes.
     *
     * You need to compare the fetched data with the old list of child nodes to determine if the data represented by certain child nodes still exists.
     *
     * If they exist, then you need to add the old nodes to the return list.
     *
     * @param [tree] Target tree
     *
     * @return List of child nodes of the target node
     */
    suspend fun refreshNode(
        targetNode: TreeNode<T>, oldChildNodeSet: Set<Int>, tree: AbstractTree<T>
    ): Set<TreeNode<T>>

    /**
     * Create a root node.
     *
     * In anyway, each tree will have a root node,
     * and then the node generator generates its children based on this root node.
     *
     * If you want to have (visual) multiple root node support,
     * you can set the root node level to less than 0.
     */
    fun createRootNode(): TreeNode<T>? = null

}

/**
 * The node id generator is used to generate an id as a unique token for each node.
 */
interface TreeIdGenerator {

    /**
     * Generate id
     */
    fun generateId(): Int
}

/**
 * Tree visitor.
 *
 * The tree structure receive the object and then start accessing the object from
 * the root node on down (always depth first access)
 *
 * @param T The type of data stored in the tree to be visited
 *
 * @see [TreeVisitable]
 */
interface TreeVisitor<T : Any> {
    /**
     * Visit a child node.
     *
     * The tree structure call this method to notify the object that the child node was visited.
     *
     * @return Does it continue to access the child nodes under this node
     */
    fun visitChildNode(node: TreeNode<T>): Boolean


    /**
     * Visit a leaf node.
     *
     * The tree structure call this method to notify the object that the leaf node was visited.
     */
    fun visitLeafNode(node: TreeNode<T>)
}

/**
 * TreeVisitable.
 *
 * Each tree structure needs to implement this interface to enable access to the tree.
 *
 * @see [TreeVisitor]
 */
interface TreeVisitable<T : Any> {
    /**
     * Visit the tree.
     * The tree structure implement this to enable access to the tree.
     *
     * This method is a suspend function as it needs to fetch node data from the node generator.
     *
     * It can be called to visit a tree.
     *
     * @param [visitor] Tree visitor
     * @param [fastVisit] Whether to have quick access.
     *
     * If the value is true, then the node data will be fetched from the cache instead of
     * calling the node generator to fetch the node data.
     */
    suspend fun visit(visitor: TreeVisitor<T>, fastVisit: Boolean = false)
}

class DefaultTreeNode(
    extra: Any?, level: Int, name: String?, id: Int
) : TreeNode<Any>(extra, level, name, id)

/**
 * Node data class.
 *
 * Represents a node that hold relevant data.
 *
 * @param T The type of data stored in the node
 */
open class TreeNode<T : Any>(
    /**
     * The data in the node.
     *
     * This data can be provided to the [TreeViewBinder] to bind data to the view.
     */
    var data: T?,

    /**
     * The depth of the node.
     *
     * This value represents the distance of the node relative to the root node.
     *
     * Note: this value can be negative.
     *
     * if it is negative we recommend that you discard the node when visiting the tree,
     * as it is likely to be a non-normal node.
     *
     * For example, a root node of -1 and its children nodes of 0 could be used to implement a visual multiple root node
     */
    var depth: Int,

    /**
     * The name of the node.
     *
     * The  [TreeViewBinder] use this to display the name of the node on the view.
     */
    var name: String?,

    /**
     * The ID of the node.
     *
     * The node's unique and most trusted identifier.
     */
    var id: Int,

    /**
     * Whether the node contains child nodes.
     */
    var hasChild: Boolean = false,

    /**
     * Whether the node is a child node
     */
    var isChild: Boolean = false,

    /**
     * Whether the node is expanded or not.
     *
     * The TreeView checks this value when displaying the list of nodes and decides whether or not to display the child nodes under the node
     */
    var expand: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TreeNode<*>

        if (depth != other.depth) return false
        if (name != other.name) return false
        if (id != other.id) return false
        if (hasChild != other.hasChild) return false
        if (isChild != other.isChild) return false
        if (expand != other.expand) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 31 + depth
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + id
        result = 31 * result + hasChild.hashCode()
        result = 31 * result + expand.hashCode()
        return result
    }

    /**
     * Return [data] that is not null.
     *
     * At some time, you may have made it clear that [data] is not non-null,
     * then you can call this method. If data is still null, then it will throw an exception.
     *
     * @return non-null [data]
     */
    fun requireData(): T {
        return checkNotNull(data)
    }
}

/**
 * Convert the node data in a tree structure into an ordered list.
 *
 * @param withExpandable Whether to add collapsed nodes
 * @param fastVisit Quick visit to the tree structure or not
 *
 * @see AbstractTree
 * @see TreeVisitor
 * @see TreeVisitable
 */
suspend fun <T : Any> AbstractTree<T>.toSortedList(
    withExpandable: Boolean = true, fastVisit: Boolean = true
): List<TreeNode<T>> {
    val result = mutableListOf<TreeNode<T>>()

    val visitor = object : TreeVisitor<T> {
        override fun visitChildNode(node: TreeNode<T>): Boolean {
            if (node.depth >= 0) {
                result.add(node)
            }
            return if (withExpandable) {
                node.expand
            } else {
                true
            }
        }

        override fun visitLeafNode(node: TreeNode<T>) {
            if (node.depth >= 0) {
                result.add(node)
            }
        }

    }

    visit(visitor, fastVisit)

    return result
}