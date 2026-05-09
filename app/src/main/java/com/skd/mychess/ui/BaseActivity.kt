package com.skd.mychess.ui

import android.app.ProgressDialog
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.skd.mychess.R

open class BaseActivity<T : ViewDataBinding>(@LayoutRes private val layoutResId: Int) :
    AppCompatActivity() {

    open lateinit var binding: T
    private var sProgressDialog: ProgressDialog? = null

    open fun T.initialize() {}


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, layoutResId)
        initializeCircularProgressBar()
        binding.initialize()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                isFragmentExist()
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        isFragmentExist()
        super.onBackPressed()
    }

    private fun isFragmentExist() {
        val backFragmentCount = supportFragmentManager.backStackEntryCount
        if (backFragmentCount == 0 || backFragmentCount == 1) {
            finish()
        }
    }

    private fun initializeCircularProgressBar() {
        sProgressDialog = ProgressDialog(this, R.style.CustomDialogStyle)
//        sProgressDialog?.setIndeterminateDrawable(
//            ContextCompat.getDrawable(this, R.drawable.progressbar_custome)
//        )
        sProgressDialog?.setCancelable(false)
        sProgressDialog?.setProgressStyle(android.R.style.Widget_ProgressBar_Small)
    }

    open fun showProgressBar() {
        sProgressDialog?.show()
    }

    open fun dismissProgressBar() {
        sProgressDialog?.dismiss()
    }
}