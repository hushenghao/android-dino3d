package com.dede.dino3d

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * Dino Egg.
 *
 * @author shhu
 * @since 2023/1/21
 */
class Dino3DActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var server: DinoServer

    override fun onCreate(savedInstanceState: Bundle?) {
        val controllerCompat = WindowInsetsControllerCompat(window, window.decorView)
        controllerCompat.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
        controllerCompat.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 适配刘海屏
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.CONSUMED
        }
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        val settings = webView.settings
        @Suppress("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.displayZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(false)
        settings.domStorageEnabled = true// require dom storage

        setContentView(webView)

        server = DinoServer(applicationContext).apply { launch() }
        webView.webViewClient = WebViewClient()
        webView.loadUrl(DinoServer.MAIN_PAGER)
        WebViewDinoController(this).attach(webView)
    }

    @SuppressLint("ClickableViewAccessibility")
    private class WebViewDinoController(val activity: Activity) : View.OnTouchListener {
        private var downTime: Long = 0

        @Volatile
        private var status: Int = -1

        @JavascriptInterface
        fun exit() {
            activity.finish()
        }

        @JavascriptInterface
        fun postStatus(status: Int) {
            this.status = status
            // 0 -> prepared
            // 1 -> start
            // 2 -> stop
            // 3 -> pause
        }

        fun attach(webView: WebView) {
            webView.setOnTouchListener(this)
            webView.addJavascriptInterface(this, "nativeBridge")
        }

        override fun onTouch(webView: View, event: MotionEvent): Boolean {
            if (status < 0 || status == 2 || status == 3) return false

            val keyCode = if (status == 0 || event.x >= webView.width / 2f)
                KeyEvent.KEYCODE_DPAD_UP    // Arrow Up, Jump or Start game
            else
                KeyEvent.KEYCODE_DPAD_DOWN  // Arrow Down, Squat
            val keyAction = when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = SystemClock.uptimeMillis()
                    KeyEvent.ACTION_DOWN
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> KeyEvent.ACTION_UP
                else -> return false
            }
            val keyEvent = createKeyEvent(keyAction, keyCode, downTime)
            // Simulate keyboard events
            webView.dispatchKeyEvent(keyEvent)
            return false
        }

        private fun createKeyEvent(keyAction: Int, keyCode: Int, downTime: Long): KeyEvent {
            return KeyEvent(downTime, SystemClock.uptimeMillis(), keyAction, keyCode, 0)
        }
    }

    private class DinoServer(val context: Context) : NanoHTTPD(IP, PORT) {

        override fun serve(session: IHTTPSession): Response {
            if (session.uri == "/") {
                return newFixedLengthResponse(Response.Status.REDIRECT.name).apply {
                    status = Response.Status.REDIRECT
                    addHeader("Location", "/dino3d/index.html")
                }
            }
            if (!session.uri.startsWith("/dino3d")) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Not Found"
                )
            }
            val path = session.uri.substring(1)
            val stream = try {
                context.assets.open(path)
            } catch (e: IOException) {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Internal Error: " + e.message
                )
            }
            return newFixedLengthResponse(
                Response.Status.OK,
                getMimeTypeForFile(session.uri),
                stream,
                stream.available().toLong()
            )
        }

        fun launch() {
            try {
                start()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        companion object {
            private const val PORT = 8888
            private const val IP = "127.0.0.1"
            const val MAIN_PAGER = "http://$IP:$PORT/dino3d/index.html"
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.requestFocus()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        server.stop()
        super.onDestroy()
    }
}