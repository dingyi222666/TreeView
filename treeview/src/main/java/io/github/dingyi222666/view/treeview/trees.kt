package io.github.dingyi222666.view.treeview

import android.util.SparseArray


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
        val rootNode = createRootNodeUseGenerator() ?: TreeNode(
            data = null, depth = 0, name = "Root", id = 0
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

    private suspend fun refreshInternal(node: TreeNode<T>): Set<TreeNode<T>> {
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
