## TreeView

An TreeView implement in Android with RecyclerView written in kotlin.

## Features

1. 100% written in kotlin.
2. Customise, ~~in the future~~ you can implement your own tree data structure.
3. Fetching data asynchronously, rather than loading it all at once
4. Horizontal scroll support. ~~(with bug)~~

## Screenshot

![output.webp](https://s2.loli.net/2023/01/14/GBwrFcm7xRvWP9O.webp)

## TODO
- [] Select/UnSelect Node
- [] Better TreeNodeGenerator API
- [] More api for operating the Node, e.g. Expand Node, Collapse Node

## Usage

- Introduction Dependency

```groovy
implementation("io.github.dingyi222666:treeview:1.0.4")
```

- First, we need a way to get the data to display, in this case we fake some unreal data

```kotlin

class VirtualFile(
    val name: String,
    val isDir: Boolean
) {

    private lateinit var parent: VirtualFile

    private val child = mutableListOf<VirtualFile>()

    fun addChild(file: VirtualFile): VirtualFile {
        child.add(file)
        file.parent = this
        return this
    }

    fun addChild(vararg virtualFile: VirtualFile): VirtualFile {
        virtualFile.forEach { addChild(it) }
        return this
    }

    fun getChild(): List<VirtualFile> = child

}

private fun repeatCreateVirtualFile(parent: VirtualFile, size: Int): VirtualFile {

    fun create(parentFile: VirtualFile): Pair<VirtualFile, VirtualFile> {
        val inside = VirtualFile("Test5", true)
        return VirtualFile("Test", true)
            .addChild(
                VirtualFile("Test2", false),
                VirtualFile("Test3", false),
                VirtualFile("Test4", false),
                inside
            ).apply {
                parentFile.addChild(this)
            } to inside
    }

    var (currentRootVirtualFile, currentInsideVirtualFile) = create(parent)


    IntRange(1, size).forEach { _ ->
        currentInsideVirtualFile = create(currentInsideVirtualFile).second
    }

    return currentRootVirtualFile
}

fun createVirtualFile(): VirtualFile {
    val root = VirtualFile("app", true)
    root.addChild(
        VirtualFile("src", true)
            .addChild(
                VirtualFile("main", true)
                    .addChild(
                        VirtualFile("java", true)
                            .addChild(
                                VirtualFile("com.dingyi.treeview", true)
                                    .addChild(VirtualFile("MainActivity.kt", false))
                            )
                    ),
                VirtualFile("res", true)
                    .addChild(
                        VirtualFile("layout", true)
                            .addChild(
                                VirtualFile("activity_main", false)
                            )
                    ),
                // Test for horizontal scroll
                /* VirtualFile("test", true).apply {
                     repeatCreateVirtualFile(this, 20)
                 }*/
            ),
    )
    return root
}

```

- Create a new node generator for the transformation of your data to the nodes

```kotlin
inner class NodeGenerator : TreeNodeGenerator<VirtualFile> {

    private val root = createVirtualFile()
    override suspend fun refreshNode(
        targetNode: TreeNode<VirtualFile>,
        oldChildNodeSet: Set<Int>,
        tree: AbstractTree<VirtualFile>,
    ): Set<TreeNode<VirtualFile>> = withContext(Dispatchers.IO) {

        // delay(100)

        val oldNodes = tree.getNodes(oldChildNodeSet)

        val child = checkNotNull(targetNode.data?.getChild()).toMutableSet()

        val result = mutableSetOf<TreeNode<VirtualFile>>()

        oldNodes.forEach { node ->
            val virtualFile = child.find { it.name == node.data?.name }
            if (virtualFile != null) {
                result.add(node)
            }
            child.remove(virtualFile)
        }

        if (child.isEmpty()) {
            return@withContext result
        }

        child.forEach {
            result.add(
                TreeNode(
                    it, targetNode.depth + 1, it.name,
                    tree.generateId(), it.isDir && it.getChild().isNotEmpty(), it.isDir, false
                )
            )
        }

        result
    }

    override fun createRootNode(): TreeNode<VirtualFile> {
        return TreeNode(root, 0, root.name, Tree.ROOT_NODE_ID, true, true)
    }
}

```

- Create a node binder to bind the node to the layout, and in most case also implement node click
  events in this class

  Note: For indenting itemView, we recommend to add a Space to the far left of your layout. The
  width of this space is the width of the indent.

```kotlin
inner class ViewBinder : TreeViewBinder<VirtualFile>(), TreeNodeEventListener<VirtualFile> {

    override fun createView(parent: ViewGroup, viewType: Int): View {
        return if (viewType == 1) {
            ItemDirBinding.inflate(layoutInflater, parent, false).root
        } else {
            ItemFileBinding.inflate(layoutInflater, parent, false).root
        }
    }

    override fun areContentsTheSame(
        oldItem: TreeNode<VirtualFile>,
        newItem: TreeNode<VirtualFile>
    ): Boolean {
        return oldItem == newItem && oldItem.data?.name == newItem.data?.name
    }

    override fun areItemsTheSame(
        oldItem: TreeNode<VirtualFile>,
        newItem: TreeNode<VirtualFile>
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun getItemViewType(node: TreeNode<VirtualFile>): Int {
        if (node.data?.isDir == true) {
            return 1
        }
        return 0
    }

    override fun bindView(
        holder: TreeView.ViewHolder,
        node: TreeNode<VirtualFile>,
        listener: TreeNodeEventListener<VirtualFile>
    ) {
        if (node.isChild) {
            applyDir(holder, node)
        } else {
            applyFile(holder, node)
        }

        val itemView = if (getItemViewType(node) == 1)
            ItemDirBinding.bind(holder.itemView).space
        else ItemFileBinding.bind(holder.itemView).space

        itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = node.depth * 10.dp
        }
    }

    private fun applyFile(holder: TreeView.ViewHolder, node: TreeNode<VirtualFile>) {
        val binding = ItemFileBinding.bind(holder.itemView)
        binding.tvName.text = node.name.toString()
    }

    private fun applyDir(holder: TreeView.ViewHolder, node: TreeNode<VirtualFile>) {
        val binding = ItemDirBinding.bind(holder.itemView)
        binding.tvName.text = node.name.toString()

        binding
            .ivArrow
            .animate()
            .rotation(if (node.expand) 90f else 0f)
            .setDuration(0)
            .start()
    }


    override fun onClick(node: TreeNode<VirtualFile>, holder: TreeView.ViewHolder) {
        if (node.isChild) {
            applyDir(holder, node)
        } 
      
        // Do something when clicked node
    }

    override fun onToggle(
        node: TreeNode<VirtualFile>,
        isExpand: Boolean,
        holder: TreeView.ViewHolder
    ) {
        if (isExpand) {
            applyDir(holder, node)
        }
    }
}
```

- If you want to implement horizontal scrolling, then you may need to do like this

```kotlin
treeview.supportHorizontalScroll = true
```

- Now you can create the tree structure and set up the node generator and node binder for the
  TreeView, then refresh the data

```kotlin
val tree = Tree.createTree<VirtualFile>()

tree.generator = NodeGenerator()
tree.initTree()

(binding.treeview as TreeView<VirtualFile>).apply {
    bindCoroutineScope(lifecycleScope)
    this.tree = tree
    binder = ViewBinder()
    nodeEventListener = binder
}

lifecycleScope.launch {
    binding.treeview.refresh()
}


```

- Done! Enjoy using it.

## Special thanks

- [Rosemoe](https://github.com/Rosemoe) (Help improve the TreeView horizontal scrolling support)
- [HackerMadCat/Multimap](https://github.com/HackerMadCat/Multimap) (Multimap implementation in kotlin)

