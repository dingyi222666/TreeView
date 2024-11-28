/*
 * TreeView - An TreeView implement in Android with RecyclerView written in kotlin.
 *  https://github.com/dingyi222666/TreeView
 *
 * Copyright (C) 2023-2024. dingyi222666@foxmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dingyi.treeview

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.Space
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.dingyi.treeview.databinding.ActivityMainBinding
import com.dingyi.treeview.databinding.ItemDirBinding
import com.dingyi.treeview.databinding.ItemFileBinding
import com.google.android.material.checkbox.MaterialCheckBox
import io.github.dingyi222666.view.treeview.AbstractTree
import io.github.dingyi222666.view.treeview.DataSource
import io.github.dingyi222666.view.treeview.Tree
import io.github.dingyi222666.view.treeview.TreeNode
import io.github.dingyi222666.view.treeview.TreeNodeEventListener
import io.github.dingyi222666.view.treeview.TreeNodeGenerator
import io.github.dingyi222666.view.treeview.TreeView
import io.github.dingyi222666.view.treeview.TreeViewBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class FileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var fileListLoader: FileListLoader

    private lateinit var tree: Tree<File>

    private var hasStoragePermission = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        fileListLoader = FileListLoader()
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.treeview) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        requestStoragePermission()

        if (!hasStoragePermission) {
            Toast.makeText(
                this,
                "No storage permission, if you granted, please restart the app",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        tree = createTree()

        (binding.treeview as TreeView<File>).apply {
            supportHorizontalScroll = true
            bindCoroutineScope(lifecycleScope)
            this.tree = this@FileActivity.tree
            binder = FileViewBinder()
            nodeEventListener = binder as FileViewBinder
            selectionMode = TreeView.SelectionMode.MULTIPLE_WITH_CHILDREN
        }

        lifecycleScope.launch {
            fileListLoader.loadFileList(Environment.getExternalStorageDirectory().absolutePath)
            binding.treeview.refresh()
        }


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onPrepareOptionsMenu(menu)
        menuInflater.inflate(R.menu.file, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        lifecycleScope.launch {
            when (item.itemId) {
                R.id.delete_dir -> {
                    // need load root dir

                    fileListLoader.removeFileInCache(
                        Environment.getExternalStorageDirectory().resolve("Download")
                    )
                    Log.d(
                        "LogTest",
                        "delete dir: ${
                            Environment.getExternalStorageDirectory()
                                .resolve("Download").absolutePath
                        }"
                    )
                    binding.treeview.refresh()
                }

                R.id.reload_all -> {
                    fileListLoader.removeFileInCache(
                        Environment.getExternalStorageDirectory()
                    )
                    Log.d("LogTest", "reload all: $fileListLoader")
                    binding.treeview.refresh()
                }
            }
        }
        return true
    }


    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT < 23) {
            return
        }

        if (Build.VERSION.SDK_INT < 30) {

            // check write and read permission

            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {

                Toast.makeText(
                    this,
                    "Please grant storage permission to continue",
                    Toast.LENGTH_SHORT
                ).show()

                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    ), 1
                )
            } else {
                hasStoragePermission = true
            }
        } else {
            // check write and read permission

            if (!Environment.isExternalStorageManager()) {
                val uri = Uri.parse("package:com.dingyi.treeview")

                Toast.makeText(
                    this,
                    "Please grant storage permission to continue",
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        uri
                    )
                )
            } else {
                hasStoragePermission = true
            }
        }
    }

    private fun createTree(): Tree<File> {
        val tree = Tree.createTree<File>()


        tree.apply {
            this.generator = FileNodeGenerator(
                File(Environment.getExternalStorageDirectory().absolutePath),
                fileListLoader
            )

            initTree()
        }

        return tree
    }


    inner class FileViewBinder : TreeViewBinder<File>(),
        TreeNodeEventListener<File> {

        override fun createView(parent: ViewGroup, viewType: Int): View {
            val layoutInflater = LayoutInflater.from(parent.context)
            return if (viewType == 1) {
                ItemDirBinding.inflate(layoutInflater, parent, false).root
            } else {
                ItemFileBinding.inflate(layoutInflater, parent, false).root
            }
        }

        override fun getItemViewType(node: TreeNode<File>): Int {
            if (node.isChild) {
                return 1
            }
            return 0
        }

        override fun bindView(
            holder: TreeView.ViewHolder,
            node: TreeNode<File>,
            listener: TreeNodeEventListener<File>
        ) {
            if (node.isChild) {
                applyDir(holder, node)
            } else {
                applyFile(holder, node)
            }

            val itemView = holder.itemView.findViewById<Space>(R.id.space)

            itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = node.depth * 22.dp
            }

            (getCheckableView(node, holder)).apply {
                isVisible = node.selected
                isSelected = node.selected
            }
        }

        private fun applyFile(holder: TreeView.ViewHolder, node: TreeNode<File>) {
            val binding = ItemFileBinding.bind(holder.itemView)
            binding.tvName.text = node.name.toString()

        }

        private fun applyDir(holder: TreeView.ViewHolder, node: TreeNode<File>) {
            val binding = ItemDirBinding.bind(holder.itemView)
            binding.tvName.text = node.name.toString()

            binding
                .ivArrow
                .animate()
                .rotation(if (node.expand) 90f else 0f)
                .setDuration(200)
                .start()

        }

        override fun getCheckableView(
            node: TreeNode<File>,
            holder: TreeView.ViewHolder
        ): MaterialCheckBox {
            return if (node.isChild) {
                ItemDirBinding.bind(holder.itemView).checkbox
            } else {
                ItemFileBinding.bind(holder.itemView).checkbox
            }
        }

        override fun onClick(node: TreeNode<File>, holder: TreeView.ViewHolder) {
            Toast.makeText(holder.itemView.context, "Clicked ${node.name}", Toast.LENGTH_LONG)
                .show()
        }

        override fun onRefresh(status: Boolean) {
            binding.progress.isVisible = status
        }

        override fun onToggle(
            node: TreeNode<File>,
            isExpand: Boolean,
            holder: TreeView.ViewHolder
        ) {
            applyDir(holder, node)
        }
    }
}

class FileNodeGenerator(
    private val rootPath: File,
    private val fileListLoader: FileListLoader
) : TreeNodeGenerator<File> {
    override suspend fun fetchChildData(targetNode: TreeNode<File>): Set<File> {
        val path = targetNode.requireData().absolutePath
        var files = fileListLoader.getCacheFileList(path)

        if (files.isEmpty()) {
            files = fileListLoader.loadFileList(path)
        }

        return files.toSet()
    }

    override fun createNode(
        parentNode: TreeNode<File>,
        currentData: File,
        tree: AbstractTree<File>
    ): TreeNode<File> {
        return TreeNode(
            data = currentData,
            depth = parentNode.depth + 1,
            name = currentData.name,
            id = tree.generateId(),
            hasChild = currentData.isDirectory && fileListLoader.getCacheFileList(currentData.absolutePath)
                .isNotEmpty(),
            isChild = currentData.isDirectory,
            expand = false
        )

    }

    override fun createRootNode(): TreeNode<File> {
        return TreeNode(
            data = rootPath,
            depth = -1,
            name = rootPath.name,
            id = Tree.ROOT_NODE_ID,
            hasChild = true,
            isChild = true,
        )
    }

}

class FileListLoader {

    private val cacheFiles = mutableMapOf<String, MutableList<File>>()

    private fun getFileList(file: File): List<File> {
        return (file.listFiles() ?: emptyArray()).run {
            sortedWith { o1, o2 ->
                if (o1.isDirectory && o2.isFile) {
                    -1
                } else if (o1.isFile && o2.isDirectory) {
                    1
                } else {
                    o1.name.compareTo(o2.name)
                }
            }
        }
    }

    suspend fun loadFileList(path: String) = withContext(Dispatchers.IO) {
        val result = cacheFiles[path] ?: run {
            val files = getFileList(File(path))
            cacheFiles[path] = files.toMutableList()

            // load sub directory, but only load one level
            files.forEach {
                if (it.isDirectory) {
                    cacheFiles[it.absolutePath] = getFileList(it).toMutableList()
                }
            }

            files.toMutableList()
        }

        result
    }

    fun getCacheFileList(path: String) = cacheFiles[path] ?: emptyList()

    fun removeFileInCache(currentFile: File): Boolean {
        if (currentFile.isDirectory) {
            cacheFiles.remove(currentFile.absolutePath)
        }

        val parent = currentFile.parentFile
        val parentPath = parent?.absolutePath
        val parentFiles = cacheFiles[parentPath]
        return parentFiles?.remove(currentFile) ?: false
    }


    override fun toString(): String {
        return "FileListLoader(cacheFiles=$cacheFiles)"
    }
}