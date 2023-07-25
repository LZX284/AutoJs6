package org.autojs.autojs.ui.splash

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs6.inrt.R

/**
 * Created by Stardust on 2017/7/7.
 * Modified by SuperMonster003 as of Apr 4, 2023.
 * Transformed by SuperMonster003 on Apr 4, 2023.
 */
class SplashActivity : BaseActivity() {

    private var mAlreadyEnterNextActivity = false
    private var mPaused = false

    private lateinit var mHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_splash)

        @Suppress("DEPRECATION")
        mHandler = Looper.myLooper()?.let { Handler(it) } ?: Handler()
        mHandler.postDelayed({ enterNextActivity() }, INIT_TIMEOUT)

        FloatyWindowManger.hideCircularMenuIfNeeded()
        super.onCreate(savedInstanceState)
    }

    override fun onPause() {
        super.onPause()
        mPaused = true
    }

    override fun onResume() {
        super.onResume()
        if (mPaused) {
            mPaused = false
            enterNextActivity()
        }
    }

    private fun enterNextActivity() {
        if (!mAlreadyEnterNextActivity && !mPaused) {
            mAlreadyEnterNextActivity = true
            MainActivity.launch(this)
            finish()
        }
    }

    companion object {

        private const val INIT_TIMEOUT: Long = 700

    }

}