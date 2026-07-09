package com.fuck.zlx

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment // 修复：补全了对齐包
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp // 修复：补全了尺寸包
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HomeWebViewScreen(onWebViewCreated: (WebView) -> Unit, jsInterface: SniffJsInterface) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE) }
    var currentUrl by remember { mutableStateOf(sharedPref.getString("HOME_URL", "https://www.zl-x.com") ?: "https://www.zl-x.com") }
    
    var progress by remember { mutableFloatStateOf(0f) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showChangeUrlDialog by remember { mutableStateOf(false) }
    var inputUrlText by remember { mutableStateOf(currentUrl) }
    var showMenu by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val isLazyLoad = prefs.getBoolean("lazy_load_video", true)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (progress > 0f && progress < 1f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            AndroidView(
                modifier = Modifier.weight(1f),
                factory = { ctx ->
                    val swipeLayout = SwipeRefreshLayout(ctx)
                    val webView = WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true 
                            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        
                        val cookieManager = android.webkit.CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        
                        addJavascriptInterface(jsInterface, "AndroidSniffer")

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                swipeLayout.isRefreshing = false 
                                android.webkit.CookieManager.getInstance().flush()
                                
                                val injectScript = """
                                    javascript:(function() {
                                        if (window.hasInjectedSniffer) return;
                                        window.hasInjectedSniffer = true;
                                        let foundM3u8 = new Set();
                                        let foundThumbs = new Set();
                                        let resolvingLinks = new Set();
                                        let hashToItemId = new Map();
                                        
                                        function cleanUrl(raw) {
                                            if (!raw) return '';
                                            return raw.split('?')[0].split('#')[0].replace(/[^a-zA-Z0-9\:\/\.\-\_]$/, '');
                                        }
                                        function extractHash(url) {
                                            const m = url.match(/([a-f0-9]{16,})/i);
                                            return m ? m[1].toLowerCase() : null;
                                        }
                                        function addM3u8Item(m3u8Url, thumbUrl, isComplete, hash) {
                                            const clean = cleanUrl(m3u8Url);
                                            if (!clean || foundM3u8.has(clean)) return null;
                                            if (isComplete && hash && hashToItemId.has(hash)) {
                                                const itemId = hashToItemId.get(hash);
                                                foundM3u8.add(clean);
                                                hashToItemId.delete(hash);
                                                if(window.AndroidSniffer) window.AndroidSniffer.onUpdateItem(itemId, clean);
                                                return null;
                                            }
                                            foundM3u8.add(clean);
                                            const itemId = 'item-' + Math.random().toString(36).substr(2, 9);
                                            if (!isComplete && hash) hashToItemId.set(hash, itemId);
                                            if(window.AndroidSniffer) {
                                                window.AndroidSniffer.onAddItem(itemId, clean, thumbUrl || "", isComplete);
                                            }
                                            return itemId;
                                        }
                                        async function autoResolveRealLink(previewM3u8, itemId) {
                                            if (!previewM3u8) return;
                                            resolvingLinks.add(previewM3u8);
                                            try {
                                                const response = await fetch(previewM3u8, { mode: 'cors', cache: 'no-store' });
                                                const text = await response.text();
                                                const realLinkMatch = text.match(/https?:\/\/[^\s"'>]+\/index\.m3u8/i) || text.match(/https?:\/\/[^\s"'>]+\.m3u8/i);
                                                if (realLinkMatch && realLinkMatch[0] && cleanUrl(realLinkMatch[0]) !== cleanUrl(previewM3u8)) {
                                                    const realUrl = cleanUrl(realLinkMatch[0]);
                                                    if (!foundM3u8.has(realUrl)) foundM3u8.add(realUrl);
                                                    if(window.AndroidSniffer) window.AndroidSniffer.onUpdateItem(itemId, realUrl);
                                                } else if (response.redirected && cleanUrl(response.url) !== cleanUrl(previewM3u8)) {
                                                    const realUrl = cleanUrl(response.url);
                                                    if (!foundM3u8.has(realUrl)) foundM3u8.add(realUrl);
                                                    if(window.AndroidSniffer) window.AndroidSniffer.onUpdateItem(itemId, realUrl);
                                                }
                                            } catch(e) {} finally {
                                                resolvingLinks.delete(previewM3u8);
                                            }
                                        }
                                        function handlePosterUrl(url) {
                                            const clean = cleanUrl(url);
                                            if (foundThumbs.has(clean)) return;
                                            foundThumbs.add(clean);
                                            const match = clean.match(/([a-f0-9]{16,})\/(thumbnails|poster2)\.jpg/i);
                                            if (match && match[1]) {
                                                const hash = match[1].toLowerCase();
                                                const previewM3u8 = 'https://video.zl-x.xyz/try/' + hash + '.m3u8';
                                                const itemId = addM3u8Item(previewM3u8, clean, false, hash);
                                                if (itemId) autoResolveRealLink(previewM3u8, itemId);
                                            }
                                        }
                                        function handlePngUrl(url) {
                                            const clean = cleanUrl(url);
                                            const m3u8 = clean.replace(/index\d*\.png$/i, 'index.m3u8');
                                            if (m3u8) {
                                                const hash = extractHash(m3u8);
                                                addM3u8Item(m3u8, '', true, hash);
                                            }
                                        }
                                        function handlePlayM3u8Pseudo(url) {
                                            const match = url.match(/m3u8=(https?:\/\/video\.zl-x\.xyz\/try\/[a-f0-9]{16,}\.m3u8[^"\s'>]*)/i);
                                            if (match && match[1]) {
                                                const clean = cleanUrl(decodeURIComponent(match[1]));
                                                if (clean && !resolvingLinks.has(clean)) {
                                                    const hash = extractHash(clean);
                                                    const itemId = addM3u8Item(clean, '', false, hash);
                                                    if (itemId) autoResolveRealLink(clean, itemId);
                                                }
                                            }
                                        }
                                        function handleFakeM3u8(url) {
                                            const clean = cleanUrl(url);
                                            if (!clean || resolvingLinks.has(clean)) return;
                                            const hash = extractHash(clean);
                                            if (window.location.href.indexOf('https://video.zl-x.xyz/try/') === 0) {
                                                addM3u8Item(clean, '', true, hash);
                                            } else {
                                                const itemId = addM3u8Item(clean, '', false, hash);
                                                if (itemId) autoResolveRealLink(clean, itemId);
                                            }
                                        }
                                        function handleRealM3u8(url) {
                                            const clean = cleanUrl(url);
                                            if (!clean || foundM3u8.has(clean)) return;
                                            addM3u8Item(clean, '', true, extractHash(clean));
                                        }
                                        function checkUrlForLinks(url) {
                                            if (!url) return;
                                            if (/thumbnails\.jpg|poster2\.jpg/i.test(url)) handlePosterUrl(url);
                                            else if (/index\d*\.png/i.test(url)) handlePngUrl(url);
                                            else if (/index\.m3u8/i.test(url) && url.indexOf('video.zl-x.xyz') === -1) handleRealM3u8(url);
                                            else if (/video\.zl-x\.xyz\/try\/[a-f0-9]{16,}\.m3u8/i.test(url)) handleFakeM3u8(url);
                                            else if (/playm3u8\?m3u8=/i.test(url)) handlePlayM3u8Pseudo(url);
                                        }
                                        const origFetch = window.fetch;
                                        window.fetch = function(...args) {
                                            const url = typeof args[0] === 'string' ? args[0] : (args[0] ? args[0].url : '');
                                            checkUrlForLinks(url);
                                            return origFetch.apply(this, args).then(resp => {
                                                const clone = resp.clone();
                                                clone.text().then(text => {
                                                    if (!text) return;
                                                    (text.match(/https?:\/\/[^\s"'>]+(thumbnails|poster2)\.jpg/gi) || []).forEach(handlePosterUrl);
                                                    (text.match(/https?:\/\/[^\s"'>]+index\d*\.png/gi) || []).forEach(handlePngUrl);
                                                    (text.match(/https?:\/\/video\.zl-x\.xyz\/try\/[a-f0-9]{16,}\.m3u8\?[^\s"'>]*/gi) || []).forEach(u => {
                                                        if (!resolvingLinks.has(cleanUrl(u))) handleFakeM3u8(u);
                                                    });
                                                    (text.match(/playm3u8\?m3u8=(https?:\/\/video\.zl-x\.xyz\/try\/[a-f0-9]{16,}\.m3u8[^"\s'>]*)/gi) || []).forEach(m => {
                                                        const match = m.match(/m3u8=(https?:\/\/video\.zl-x\.xyz\/try\/[a-f0-9]{16,}\.m3u8[^"\s'>]*)/i);
                                                        if (match && match[1] && !resolvingLinks.has(cleanUrl(match[1]))) handlePlayM3u8Pseudo(m);
                                                    });
                                                    (text.match(/https?:\/\/[^\s"'>]+\/index\.m3u8/gi) || []).forEach(u => {
                                                        if (u.indexOf('video.zl-x.xyz') === -1) handleRealM3u8(u);
                                                    });
                                                }).catch(e => {});
                                                return resp;
                                            });
                                        };
                                        const origOpen = XMLHttpRequest.prototype.open;
                                        XMLHttpRequest.prototype.open = function(...args) {
                                            const url = args[1] || '';
                                            checkUrlForLinks(url);
                                            this.addEventListener('load', () => {
                                                if (this.responseText) {
                                                    const text = this.responseText;
                                                    (text.match(/https?:\/\/[^\s"'>]+(thumbnails|poster2)\.jpg/gi) || []).forEach(handlePosterUrl);
                                                    (text.match(/https?:\/\/[^\s"'>]+index\d*\.png/gi) || []).forEach(handlePngUrl);
                                                    (text.match(/https?:\/\/video\.zl-x\.xyz\/try\/[a-f0-9]{16,}\.m3u8\?[^\s"'>]*/gi) || []).forEach(u => {
                                                        if (!resolvingLinks.has(cleanUrl(u))) handleFakeM3u8(u);
                                                    });
                                                    (text.match(/playm3u8\?m3u8=(https?:\/\/video\.zl-x\.xyz\/try\/[a-f0-9]{16,}\.m3u8[^"\s'>]*)/gi) || []).forEach(m => {
                                                        const match = m.match(/m3u8=(https?:\/\/video\.zl-x\.xyz\/try\/[a-f0-9]{16,}\.m3u8[^"\s'>]*)/i);
                                                        if (match && match[1] && !resolvingLinks.has(cleanUrl(match[1]))) handlePlayM3u8Pseudo(m);
                                                    });
                                                    (text.match(/https?:\/\/[^\s"'>]+\/index\.m3u8/gi) || []).forEach(u => {
                                                        if (u.indexOf('video.zl-x.xyz') === -1) handleRealM3u8(u);
                                                    });
                                                }
                                            });
                                            origOpen.apply(this, args);
                                        };
                                        if (typeof PerformanceObserver !== 'undefined') {
                                            const observer = new PerformanceObserver((list) => {
                                                list.getEntries().forEach(entry => checkUrlForLinks(entry.name));
                                            });
                                            observer.observe({ entryTypes: ['resource'] });
                                        }

                                        // 🌟 修复网页闪烁的核心点：去除了霸道的 resize 事件！
                                        var lazyLoadEnabled = $isLazyLoad;
                                        if (!lazyLoadEnabled) {
                                            setInterval(function() {
                                                document.querySelectorAll('[loading="lazy"]').forEach(function(el) {
                                                    el.setAttribute('loading', 'eager');
                                                });
                                                var mediaElements = document.querySelectorAll('img, video, source');
                                                mediaElements.forEach(function(el) {
                                                    for (var key in el.dataset) {
                                                        var val = el.dataset[key];
                                                        if (val && typeof val === 'string' && (val.indexOf('http') === 0 || val.indexOf('//') === 0)) {
                                                            if (el.src !== val) el.src = val; 
                                                        }
                                                    }
                                                });
                                                // 换成温柔无感知的触发器
                                                window.dispatchEvent(new Event('scroll'));
                                            }, 2000);
                                        }
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(injectScript, null)
                            }
                        }
                        loadUrl(currentUrl)
                        onWebViewCreated(this)
                        webViewInstance = this
                    }

                    swipeLayout.setOnRefreshListener { webView.reload() }
                    swipeLayout.addView(webView)
                    swipeLayout
                }
            )
        }

        // 右下角菜单按钮
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 120.dp)) {
            FloatingActionButton(
                onClick = { showMenu = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Filled.MoreVert, "菜单")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                // 刷新当前页面
                DropdownMenuItem(
                    text = { Text("刷新") },
                    onClick = {
                        showMenu = false
                        webViewInstance?.reload()
                    },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) }
                )
                // 将当前 WebView 正在显示的 URL 设为首页
                DropdownMenuItem(
                    text = { Text("设为首页") },
                    onClick = {
                        showMenu = false
                        val curUrl = webViewInstance?.url ?: currentUrl
                        sharedPref.edit().putString("HOME_URL", curUrl).apply()
                        currentUrl = curUrl
                        Toast.makeText(context, "已将当前页面设为首页", Toast.LENGTH_SHORT).show()
                    },
                    leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) }
                )
                // 修改首页地址（原有功能）
                DropdownMenuItem(
                    text = { Text("修改首页地址") },
                    onClick = {
                        showMenu = false
                        inputUrlText = currentUrl
                        showChangeUrlDialog = true
                    },
                    leadingIcon = { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                )
            }
        }

        if (showChangeUrlDialog) {
            AlertDialog(
                onDismissRequest = { showChangeUrlDialog = false },
                title = { Text("设置新首页") },
                text = {
                    OutlinedTextField(
                        value = inputUrlText,
                        onValueChange = { inputUrlText = it },
                        label = { Text("输入完整的网址(含http/https)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val newUrl = if (!inputUrlText.startsWith("http")) "https://$inputUrlText" else inputUrlText
                        sharedPref.edit().putString("HOME_URL", newUrl).apply()
                        currentUrl = newUrl
                        webViewInstance?.loadUrl(newUrl)
                        showChangeUrlDialog = false
                        Toast.makeText(context, "设置成功！", Toast.LENGTH_SHORT).show()
                    }) { Text("保存并加载") }
                },
                dismissButton = {
                    TextButton(onClick = { showChangeUrlDialog = false }) { Text("取消") }
                }
            )
        }
    }
}
