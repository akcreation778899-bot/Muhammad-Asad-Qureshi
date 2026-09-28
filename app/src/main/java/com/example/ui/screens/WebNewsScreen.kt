package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.news.NewsArticle
import com.example.ui.MainViewModel
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ElectricCoral
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WebNewsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val subTab by viewModel.webNewsSubTab.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Tab Selector (Live News vs Web Browser)
        TabRow(
            selectedTabIndex = subTab,
            containerColor = DarkSurfaceElevated,
            contentColor = NeonCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[subTab]),
                    color = NeonCyan,
                    height = 3.dp
                )
            }
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { viewModel.setWebNewsSubTab(0) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Newspaper, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live Fresh News", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                selectedContentColor = NeonCyan,
                unselectedContentColor = TextSecondary
            )
            Tab(
                selected = subTab == 1,
                onClick = { viewModel.setWebNewsSubTab(1) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Real-Time Browser", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                selectedContentColor = NeonCyan,
                unselectedContentColor = TextSecondary
            )
        }

        if (subTab == 0) {
            LiveNewsSection(viewModel)
        } else {
            RealTimeBrowserSection(viewModel)
        }
    }
}

@Composable
fun LiveNewsSection(viewModel: MainViewModel) {
    val articles by viewModel.newsArticles.collectAsState()
    val category by viewModel.selectedNewsCategory.collectAsState()
    val isLoading by viewModel.isLoadingNews.collectAsState()

    val categories = listOf("Top Headlines", "Technology", "Business", "Sports", "World")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // News Audio Broadcast Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            shape = RoundedCornerShape(18.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonCyan, NeonViolet)))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Fresh Real-Time News",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Real-time global & national feeds synthesized by Zoya",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Button(
                    onClick = { viewModel.readNewsSummaryAloud() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("read_news_summary_button")
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Listen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Category Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                Surface(
                    onClick = { viewModel.loadNews(cat) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (category == cat) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (category == cat) NeonCyan else DarkBorder)
                ) {
                    Text(
                        text = cat,
                        color = if (category == cat) NeonCyan else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonCyan)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(articles) { article ->
                    NewsArticleCard(
                        article = article,
                        onReadAloud = { viewModel.readArticleAloud(article) },
                        onOpenInBrowser = {
                            viewModel.loadUrl(article.link)
                            viewModel.setWebNewsSubTab(1)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NewsArticleCard(
    article: NewsArticle,
    onReadAloud: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = NeonCyan.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = article.source,
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = article.publishedAt,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = article.title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            if (article.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = article.description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onReadAloud, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Read Aloud", tint = NeonViolet, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = onOpenInBrowser,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Read in Browser", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealTimeBrowserSection(viewModel: MainViewModel) {
    val browserUrl by viewModel.browserUrl.collectAsState()
    var inputUrl by remember(browserUrl) { mutableStateOf(browserUrl) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var currentTitle by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Browser Controls & URL Bar
        Surface(
            color = DarkSurfaceElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (webViewInstance?.canGoBack() == true) webViewInstance?.goBack()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }

                    IconButton(
                        onClick = {
                            if (webViewInstance?.canGoForward() == true) webViewInstance?.goForward()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = TextPrimary)
                    }

                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload", tint = NeonCyan)
                    }

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text("Search web or enter URL...", fontSize = 12.sp, color = TextMuted) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("browser_url_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.loadUrl(inputUrl)
                                webViewInstance?.loadUrl(viewModel.browserUrl.value)
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Go", tint = NeonCyan)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // AI Summarize Page Audio Button
                    IconButton(
                        onClick = {
                            // Extract page text via javascript and read aloud with Zoya
                            webViewInstance?.evaluateJavascript("(function() { return document.title + '. ' + (document.querySelector('article, main, p') ? document.querySelector('article, main, p').innerText : document.body.innerText).substring(0, 300); })();") { result ->
                                val clean = result?.replace("\"", "")?.replace("\\n", " ") ?: ""
                                if (clean.isNotBlank()) {
                                    viewModel.voiceManager.speak("Zoya web page parh rahi hai: $clean")
                                } else {
                                    viewModel.voiceManager.speak("Page load ho chuka hai: $currentTitle")
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp).testTag("ai_page_summarize_button")
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "AI Read Page", tint = NeonViolet)
                    }
                }

                // Quick Bookmarks Bar
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    val quickLinks = listOf(
                        "Google" to "https://www.google.com",
                        "Dawn News" to "https://www.dawn.com",
                        "BBC News" to "https://www.bbc.com",
                        "Wikipedia" to "https://www.wikipedia.org",
                        "TechCrunch" to "https://techcrunch.com"
                    )
                    items(quickLinks) { (label, url) ->
                        Surface(
                            onClick = {
                                inputUrl = url
                                viewModel.loadUrl(url)
                                webViewInstance?.loadUrl(url)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                        ) {
                            Text(
                                text = label,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        if (pageProgress in 1..99) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = NeonCyan,
                trackColor = Color.Transparent
            )
        }

        // Live WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            url?.let { inputUrl = it }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            currentTitle = view?.title ?: ""
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            pageProgress = newProgress
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            title?.let { currentTitle = it }
                        }
                    }

                    loadUrl(browserUrl)
                    webViewInstance = this
                }
            },
            update = { webView ->
                if (webView.url != browserUrl) {
                    webView.loadUrl(browserUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("realtime_webview")
        )
    }
}
