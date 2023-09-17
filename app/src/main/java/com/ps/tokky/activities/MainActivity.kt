package com.ps.tokky.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ps.tokky.R
import com.ps.tokky.activities.transfer.export.ExportActivity
import com.ps.tokky.activities.transfer.imports.ImportActivity
import com.ps.tokky.activities.transfer.imports.ImportActivity.Companion.INTENT_EXTRA_KEY_FILE_PATH
import com.ps.tokky.databinding.ActivityMainBinding
import com.ps.tokky.databinding.BottomSheetTransferAccountsBinding
import com.ps.tokky.utils.Constants.FILE_MIME_TYPE
import com.ps.tokky.utils.DividerItemDecorator
import com.ps.tokky.utils.TokenAdapter

class MainActivity : BaseActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val adapter: TokenAdapter by lazy {
        TokenAdapter(this, binding.recyclerView, addNewActivityLauncher)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Block screenshots
        if (!preferences.allowScreenshots) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val layoutManager = LinearLayoutManager(this)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        val divider =
            DividerItemDecorator(ResourcesCompat.getDrawable(resources, R.drawable.divider, null))
        binding.recyclerView.addItemDecoration(divider)
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        binding.fabAddNew.setOnClickListener {
            addNewActivityLauncher.launch(Intent(this, EnterKeyDetailsActivity::class.java))
        }

        binding.fabAddNew.setOnLongClickListener {
            addNewActivityLauncher.launch(Intent(this, CameraScannerActivity::class.java))
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!adapter.editModeEnabled) {
                    finish()
                    return
                }
                openEditMode(false)
            }
        })
        refresh(true)
    }

    fun refresh(reload: Boolean) {
        val list = db.getAll(reload)
        adapter.updateEntries(list)
        binding.emptyLayout.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        invalidateOptionsMenu()
    }

    override fun onResume() {
        super.onResume()
        adapter.onResume()
    }

    override fun onPause() {
        super.onPause()
        adapter.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (!adapter.editModeEnabled) {
            menuInflater.inflate(R.menu.menu_main, menu)
            menu?.findItem(R.id.menu_main_edit)?.isEnabled = db.getAll(false).isNotEmpty()
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                openEditMode(false)
                return true
            }
            R.id.menu_main_refresh -> {
                refresh(true)
                return true
            }
            R.id.menu_main_edit -> {
                openEditMode(!adapter.editModeEnabled)
                return true
            }
            R.id.menu_main_transfer_accounts -> {
                val dialogBinding = BottomSheetTransferAccountsBinding.inflate(layoutInflater)
                val dialog = BottomSheetDialog(this)
                dialog.setContentView(dialogBinding.root)

                dialogBinding.btnExportAccounts.setOnClickListener {
                    dialog.dismiss()
                    startActivity(Intent(this@MainActivity, ExportActivity::class.java))
                }

                dialogBinding.btnImportAccounts.setOnClickListener {
                    dialog.dismiss()

                    val filePickerIntent = Intent().apply {
                        type = FILE_MIME_TYPE
                        action = Intent.ACTION_GET_CONTENT
                    }

                    readFileLauncher.launch(filePickerIntent)
                }

                dialog.show()
            }
            R.id.menu_main_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun openEditMode(open: Boolean) {
        invalidateOptionsMenu()
        if (open) {
            binding.toolbar.title = getString(R.string.edit_mode_title)
            binding.toolbar.navigationIcon =
                ResourcesCompat.getDrawable(resources, R.drawable.ic_close, theme)
            adapter.editModeEnabled = true
            binding.fabAddNew.hide()
        } else {
            binding.toolbar.title = getString(R.string.app_name)
            binding.toolbar.navigationIcon = null
            adapter.editModeEnabled = false
            binding.fabAddNew.show()

            binding.emptyLayout.visibility =
                if (db.getAll(false).isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private val addNewActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val extras = it.data?.extras
            if (it.resultCode == Activity.RESULT_OK && extras != null) {
                refresh(true)
            }
        }

    private val readFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val filePath = it.data?.data
            if (filePath != null) {
                importAccountsLauncher.launch(
                    Intent(this, ImportActivity::class.java)
                        .putExtra(INTENT_EXTRA_KEY_FILE_PATH, filePath.toString())
                )
            }
        }

    private val importAccountsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refresh(true)
        }

    companion object {
        private const val TAG = "MainActivity"
    }
}