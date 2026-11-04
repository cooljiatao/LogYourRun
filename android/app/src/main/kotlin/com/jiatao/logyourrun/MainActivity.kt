package com.jiatao.logyourrun

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  private lateinit var webView: WebView
  private lateinit var splashView: ImageView
  private lateinit var backupBridge: BackupBridge
  private val scope = MainScope()
  private var splashJob: Job? = null

  /** 页面是否已加载完（用来决定闪屏能否提前结束） */
  @Volatile private var pageReady = false

  /** 上一次按下返回键的时间，用于双击退出判断 */
  private var lastBackPressedTime = 0L

  companion object {
    /** 双击退出 App 的判定间隔 */
    private const val EXIT_INTERVAL_MS = 1000L

    /** 闪屏至少停留时长 */
    private const val SPLASH_DURATION_MS = 3000L
  }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    backupBridge = BackupBridge(this)
    splashView = createSplashView()
    webView = createWebView()

    // WebView 先加载但不显示，闪屏盖在上面
    webView.visibility = View.GONE
    setContentView(
      FrameLayout(this).apply {
        addView(webView)
        addView(splashView)
      }
    )

    webView.loadUrl(BuildConfig.API_URL)

    // 从 onCreate 这一刻开始计时：3 秒到且页面已加载完才收起闪屏
    splashJob =
      scope.launch {
        delay(SPLASH_DURATION_MS)
        while (!pageReady) delay(50)
        dismissSplash()
      }

    // 处理返回键事件
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (webView.canGoBack()) {
            webView.goBack()
          } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressedTime <= EXIT_INTERVAL_MS) {
              finish()
              System.exit(0)
            } else {
              lastBackPressedTime = now
              Toast.makeText(this@MainActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
            }
          }
        }
      },
    )
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun createWebView(): WebView =
    WebView(this).apply {
      layoutParams =
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.allowFileAccess = false
      settings.allowContentAccess = false
      webViewClient =
        object : WebViewClient() {
          override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // 页面全部资源都在 assets 里，任何外部跳转都拦掉
            return true
          }

          override fun onPageFinished(view: WebView, url: String) {
            pageReady = true
          }
        }
      addJavascriptInterface(backupBridge, "LogYourRunAndroid")
    }

  /** 全屏显示 drawable-xxxhdpi/launch.jpg，保持铺满屏幕。 */
  private fun createSplashView(): ImageView =
    ImageView(this).apply {
      layoutParams =
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      scaleType = ImageView.ScaleType.FIT_XY
      setImageResource(R.drawable.launch)
      // 闪屏期间挡住触摸，避免误触下面的页面
      isClickable = true
    }

  private fun dismissSplash() {
    if (!::splashView.isInitialized) return
    splashView.visibility = View.GONE
    webView.visibility = View.VISIBLE
  }

  override fun onDestroy() {
    splashJob?.cancel()
    scope.cancel()
    if (::webView.isInitialized) webView.destroy()
    super.onDestroy()
  }

  @Deprecated("Activity Result 回调，需要与 startActivityForResult 配对")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    when (requestCode) {
      BackupBridge.REQ_EXPORT -> backupBridge.onExportResult(resultCode, data)
      BackupBridge.REQ_IMPORT -> backupBridge.onImportResult(resultCode, data)
    }
  }

  /** 供 BackupBridge 在页面里执行脚本。 */
  fun evaluateInPage(script: String) {
    if (::webView.isInitialized) {
      webView.evaluateJavascript(script, null)
    }
  }
}
