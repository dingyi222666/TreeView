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
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.Space
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.activity.enableEdgeToEdge
import com.dingyi.treeview.databinding.ActivityMainBinding
import com.dingyi.treeview.databinding.ItemDirBinding
import com.dingyi.treeview.databinding.ItemFileBinding
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.dingyi222666.view.treeview.AbstractTree
import io.github.dingyi222666.view.treeview.Branch
import io.github.dingyi222666.view.treeview.CreateDataScope
import io.github.dingyi222666.view.treeview.DataSource
import io.github.dingyi222666.view.treeview.Leaf
import io.github.dingyi222666.view.treeview.Tree
import io.github.dingyi222666.view.treeview.TreeNode
import io.github.dingyi222666.view.treeview.TreeNodeEventListener
import io.github.dingyi222666.view.treeview.TreeNodeGenerator
import io.github.dingyi222666.view.treeview.TreeView
import io.github.dingyi222666.view.treeview.TreeViewBinder
import io.github.dingyi222666.view.treeview.buildTree
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var tree: Tree<DataSource<String>>

    private var isSlow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.treeview) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        val tree = createTree()
        this.tree = tree

        (binding.treeview as TreeView<DataSource<String>>).apply {
            supportHorizontalScroll = true
            bindCoroutineScope(lifecycleScope)
            this.tree = tree
            binder = ViewBinder()
            nodeEventListener = binder as ViewBinder
            selectionMode = TreeView.SelectionMode.NONE
        }

        lifecycleScope.launch {
            binding.treeview.refresh()
            //  binding.treeview.expandUntil(1,true)
            //  binding.treeview.expandAll(true)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onPrepareOptionsMenu(menu)
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        println("prepare menu $menu")
        menu.findItem(R.id.drag_node).apply {
            isChecked = binding.treeview.supportDragging
            isEnabled =
                binding.treeview.selectionMode == TreeView.SelectionMode.NONE
        }
        menu.findItem(R.id.select_mode).apply {
            isChecked =
                binding.treeview.selectionMode != TreeView.SelectionMode.NONE
            isEnabled = !binding.treeview.supportDragging
        }
        menu.findItem(R.id.slow_mode).apply {
            isChecked = isSlow
        }
        menu.findItem(R.id.selected_group).apply {
            isEnabled =
                binding.treeview.selectionMode != TreeView.SelectionMode.NONE
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        lifecycleScope.launch {
            when (item.itemId) {
                R.id.collapse_all -> binding.treeview.collapseAll()
                R.id.expand_all -> binding.treeview.expandAll()
                R.id.expand_level -> binding.treeview.expandUntil(2)
                R.id.collapse_level -> binding.treeview.collapseFrom(2)
                R.id.select_all -> selectAllNode()
                R.id.deselect_all -> deselectAllNode()
                R.id.print_selected -> printSelectedNodes()
                R.id.goto_file_activity -> {
                    startActivity(Intent(this@MainActivity, FileActivity::class.java))
                }

                R.id.drag_node -> {
                    binding.treeview.supportDragging = !binding.treeview.supportDragging
                    if (binding.treeview.supportDragging) {
                        binding.treeview.selectionMode = TreeView.SelectionMode.NONE
                    } else {
                        binding.treeview.selectionMode = TreeView.SelectionMode.MULTIPLE
                    }
                    item.isChecked = binding.treeview.supportDragging
                }

                R.id.select_mode -> {
                    binding.treeview.selectionMode =
                        if (binding.treeview.selectionMode == TreeView.SelectionMode.SINGLE) {
                            deselectAllNode()
                            TreeView.SelectionMode.NONE
                        } else {
                            TreeView.SelectionMode.SINGLE
                        }

                    item.isChecked =
                        binding.treeview.selectionMode == TreeView.SelectionMode.SINGLE
                }

                R.id.slow_mode -> {
                    isSlow = !isSlow
                    item.isChecked = isSlow
                }
            }
        }
        return true
    }


    private fun selectAllNode() {
        lifecycleScope.launch {
            binding.treeview.apply {
                // select node and it's children
                selectionMode = TreeView.SelectionMode.MULTIPLE_WITH_CHILDREN
                selectNode(binding.treeview.tree.rootNode, true)
                expandAll()
                selectionMode = TreeView.SelectionMode.MULTIPLE_WITH_CHILDREN
            }
        }
    }

    private fun deselectAllNode() {
        lifecycleScope.launch {
            binding.treeview.apply {
                // select node and it's children
                selectionMode = TreeView.SelectionMode.NONE
            }
        }
    }

    private fun printSelectedNodes() {
        val selectedNodes = (binding.treeview.tree as Tree<DataSource<String>>).getSelectedNodes()
        val showText = StringBuilder()

        selectedNodes.forEach {
            Log.d("LogTest", "hash: ${it.hashCode()}")
            showText.append(it.path).append("\n")
        }

        //println(binding.treeview.tree.resolveNodeFromCache("/app"))

        // Use MaterialAlertDialogBuilder to show selected nodes

        MaterialAlertDialogBuilder(this)
            .setTitle("Selected Nodes")
            .setMessage(showText.toString())
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun createTree(): Tree<DataSource<String>> {
        val dataCreator: CreateDataScope<String> = { _, _ -> UUID.randomUUID().toString() }
        val tree = buildTree(dataCreator) {
            Branch("app") {
                Branch("src") {
                    Branch("main") {
                        Branch("kotlin") {
                            Branch("com.dingyi.treeview") {
                                Leaf("MainActivity.kt")
                            }
                        }
                        Branch("java") {
                            Branch("com.dingyi.treeview") {
                                Leaf("MainActivity.java")
                            }
                        }
                        Branch("res") {
                            Branch("drawable") {

                            }
                            Branch("xml") {}
                        }
                        Leaf("AndroidManifest.xml")
                    }
                    Branch("test") {
                        Branch("java") {
                            Branch("com.dingyi.treeview") {
                                Leaf("ExampleUnitTest.kt")
                            }
                        }
                    }

                }
                Leaf("build.gradle")
                Leaf("gradle.properties")
            }
            Branch("build") {
                Branch("generated") {
                    Branch("source") {
                        Branch("buildConfig") {
                            Branch("debug") {
                                Leaf("com.dingyi.treeview.BuildConfig.java")
                            }
                        }
                    }
                }
                Branch("outputs") {
                    Branch("apk") {
                        Branch("debug") {
                            Leaf("app-debug.apk")
                        }
                    }
                }
            }
        }

        val oldGenerator = tree.generator

        tree.generator = object : TreeNodeGenerator<DataSource<String>> {
            override suspend fun fetchChildData(targetNode: TreeNode<DataSource<String>>): Set<DataSource<String>> {
                if (isSlow) {
                    delay(2000L)
                }
                return oldGenerator.fetchChildData(targetNode)
            }

            override fun createNode(
                parentNode: TreeNode<DataSource<String>>,
                currentData: DataSource<String>,
                tree: AbstractTree<DataSource<String>>
            ): TreeNode<DataSource<String>> {
                return oldGenerator.createNode(parentNode, currentData, tree)
            }

            override suspend fun moveNode(
                srcNode: TreeNode<DataSource<String>>,
                targetNode: TreeNode<DataSource<String>>,
                tree: AbstractTree<DataSource<String>>
            ): Boolean {
                return oldGenerator.moveNode(srcNode, targetNode, tree)
            }
        }

        return tree
    }

    inner class ViewBinder : TreeViewBinder<DataSource<String>>(),
        TreeNodeEventListener<DataSource<String>> {

        override fun createView(parent: ViewGroup, viewType: Int): View {
            return if (viewType == 1) {
                ItemDirBinding.inflate(layoutInflater, parent, false).root
            } else {
                ItemFileBinding.inflate(layoutInflater, parent, false).root
            }
        }

        override fun getItemViewType(node: TreeNode<DataSource<String>>): Int {
            if (node.isChild) {
                return 1
            }
            return 0
        }

        override fun bindView(
            holder: TreeView.ViewHolder,
            node: TreeNode<DataSource<String>>,
            listener: TreeNodeEventListener<DataSource<String>>
        ) {
            if (node.isChild) {
                applyDir(holder, node)
            } else {
                applyFile(holder, node)
            }

            applyDepth(holder, node)

            getCheckableView(node, holder).apply {
                isVisible = node.selected
                isSelected = node.selected
            }
        }

        private fun applyFile(holder: TreeView.ViewHolder, node: TreeNode<DataSource<String>>) {
            val binding = ItemFileBinding.bind(holder.itemView)
            binding.tvName.text = node.name.toString()

        }

        private fun applyDepth(holder: TreeView.ViewHolder, node: TreeNode<DataSource<String>>) {
            val itemView = holder.itemView.findViewById<Space>(R.id.space)

            itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = node.depth * 22.dp
            }
        }

        private fun applyDir(holder: TreeView.ViewHolder, node: TreeNode<DataSource<String>>) {
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
            node: TreeNode<DataSource<String>>,
            holder: TreeView.ViewHolder
        ): MaterialCheckBox {
            return if (node.isChild) {
                ItemDirBinding.bind(holder.itemView).checkbox
            } else {
                ItemFileBinding.bind(holder.itemView).checkbox
            }
        }

        override fun onClick(node: TreeNode<DataSource<String>>, holder: TreeView.ViewHolder) {
            Toast.makeText(this@MainActivity, "Clicked ${node.name}", Toast.LENGTH_LONG).show()
        }

        override fun onMoveView(
            srcHolder: RecyclerView.ViewHolder,
            srcNode: TreeNode<DataSource<String>>,
            targetHolder: RecyclerView.ViewHolder?,
            targetNode: TreeNode<DataSource<String>>?
        ): Boolean {
            applyDepth(srcHolder as TreeView.ViewHolder, srcNode)

            srcHolder.itemView.alpha = 0.7f

            return true
        }

        override fun onMovedView(
            srcNode: TreeNode<DataSource<String>>?,
            targetNode: TreeNode<DataSource<String>>?,
            holder: RecyclerView.ViewHolder
        ) {
            holder.itemView.alpha = 1f
        }

        override fun onToggle(
            node: TreeNode<DataSource<String>>,
            isExpand: Boolean,
            holder: TreeView.ViewHolder
        ) {
            applyDir(holder, node)
        }

        override fun onRefresh(status: Boolean) {
            binding.progress.isVisible = status
        }
    }


}

inline val Int.dp: Int
    get() = (Resources.getSystem().displayMetrics.density * this + 0.5f).toInt()