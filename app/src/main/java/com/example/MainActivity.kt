package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import android.webkit.ValueCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private val urlFlow = MutableStateFlow("https://ventarys.net/asistant")

    val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            pendingWebChromePermission?.grant(pendingWebChromePermission?.resources)
        } else {
            pendingWebChromePermission?.deny()
        }
        pendingWebChromePermission = null
    }

    companion object {
        var pendingWebChromePermission: PermissionRequest? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        // Kiosk mode flag
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                val currentUrl by urlFlow.collectAsState()
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
                    AppContent(
                        initialUrl = currentUrl,
                        onMicPermissionRequested = { request ->
                            pendingWebChromePermission = request
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!text.isNullOrBlank()) {
                val encoded = URLEncoder.encode(text, "UTF-8")
                urlFlow.value = "https://ventarys.net/asistant?prompt=$encoded"
            }
        }
    }
}

@Composable
fun AppContent(initialUrl: String, onMicPermissionRequested: (PermissionRequest) -> Unit) {
    val isConnected = rememberConnectivityState()
    
    if (!isConnected) {
        OfflineScreen()
    } else {
        WebViewScreen(url = initialUrl, onMicPermissionRequested)
    }
}

@Composable
fun OfflineScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.sin_conexion),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, onMicPermissionRequested: (PermissionRequest) -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val isDarkTheme = isSystemInDarkTheme()
    
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = if (data != null) {
                val clipData = data.clipData
                if (clipData != null) {
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } else if (data.data != null) {
                    arrayOf(data.data!!)
                } else {
                    null
                }
            } else {
                null
            }
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }
    
    LaunchedEffect(url) {
        webViewInstance?.loadUrl(url)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setSupportZoom(false)
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true

                    val defaultUserAgent = settings.userAgentString
                    settings.userAgentString = defaultUserAgent.replace("; wv", "")

                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    CookieManager.getInstance().setAcceptCookie(true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val urlString = request?.url?.toString() ?: return false
                            if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                                return false // Let WebView load it
                            }
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, request?.url)
                                view?.context?.startActivity(intent)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            loadingProgress = 0f
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallbackParam: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            if (filePathCallback != null) {
                                filePathCallback?.onReceiveValue(null)
                            }
                            filePathCallback = filePathCallbackParam
                            if (fileChooserParams != null) {
                                try {
                                    fileChooserLauncher.launch(fileChooserParams.createIntent())
                                } catch (e: Exception) {
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = null
                                    return false
                                }
                            }
                            return true
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadingProgress = newProgress / 100f
                            if (newProgress == 100) isLoading = false
                        }

                        override fun onPermissionRequest(request: PermissionRequest) {
                            if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                onMicPermissionRequested(request)
                            } else {
                                request.deny()
                            }
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean {
                            val newWebView = WebView(view!!.context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.setSupportMultipleWindows(true)
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                CookieManager.getInstance().setAcceptCookie(true)
                            }
                            
                            val dialog = Dialog(view.context).apply {
                                setContentView(newWebView)
                                window?.setLayout(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                show()
                            }
                            
                            newWebView.webChromeClient = object : WebChromeClient() {
                                override fun onCloseWindow(window: WebView?) {
                                    dialog.dismiss()
                                }
                            }
                            
                            newWebView.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(newView: WebView?, request: WebResourceRequest?): Boolean {
                                    return false // load in the popup itself
                                }
                            }
                            
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            transport?.webView = newWebView
                            resultMsg?.sendToTarget()
                            return true
                        }
                    }
                    
                    loadUrl(url)
                    webViewInstance = this
                }
            },
            update = {
                webViewInstance = it
            }
        )

        // Loading Splash Screen
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Logo",
                        modifier = Modifier.size(120.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun rememberConnectivityState(): Boolean {
    val context = LocalContext.current
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    val currentNetwork = manager.activeNetwork
    val currentCapabilities = manager.getNetworkCapabilities(currentNetwork)
    val hasInternet = currentCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    
    var isConnected by remember { mutableStateOf(hasInternet) }

    DisposableEffect(manager) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
            
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isConnected = true
            }

            override fun onLost(network: Network) {
                isConnected = false
            }
        }
        
        manager.registerNetworkCallback(request, callback)

        onDispose {
            manager.unregisterNetworkCallback(callback)
        }
    }

    return isConnected
}
