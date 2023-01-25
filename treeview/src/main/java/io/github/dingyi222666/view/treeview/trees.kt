package io.github.dingyi222666.view.treeview

import android.util.SparseArray


/**
 * Default tree structure implementation
 */
class Tree<T : Any> internal constructor() : AbstractTree<T> {

    private val allNode = SparseArray<TreeNode<T>>()

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

    private fun putNode(id: Int, node: TreeNode<T>) {
        allNode.put(id, node)
    }

    private fun putChildNode(nodeId: Int, childNodeId: Int) {
        allNodeAndChild.put(nodeId, childNodeId)
    }

    private fun putNodeAndBindParentNode(node: TreeNode<T>, parentNodeId: Int) {
        putNode(node.id, node)
        putChildNode(parentNodeId, node.id)
    }

    private fun removeAndAddAllChild(node: TreeNode<T>, list: Iterable<TreeNode<T>>) {
        removeAllChild(node)
        addAllChild(node, list)
    }

    private fun getChildNodeForCache(nodeId: Int): Set<Int> {
        return allNodeAndChild[nodeId] ?: emptySet()
    }


    override fun createRootNode(): TreeNode<*> {
        val rootNode = createRootNodeUseGenerator() ?: TreeNode<T>(
            data = null, depth = 0, name = "Root", id = 0
        )
        rootNode.isChild = true
        rootNode.expand = true
        this.rootNode = rootNode
        return rootNode
    }

    private fun addAllChild(parentNode: TreeNode<T>, currentNodes: Iterable<TreeNode<T>>) {
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


    private suspend fun refreshInternal(parentNode: TreeNode<T>): Set<TreeNode<T>> {
        val childNodeCache = getChildNodeForCache(parentNode.id)

        val targetChildNodeList = mutableSetOf<TreeNode<T>>()
        val childNodeData = generator.fetchNodeChildData(parentNode)

        if (childNodeData.isEmpty()) {
            removeAndAddAllChild(parentNode, targetChildNodeList)
            return targetChildNodeList
        }

        val oldNodes = getNodesInternal(childNodeCache)

        for (data in childNodeData) {
            val targetNode =
                oldNodes.find { it.data == data } ?: generator.createNode(parentNode, data, this)
            oldNodes.remove(targetNode)
            targetChildNodeList.add(targetNode)
        }


        removeAndAddAllChild(parentNode, targetChildNodeList)

        return targetChildNodeList
    }

    override suspend fun refresh(node: TreeNode<T>): TreeNode<T> {
        refreshInternal(node)
        return node
    }

    override suspend fun refreshWithChild(node: TreeNode<T>, withExpandable: Boolean): TreeNode<T> {
        val willRefreshNodes = ArrayDeque<TreeNode<T>>()

        willRefreshNodes.add(node)

        while (willRefreshNodes.isNotEmpty()) {
            val currentRefreshNode = willRefreshNodes.removeLast()
            val childNodes = refreshInternal(currentRefreshNode)
            for (childNode in childNodes) {
                if (withExpandable && !childNode.expand) {
                    continue
                }
                willRefreshNodes.addLast(childNode)
            }
        }

        return node
    }

    private fun getNodesInternal(nodeIdList: Set<Int>): MutableList<TreeNode<T>> =
        mutableListOf<TreeNode<T>>().apply {
            nodeIdList.forEach {
                add(getNode(it))
            }
        }

    override fun getNodes(nodeIdList: Set<Int>): List<TreeNode<T>> =
        getNodesInternal(nodeIdList)

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
     * Fetch data from child nodes based on the current node.
     *
     * You will need to fetch the data yourself (which can be asynchronous)
     *
     * @return Data list of the children of the current node.
     */
    suspend fun fetchNodeChildData(targetNode: TreeNode<T>): Set<T>

    /**
     * Given the data and the parent node, create a new node.
     *
     * This method is only called to create new nodes when the tree data structure require it
     *
     * @param [currentData] Need to create node data
     * @param [parentNode] Need to create the parent node of the node
     * @param [tree] Target tree
     */
    fun createNode(parentNode: TreeNode<T>, currentData: T, tree: AbstractTree<T>): TreeNode<T>

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
