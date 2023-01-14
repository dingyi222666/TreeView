## TreeView

An TreeView implement in Android with RecyclerView written in kotlin.

## Features

1. 100% written in kotlin.
2. Customise, ~~in the future~~ you can implement your own tree data structure.
3. Fetching data asynchronously, rather than loading it all at once
4. Horizontal scroll support. (with bug)

## Screenshot

![output.webp](https://s2.loli.net/2023/01/14/GBwrFcm7xRvWP9O.webp)


## Usage

- Introduction Dependency

```groovy
implementation("io.github.dingyi222666:treeview:1.0.2")
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
        oldNodeSet: Set<Int>,
        withChild: Boolean,
        tree: AbstractTree<VirtualFile>,
    ): List<TreeNode<VirtualFile>> = withContext(Dispatchers.IO) {
        delay(100)
        val oldNodes = tree.getNodes(oldNodeSet)

        val child = checkNotNull(targetNode.extra?.getChild()).toMutableList()

        val result = mutableListOf<TreeNode<VirtualFile>>()

        oldNodes.forEach { node ->
            val virtualFile = child.find { it.name == node.extra?.name }
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
                    it, targetNode.level + 1, it.name,
                    tree.generateId(), it.isDir && it.getChild().isNotEmpty(), false
                )
            )
        }

        result
    }


    override fun createRootNode(): TreeNode<VirtualFile> {
        return TreeNode(root, 0, root.name, 0, true, true)
    }


}

```

- Create a node binder to bind the node to the layout, and in most cases also implement node click
  events in this class

```kotlin

inner class ViewBinder : TreeViewBinder<VirtualFile>(), TreeNodeListener<VirtualFile> {

    override fun createView(parent: ViewGroup, viewType: Int): View {
        if (viewType == 1) {
            return ItemDirBinding.inflate(layoutInflater, parent, false).root
        } else {
            return ItemFileBinding.inflate(layoutInflater, parent, false).root
        }
    }

    override fun areContentsTheSame(
        oldItem: TreeNode<VirtualFile>,
        newItem: TreeNode<VirtualFile>
    ): Boolean {
        return oldItem == newItem && oldItem.extra?.name == newItem.extra?.name
    }

    override fun areItemsTheSame(
        oldItem: TreeNode<VirtualFile>,
        newItem: TreeNode<VirtualFile>
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun getItemViewType(node: TreeNode<VirtualFile>): Int {
        if (node.extra?.isDir == true) {
            return 1
        }
        return 0
    }

    override fun bindView(
        holder: TreeView.ViewHolder,
        node: TreeNode<VirtualFile>,
        listener: TreeNodeListener<VirtualFile>
    ) {
        if (node.hasChild) {
            applyDir(holder, node)
        } else {
            applyFile(holder, node)
        }
        /*val itemView = if (getItemViewType(node) == 1)
            ItemDirBinding.bind(holder.currentItemView).space
        else ItemFileBinding.bind(holder.currentItemView).space
        itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = node.level * 10.dp
        }

        val itemView2 = if (getItemViewType(node) == 1)
            ItemDirBinding.bind(holder.currentItemView).spaceRight
        else ItemFileBinding.bind(holder.currentItemView).spaceRight
        itemView2.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = this@MainActivity.resources.displayMetrics.widthPixels
        }*/
        holder.currentItemView.updatePadding(
            top = 0,
            right = 0,
            bottom = 0,
            left = node.level * 10.dp
        )

    }

    private fun applyFile(holder: TreeView.ViewHolder, node: TreeNode<VirtualFile>) {
        val binding = ItemFileBinding.bind(holder.currentItemView)
        binding.tvName.text = node.name.toString()

    }

    private fun applyDir(holder: TreeView.ViewHolder, node: TreeNode<VirtualFile>) {
        val binding = ItemDirBinding.bind(holder.currentItemView)
        binding.tvName.text = node.name.toString()

        binding
            .ivArrow
            .animate()
            .rotation(if (node.expand) 90f else 0f)
            .setDuration(0)
            .start()
    }


    override fun onClick(node: TreeNode<VirtualFile>, holder: TreeView.ViewHolder) {
        if (node.hasChild) {
            applyDir(holder, node)
        } else {
            applyFile(holder, node)
        }
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

    1. You may need to use two placeholder Views, one to display the blank left margin and the right
       View to expand the width of the itemView so it can be scrolled
    2. Modify your bindView code to something like the following implementation

```kotlin

val itemView = if (getItemViewType(node) == 1)
    ItemDirBinding.bind(holder.currentItemView).space
else ItemFileBinding.bind(holder.currentItemView).space
itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
    width = node.level * 10.dp
}

itemView = if (getItemViewType(node) == 1)
    ItemDirBinding.bind(holder.currentItemView).spaceRight
else ItemFileBinding.bind(holder.currentItemView).spaceRight

itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
    // Set to screen width to ensure it can be scrolled
    width = this@MainActivity.resources.displayMetrics.widthPixels
}

```

- Now you can create the tree structure and set up the node generator and node binder for the TreeView, then refresh the data

```kotlin

val tree = Tree.createTree<VirtualFile>()

tree.generator = NodeGenerator()

tree.initTree()

binding.treeview.apply {
    // horizontalScroll support, default is false
    supportHorizontalScroll = true
    bindCoroutineScope(lifecycleScope)
    this.tree = tree as Tree<Any>
    binder = ViewBinder() as TreeViewBinder<Any>
    nodeClickListener = binder
}

lifecycleScope.launch {
    binding.treeview.refresh()
}


```

### Tips

This library is still an experimental library and we do not recommend using it in a production environment until it is fully developed.