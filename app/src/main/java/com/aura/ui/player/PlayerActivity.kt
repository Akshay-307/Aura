package com.aura.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.*
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.aura.AuraApp
import com.aura.data.model.ContentType
import com.aura.databinding.ActivityPlayerBinding
import com.aura.ui.base.ViewModelFactory
import com.aura.utils.Constants
import com.aura.utils.formatDuration
import com.aura.utils.hide
import com.aura.utils.show
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("UnsafeOptInUsageError")
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var viewModel: PlayerViewModel

    // ExoPlayer
    private var exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null

    private var isControlsVisible = true
    private var hideControlsJob: Job? = null
    private var isLocked = false
    private var playbackSpeed = 1.0f

    // Touch tracking for brightness/volume swipe
    private var touchStartY = 0f
    private var touchStartX = 0f
    private var initialBrightness = 0.5f
    private var initialVolume = 0
    private var audioManager: AudioManager? = null

    private var streamUrl: String = ""
    private var isHls: Boolean = false
    private var isEmbed: Boolean = false
    private var title: String = ""
    private var detailUrl: String = ""
    private var embedWebView: WebView? = null
    private var seekBarJob: Job? = null
    private var posterUrl: String = ""
    private var contentType: ContentType = ContentType.MOVIE

    private val webViewControls = mutableListOf<View>()
    private var isWebViewControlsVisible = true
    private var hideWebViewControlsJob: Job? = null
    private var webViewScroll: android.widget.HorizontalScrollView? = null
    private var webViewToggleBtn: android.widget.TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Removed FLAG_SECURE to prevent black screens on emulators, screen sharing, casting, and mirroring.

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as AuraApp
        viewModel = ViewModelProvider(this, ViewModelFactory(app))[PlayerViewModel::class.java]

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        streamUrl = intent.getStringExtra(Constants.EXTRA_STREAM_URL) ?: ""
        if (streamUrl.isBlank() ||
            streamUrl.startsWith("tv:") ||
            streamUrl.startsWith("movie:") ||
            (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://") && !streamUrl.startsWith("magnet:") && !streamUrl.startsWith("file://") && !streamUrl.startsWith("/"))) {

            android.widget.Toast.makeText(this, "The playback link is not valid or unavailable", android.widget.Toast.LENGTH_LONG).show()
            finish()
            return
        }
        isHls = intent.getBooleanExtra(Constants.EXTRA_STREAM_IS_HLS, false)
        val type = intent.getStringExtra("stream_type")
        isEmbed = if (type != null) {
            type == "embed" || type == "torrent"
        } else {
            streamUrl.contains("vidsrc")
                    || streamUrl.contains("vidlink")
                    || streamUrl.contains("111movies")
                    || streamUrl.contains("vidzee.wtf")
                    || streamUrl.startsWith("magnet:")
        }
        title = intent.getStringExtra(Constants.EXTRA_TITLE) ?: ""
        detailUrl = intent.getStringExtra(Constants.EXTRA_DETAIL_URL) ?: ""
        posterUrl = intent.getStringExtra("poster_url") ?: ""
        val contentTypeStr = intent.getStringExtra("content_type") ?: "MOVIE"
        contentType = try { ContentType.valueOf(contentTypeStr) } catch (_: Exception) { ContentType.MOVIE }

        binding.tvPlayerTitle.text = title

        if (isEmbed) {
            setupEmbedWebView()
        } else {
            setupPlayer()
            setupControls()
            setupGestures()

            val allUrls = intent.getStringArrayExtra("all_stream_urls")
            val allNames = intent.getStringArrayExtra("all_stream_names")
            if (!allUrls.isNullOrEmpty() && allUrls.size > 1) {
                binding.btnSources.visibility = View.VISIBLE
                binding.btnSources.setOnClickListener {
                    showNativeSourcesSelector(allUrls, allNames ?: emptyArray())
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupEmbedWebView() {
        binding.playerView.hide()
        binding.bufferingIndicator.hide()
        binding.controlsOverlay.hide()

        // Start simulated saving of position for WebView embed streams
        lifecycleScope.launch {
            val savedPos = viewModel.getSavedPosition(detailUrl)
            val elapsed = if (savedPos > 0) savedPos else 0L
            val simulatedStartTime = System.currentTimeMillis() - elapsed
            val duration = when (contentType) {
                ContentType.ANIME -> 24 * 60 * 1000L // 24 mins
                ContentType.MOVIE -> 120 * 60 * 1000L // 2 hours
                else -> 45 * 60 * 1000L // 45 mins
            }
            viewModel.startSavingPosition(
                detailUrl = detailUrl,
                title = title,
                posterUrl = posterUrl,
                contentType = contentType,
                getPosition = {
                    val currentElapsed = System.currentTimeMillis() - simulatedStartTime
                    currentElapsed.coerceAtMost(duration)
                },
                getDuration = { duration }
            )
        }

        val root = binding.root as FrameLayout

        val allowedHost = try {
            if (streamUrl.startsWith("magnet:")) {
                "webtor.io"
            } else {
                android.net.Uri.parse(streamUrl).host?.lowercase() ?: ""
            }
        } catch (_: Exception) { "" }

        // â”€â”€ WebView â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                setSupportMultipleWindows(false)
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                private val AD_DOMAINS = setOf(
                    "doubleclick.net", "googlesyndication.com", "adservice.google.com",
                    "adnxs.com", "moatads.com", "openx.net", "popads.net", "popcash.net",
                    "trafficjunky.com", "exoclick.com", "juicyads.com", "adsterra.com",
                    "hilltopads.net", "adskeeper.co.uk", "revcontent.com", "taboola.com",
                    "outbrain.com", "mgid.com", "propellerads.com", "adcash.com",
                    "clickadu.com", "servenobid.com", "betterads.org", "pushance.com",
                    "ad-maven.com", "admaven.com", "adsrvr.org", "rubiconproject.com",
                    "pubmatic.com", "smartadserver.com", "criteo.com", "yieldmo.com",
                    "33across.com", "appnexus.com", "lijit.com", "gravity4.com",
                    "teads.tv", "bidswitch.net", "improvedigital.com",
                    "bongacams.com", "chaturbate.com", "onclickmax.com", "onclicksuper.com",
                    "1phads.com", "richpush.co", "pushground.com", "bitterstrawberry.com",
                    "a-ads.com", "coinzillatag.com", "cointraffic.io", "onclicktrends.com",
                    "onclickalgo.com", "adrunnet.com", "adsrv.pro", "clik.tokyo", "clik.today",
                    "histats.com", "streamad.cc", "popcash.net", "popunder.net",
                    "clickaine.com", "adskeeper.com", "adspyglass.com", "adf.ly",
                    "ouo.io", "bc.vc", "shorte.st", "clk.sh", "za.gl", "up-to-down.net",
                    "linkvertise.com", "exe.io", "gplinks.co", "shrinke.me",
                    "cutpaid.com", "clk.ink", "shrinkme.io", "earnow.online",
                    "fc.lc", "short1link.com", "bit.ly.link", "go.skiplink.me",
                    "adbull.me", "adfly.info", "shorterall.com", "short-url.link",
                    "relink.us", "link1s.com", "gplinks.in", "link.paid4.fun",
                    "scorecardresearch.com", "quantserve.com", "comscore.com",
                    "chartbeat.com", "parsely.com", "newrelic.com", "akamaized.net",
                    "trafficstars.com", "plugrush.com", "adnium.com", "ero-advertising.com",
                    "juicyads.com", "tsartech.g", "liveadexchanger.com"
                )

                private val BLOCK_PATTERNS = listOf(
                    "/popup", "/popunder", "/ads/", "/ad/", "adsense", "pagead",
                    "prebid", "ad_unit", "adsbygoogle", "doubleclick", "/banner",
                    "click.php?", "redirect.php?", "go.php?", "out.php?"
                )

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val host    = request.url.host?.lowercase() ?: return true
                    val fullUrl = request.url.toString().lowercase()

                    if (AD_DOMAINS.any { d -> host == d || host.endsWith(".$d") }) return true
                    if (BLOCK_PATTERNS.any { fullUrl.contains(it) }) return true

                    // Strict sandboxing: Only allow main frame navigation if the host matches our allowedHost or its subdomains
                    if (allowedHost.isNotEmpty() && request.isForMainFrame) {
                        val isAllowed = host == allowedHost || host.endsWith(".$allowedHost")
                        if (!isAllowed) {
                            return true // Strictly block redirects of the main window to other sites (like YouTube, redirect loops, etc.)
                        }
                    }
                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    val host    = request.url.host?.lowercase() ?: ""
                    val fullUrl = request.url.toString().lowercase()

                    val block = AD_DOMAINS.any { d -> host == d || host.endsWith(".$d") }
                        || BLOCK_PATTERNS.any { fullUrl.contains(it) }

                    if (block) return android.webkit.WebResourceResponse(
                        "text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0))
                    )
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    injectAggressiveAdblocker(view, url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectAggressiveAdblocker(view, url)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean,
                    isUserGesture: Boolean, resultMsg: android.os.Message?
                ): Boolean = false
            }

            if (streamUrl.startsWith("magnet:")) {
                val html = buildWebTorrentHtml(streamUrl)
                loadDataWithBaseURL("https://webtor.io", html, "text/html", "UTF-8", null)
            } else {
                loadUrl(streamUrl)
            }
        }
        embedWebView = webView
        root.addView(webView)

        // â”€â”€ WebView Gestures: Intercept single taps to toggle Aura UI controls overlay â”€â”€â”€â”€â”€â”€â”€
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                toggleWebViewControls()
                return false // always return false so touches are still forwarded to inner WebView player buttons
            }
        })
        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        webViewControls.clear()

        // â”€â”€ Back Button (Polished Round Icon Button) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val backBtn = android.widget.ImageButton(this).apply {
            setImageResource(com.aura.R.drawable.ic_arrow_back)
            setBackgroundResource(com.aura.R.drawable.bg_player_icon_btn)
            setColorFilter(android.graphics.Color.WHITE)
            val padding = (10 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            layoutParams = FrameLayout.LayoutParams(
                (44 * resources.displayMetrics.density).toInt(),
                (44 * resources.displayMetrics.density).toInt()
            ).also { lp ->
                lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                lp.topMargin = (24 * resources.displayMetrics.density).toInt()
                lp.leftMargin = (24 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        root.addView(backBtn)
        webViewControls.add(backBtn)

        // â”€â”€ Source Switcher Bar (Minimizable) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val allUrls   = intent.getStringArrayExtra("all_stream_urls")
        val allNames  = intent.getStringArrayExtra("all_stream_names")
        if (!allUrls.isNullOrEmpty() && allUrls.size > 1) {
            buildMinimizableSourceSwitcher(root, allUrls, allNames ?: emptyArray(), webView)
        }

        // Initialize WebView Controls visibility state
        isWebViewControlsVisible = true
        backBtn.visibility = View.VISIBLE
        backBtn.alpha = 1f
        webViewToggleBtn?.visibility = View.VISIBLE
        webViewToggleBtn?.alpha = 1f
        scheduleHideWebViewControls()
    }

    private fun injectAggressiveAdblocker(view: WebView?, url: String?) {
        val currentHost = try {
            android.net.Uri.parse(url).host?.lowercase() ?: ""
        } catch (_: Exception) { "" }

        view?.evaluateJavascript("""
            (function(){
                function runAdBlocker() {
                    window.open = function(){ return null; };
                    window.alert = function(){};
                    window.confirm = function(){ return false; };
                    window.prompt = function(){ return null; };

                    var _allowedHost = '$currentHost';
                    var _origAssign   = window.location.assign.bind(window.location);
                    var _origReplace  = window.location.replace.bind(window.location);
                    Object.defineProperty(window.location, 'assign', {
                        value: function(u){ try { var h=new URL(u).hostname.toLowerCase(); if(h===_allowedHost||h.endsWith('.'+_allowedHost)){_origAssign(u);} } catch(e){} },
                        writable: true, configurable: true
                    });
                    Object.defineProperty(window.location, 'replace', {
                        value: function(u){ try { var h=new URL(u).hostname.toLowerCase(); if(h===_allowedHost||h.endsWith('.'+_allowedHost)){_origReplace(u);} } catch(e){} },
                        writable: true, configurable: true
                    });

                    if (!document.getElementById('Aura-adblock-css')) {
                        var s = document.createElement('style');
                        s.id = 'Aura-adblock-css';
                        s.innerHTML = [
                            '.ad-overlay','.popup-ad','.ad-container','.ad-banner',
                            '[class*="ad-banner"]','[id*="banner"]','[class*="popup"]',
                            '[id*="popup"]','[class*="adsbygoogle"]','[id*="adslot"]',
                            '.overlay-ad','.interstitial-ad','#ad-container',
                            '.ad-wrapper','[class*="ads-wrapper"]','iframe[src*="ads"]',
                            'iframe[src*="pop"]','iframe[src*="doubleclick"]','iframe[src*="adserv"]'
                        ].join(',') + '{display:none!important;visibility:hidden!important;height:0!important;width:0!important;overflow:hidden!important;}';
                        document.head && document.head.appendChild(s);
                    }

                    var adScriptPatterns = ['popads','exoclick','propellerads','adsterra',
                        'trafficjunky','clickadu','admaven','popcash','juicyads','onclick'];
                    document.querySelectorAll('script[src]').forEach(function(sc){
                        var src=(sc.src||'').toLowerCase();
                        if(adScriptPatterns.some(function(p){return src.indexOf(p)>-1;})){
                            sc.remove();
                        }
                    });

                    document.querySelectorAll('iframe').forEach(function(f){
                        var src = (f.src||'').toLowerCase();
                        var adPatterns=['ads','pop','banner','click','doubleclick','adserv','pagead','onclick'];
                        if(adPatterns.some(function(p){return src.indexOf(p)>-1;})){
                            f.remove();
                        }
                    });

                    function autoBypass(){
                        var selectors = 'button, a, div[role="button"], span[role="button"], [class*="btn"], [class*="button"]';
                        document.querySelectorAll(selectors).forEach(function(b){
                            var txt = (b.textContent||b.innerText||'').trim().toLowerCase();
                            var bypassTexts = [
                                'continue with ads','i want to continue with ads','continue to watch',
                                'free with ads','no thanks','continue for free',
                                'watch now','play now','skip ad','skip ads',
                                'close ad','dismiss','got it','accept',
                                'watch for free','continue watching','start watching',
                                'watch with ads','continue with advertisement'
                            ];
                            if(bypassTexts.some(function(t){return txt===t||txt.indexOf(t)>-1;})){
                                b.click();
                            }
                            var cls = (b.className||'').toLowerCase();
                            if(cls.indexOf('play-btn')>-1||cls.indexOf('btn-play')>-1||
                               cls.indexOf('watch-btn')>-1||cls.indexOf('skip')>-1||
                               cls.indexOf('free-btn')>-1||cls.indexOf('continue-btn')>-1){
                                b.click();
                            }
                        });
                        
                        document.querySelectorAll('button, [role="button"]').forEach(function(b){
                            var txt = (b.textContent||b.innerText||'').trim().toLowerCase();
                            if(txt.indexOf('ad')>-1 && txt.indexOf('sign')===-1){
                                b.click();
                            }
                            if(b.getAttribute('data-testid')==='continue-with-ads' ||
                               b.getAttribute('data-action')==='continue'){
                                b.click();
                            }
                        });
                        
                        document.querySelectorAll(
                            '[class*="gate"],[class*="modal"],[class*="overlay"],' +
                            '[class*="interstitial"],[class*="ad-wall"],[class*="splash"],' +
                            '[id*="gate"],[id*="modal"],[id*="overlay"],' +
                            '[class*="popup"],[class*="dialog"],[class*="paywall"]'
                        ).forEach(function(el){
                            var zIndex = parseInt(window.getComputedStyle(el).zIndex)||0;
                            if(zIndex > 100) el.style.cssText='display:none!important;';
                        });
                    }

                    autoBypass();
                }

                runAdBlocker();
                var intervalId = setInterval(runAdBlocker, 500);
                setTimeout(function() { clearInterval(intervalId); }, 25000);
            })();
        """.trimIndent(), null)
    }

    private fun toggleWebViewControls() {
        isWebViewControlsVisible = !isWebViewControlsVisible
        if (isWebViewControlsVisible) {
            showWebViewControlsTemporarily()
        } else {
            hideWebViewControls()
        }
    }

    private fun showWebViewControlsTemporarily() {
        hideWebViewControlsJob?.cancel()
        isWebViewControlsVisible = true
        webViewControls.forEach { v ->
            v.visibility = View.VISIBLE
            v.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
        scheduleHideWebViewControls()
    }

    private fun hideWebViewControls() {
        hideWebViewControlsJob?.cancel()
        isWebViewControlsVisible = false
        webViewControls.forEach { v ->
            v.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    v.visibility = View.GONE
                }
                .start()
        }

        // Hide scroll view if expanded
        webViewScroll?.let { scroll ->
            if (scroll.visibility == View.VISIBLE) {
                scroll.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction { scroll.visibility = View.GONE }
                    .start()
            }
        }

        // Reset toggleBtn state to collapsed
        webViewToggleBtn?.let { toggleBtn ->
            val normalGd = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20 * resources.displayMetrics.density
                setColor(android.graphics.Color.parseColor("#CC111111"))
                setStroke((1 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#44FFFFFF"))
            }
            toggleBtn.background = normalGd
        }
    }

    private fun scheduleHideWebViewControls() {
        hideWebViewControlsJob?.cancel()
        hideWebViewControlsJob = lifecycleScope.launch {
            delay(5000)  // 5 seconds
            hideWebViewControls()
        }
    }

    /** Builds a source bar with a small toggle FAB to show/hide the provider list. */
    private fun buildMinimizableSourceSwitcher(
        root: FrameLayout,
        urls: Array<String>,
        names: Array<String>,
        webView: WebView
    ) {
        var isBarExpanded = false

        // â”€â”€ Scrollable source row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val scroll = android.widget.HorizontalScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { lp ->
                lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                lp.topMargin = (24 * resources.displayMetrics.density).toInt()
                lp.leftMargin = (80 * resources.displayMetrics.density).toInt()
                lp.rightMargin = (130 * resources.displayMetrics.density).toInt()
            }
            visibility = View.GONE
            alpha = 0f
        }
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, (8 * resources.displayMetrics.density).toInt(), pad, (8 * resources.displayMetrics.density).toInt())
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#CC111111"))
                cornerRadius = 24 * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#33FFFFFF"))
            }
            background = bg
        }
        urls.forEachIndexed { i, url ->
            val label = if (i < names.size && names[i].isNotBlank()) names[i] else "Source ${i + 1}"
            val btn = android.widget.TextView(this).apply {
                text = label
                setTextColor(android.graphics.Color.WHITE)
                textSize = 11f
                val hPad = (14 * resources.displayMetrics.density).toInt()
                val vPad = (6 * resources.displayMetrics.density).toInt()
                setPadding(hPad, vPad, hPad, vPad)
                val active = url == streamUrl
                val chip = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 20 * resources.displayMetrics.density
                    setColor(
                        if (active) android.graphics.Color.parseColor("#E50914")
                        else android.graphics.Color.parseColor("#44FFFFFF")
                    )
                }
                background = chip
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { lp ->
                    val m = (4 * resources.displayMetrics.density).toInt()
                    lp.setMargins(m, 0, m, 0)
                }
                setOnClickListener {
                    val type = when {
                        url.contains("torrent.cf") || url.contains("stremio.best") || label.contains("Torrent", ignoreCase = true) -> "direct"
                        label.contains("Direct", ignoreCase = true) -> "direct"
                        else -> "embed"
                    }
                    val intent = Intent(this@PlayerActivity, PlayerActivity::class.java).apply {
                        putExtra(Constants.EXTRA_STREAM_URL, url)
                        putExtra(Constants.EXTRA_STREAM_IS_HLS, url.contains(".m3u8"))
                        putExtra(Constants.EXTRA_TITLE, title)
                        putExtra(Constants.EXTRA_DETAIL_URL, detailUrl)
                        putExtra("all_stream_urls", urls)
                        putExtra("all_stream_names", names)
                        putExtra("stream_type", type)
                    }
                    finish()
                    startActivity(intent)
                }
            }
            row.addView(btn)
        }
        scroll.addView(row)
        root.addView(scroll)
        webViewScroll = scroll

        // â”€â”€ Beautiful Sources Pill toggle button â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val toggleBtn = android.widget.TextView(this).apply {
            text = "Sources ⚡"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            val paddingH = (16 * resources.displayMetrics.density).toInt()
            val paddingV = (8 * resources.displayMetrics.density).toInt()
            setPadding(paddingH, paddingV, paddingH, paddingV)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { lp ->
                lp.gravity = android.view.Gravity.TOP or android.view.Gravity.END
                lp.topMargin = (24 * resources.displayMetrics.density).toInt()
                lp.rightMargin = (24 * resources.displayMetrics.density).toInt()
            }
            val gd = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20 * resources.displayMetrics.density
                setColor(android.graphics.Color.parseColor("#CC111111"))
                setStroke((1 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#44FFFFFF"))
            }
            background = gd
            elevation = 8f
            setOnClickListener {
                isBarExpanded = !isBarExpanded
                if (isBarExpanded) {
                    scroll.visibility = View.VISIBLE
                    scroll.animate().alpha(1f).setDuration(200).start()
                    val activeGd = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 20 * resources.displayMetrics.density
                        setColor(android.graphics.Color.parseColor("#E50914"))
                    }
                    background = activeGd
                } else {
                    scroll.animate().alpha(0f).setDuration(200).withEndAction {
                        scroll.visibility = View.GONE
                    }.start()
                    val normalGd = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 20 * resources.displayMetrics.density
                        setColor(android.graphics.Color.parseColor("#CC111111"))
                        setStroke((1 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#44FFFFFF"))
                    }
                    background = normalGd
                }
                scheduleHideWebViewControls()
            }
        }
        root.addView(toggleBtn)
        webViewToggleBtn = toggleBtn
        webViewControls.add(toggleBtn)
    }

    private fun showNativeSourcesSelector(urls: Array<String>, names: Array<String>) {
        val list = names.mapIndexed { i, name ->
            if (name.isNotBlank()) name else "Source ${i + 1}"
        }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Source")
            .setItems(list) { _, which ->
                val selectedUrl = urls[which]
                val selectedName = names[which]
                val type = when {
                    selectedName.contains("Torrent", ignoreCase = true) -> "direct"
                    selectedName.contains("Direct", ignoreCase = true) -> "direct"
                    selectedUrl.contains("stremio.best") -> "direct"
                    else -> "embed"
                }

                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra(Constants.EXTRA_STREAM_URL, selectedUrl)
                    putExtra(Constants.EXTRA_STREAM_IS_HLS, selectedUrl.contains(".m3u8"))
                    putExtra(Constants.EXTRA_TITLE, title)
                    putExtra(Constants.EXTRA_DETAIL_URL, detailUrl)
                    putExtra("all_stream_urls", urls)
                    putExtra("all_stream_names", names)
                    putExtra("stream_type", type)
                }
                finish()
                startActivity(intent)
            }
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPlayer() {
        // Build ExoPlayer
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        exoPlayer = player

        // Bind to the PlayerView from layout
        binding.playerView.player = player
        binding.playerView.useController = false
        binding.playerView.visibility = View.VISIBLE

        // Build MediaItem
        val mediaItemBuilder = androidx.media3.common.MediaItem.Builder()
            .setUri(streamUrl)

        if (isHls || streamUrl.contains(".m3u8")) {
            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }

        val mediaItem = mediaItemBuilder.build()
        player.setMediaItem(mediaItem)

        // Add listener
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    androidx.media3.common.Player.STATE_BUFFERING -> {
                        binding.bufferingIndicator.show()
                    }
                    androidx.media3.common.Player.STATE_READY -> {
                        binding.bufferingIndicator.hide()
                        updateAudioTrackButtonVisibility()
                    }
                    androidx.media3.common.Player.STATE_ENDED -> {
                        finish()
                    }
                    androidx.media3.common.Player.STATE_IDLE -> {
                        // no-op
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseButton(isPlaying)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.widget.Toast.makeText(
                    this@PlayerActivity,
                    "Playback error",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                finish()
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                updateAudioTrackButtonVisibility()
            }
        })

        // Resume from saved position then play
        lifecycleScope.launch {
            val savedPos = viewModel.getSavedPosition(detailUrl)
            if (savedPos > 0) {
                player.seekTo(savedPos)
            }
            player.prepare()
            player.playWhenReady = true
        }

        // Start saving position
        viewModel.startSavingPosition(
            detailUrl = detailUrl,
            title = title,
            posterUrl = posterUrl,
            contentType = contentType,
            getPosition = { player.currentPosition.coerceAtLeast(0) },
            getDuration = { player.duration.coerceAtLeast(0) }
        )

        // Update seek bar periodically
        seekBarJob?.cancel()
        seekBarJob = lifecycleScope.launch {
            while (true) {
                delay(500)
                val duration = player.duration
                val position = player.currentPosition
                if (duration > 0) {
                    binding.seekBar.max = duration.toInt()
                    binding.seekBar.progress = position.toInt()
                    binding.tvCurrentTime.text = position.formatDuration()
                    binding.tvDuration.text = duration.formatDuration()
                }
            }
        }
    }

    private fun setupControls() {
        // PlayerView tap to toggle controls
        binding.playerView.setOnClickListener {
            if (!isLocked) toggleControls()
        }

        // Tapping the lock overlay unlocks the screen
        binding.lockOverlay.setOnClickListener {
            isLocked = false
            binding.lockOverlay.visibility = View.GONE
            binding.btnLock.text = getString(com.aura.R.string.player_lock)
            // Show controls briefly after unlocking
            showControlsTemporarily()
        }

        binding.btnPlayPause.setOnClickListener {
            exoPlayer?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
            }
            scheduleHideControls()
        }

        binding.btnRewind.setOnClickListener {
            exoPlayer?.let { p ->
                p.seekTo((p.currentPosition - Constants.SEEK_INCREMENT_MS).coerceAtLeast(0))
            }
            scheduleHideControls()
        }

        binding.btnForward.setOnClickListener {
            exoPlayer?.let { p ->
                p.seekTo(p.currentPosition + Constants.SEEK_INCREMENT_MS)
            }
            scheduleHideControls()
        }

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnLock.setOnClickListener {
            isLocked = !isLocked
            binding.lockOverlay.visibility = if (isLocked) View.VISIBLE else View.GONE
            binding.btnLock.text = if (isLocked)
                getString(com.aura.R.string.player_unlock)
            else
                getString(com.aura.R.string.player_lock)
            if (!isLocked) showControlsTemporarily()
        }

        binding.btnPip.setOnClickListener { enterPiPMode() }

        binding.btnAudio.setOnClickListener {
            showAudioSelectorDialog()
            scheduleHideControls()
        }

        binding.btnSpeed.setOnClickListener {
            showSpeedSelector()
            scheduleHideControls()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer?.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Cancel auto-hide while user is dragging
                hideControlsJob?.cancel()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Restart auto-hide after user releases
                scheduleHideControls()
            }
        })

        scheduleHideControls()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isLocked) return false
                val width = binding.playerView.width
                if (e.x < width / 2) {
                    // Double-tap left = rewind
                    exoPlayer?.let { p ->
                        p.seekTo((p.currentPosition - Constants.SEEK_INCREMENT_MS).coerceAtLeast(0))
                    }
                } else {
                    // Double-tap right = forward
                    exoPlayer?.let { p ->
                        p.seekTo(p.currentPosition + Constants.SEEK_INCREMENT_MS)
                    }
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isLocked) {
                    toggleControls()
                }
                return true
            }
        })

        // Use the root FrameLayout overlay for gestures
        val root = binding.root as FrameLayout
        root.setOnTouchListener { _, event ->
            // Only intercept if controls overlay is what we're touching over
            if (!isLocked) {
                gestureDetector.onTouchEvent(event)
                handleSwipeGesture(event)
            } else {
                gestureDetector.onTouchEvent(event)
            }
            false
        }

        binding.playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            if (!isLocked) {
                handleSwipeGesture(event)
            }
            true
        }
    }

    private fun handleSwipeGesture(event: MotionEvent) {
        val screenWidth = binding.playerView.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = event.y
                touchStartX = event.x
                initialBrightness = window.attributes.screenBrightness.let {
                    if (it < 0) Settings.System.getInt(
                        contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128
                    ) / 255f else it
                }
                initialVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            }
            MotionEvent.ACTION_MOVE -> {
                val viewHeight = binding.playerView.height.toFloat().takeIf { it > 0f }
                    ?: resources.displayMetrics.heightPixels.toFloat()
                val dy = (touchStartY - event.y) / viewHeight
                if (event.x < screenWidth / 2) {
                    // Left side = brightness
                    val newBrightness = (initialBrightness + dy).coerceIn(0.01f, 1f)
                    window.attributes = window.attributes.also { it.screenBrightness = newBrightness }
                } else {
                    // Right side = volume
                    val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                    val volDelta = (dy * maxVol).toInt()
                    val newVol = (initialVolume + volDelta).coerceIn(0, maxVol)
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                }
            }
        }
    }

    private fun toggleControls() {
        isControlsVisible = !isControlsVisible
        if (isControlsVisible) {
            showControlsTemporarily()
        } else {
            binding.controlsOverlay.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { binding.controlsOverlay.hide() }
                .start()
        }
    }

    /** Shows controls and schedules auto-hide after 5 seconds. */
    private fun showControlsTemporarily() {
        hideControlsJob?.cancel()
        binding.controlsOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .withStartAction { binding.controlsOverlay.show(); binding.controlsOverlay.alpha = 0f }
            .start()
        isControlsVisible = true
        scheduleHideControls()
    }

    private fun scheduleHideControls() {
        hideControlsJob?.cancel()
        hideControlsJob = lifecycleScope.launch {
            delay(5000)  // 5 seconds
            if (isControlsVisible) {
                binding.controlsOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        binding.controlsOverlay.hide()
                        isControlsVisible = false
                    }
                    .start()
            }
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) com.aura.R.drawable.ic_pause else com.aura.R.drawable.ic_play
        )
    }

    private fun showSpeedSelector() {
        val speeds = Constants.SPEED_OPTIONS.map { "${it}x" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setItems(speeds) { _, which ->
                playbackSpeed = Constants.SPEED_OPTIONS[which]
                exoPlayer?.setPlaybackSpeed(playbackSpeed)
                binding.btnSpeed.text = "${playbackSpeed}x"
            }
            .show()
    }

    private fun updateAudioTrackButtonVisibility() {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks
        var audioTrackCount = 0
        for (group in currentTracks.groups) {
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                audioTrackCount += group.length
            }
        }
        runOnUiThread {
            binding.btnAudio.visibility = if (audioTrackCount > 1) View.VISIBLE else View.GONE
        }
    }

    private fun showAudioSelectorDialog() {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks

        data class AudioTrackInfo(
            val groupIndex: Int,
            val trackIndex: Int,
            val name: String,
            val isSelected: Boolean
        )

        val audioTracks = mutableListOf<AudioTrackInfo>()
        for ((groupIndex, group) in currentTracks.groups.withIndex()) {
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val label = format.label
                        ?: format.language?.let { java.util.Locale(it).displayLanguage }
                        ?: "Track ${audioTracks.size + 1}"
                    val isSelected = group.isTrackSelected(trackIndex)
                    audioTracks.add(AudioTrackInfo(groupIndex, trackIndex, label, isSelected))
                }
            }
        }

        if (audioTracks.isEmpty()) return

        val displayNames = audioTracks.map { it.name }.toTypedArray()
        val selectedIndex = audioTracks.indexOfFirst { it.isSelected }.takeIf { it >= 0 } ?: 0

        android.app.AlertDialog.Builder(this)
            .setTitle("Select Audio Language")
            .setSingleChoiceItems(displayNames, selectedIndex) { dialog, which ->
                val selected = audioTracks[which]
                val trackGroup = currentTracks.groups[selected.groupIndex]
                val overrides = mapOf(
                    trackGroup.mediaTrackGroup to androidx.media3.common.TrackSelectionOverride(
                        trackGroup.mediaTrackGroup,
                        listOf(selected.trackIndex)
                    )
                )
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
                    .apply {
                        for ((_, override) in overrides) {
                            addOverride(override)
                        }
                    }
                    .build()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPiPMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPiPMode, newConfig)
        if (isInPiPMode) {
            binding.controlsOverlay.hide()
        }
    }

    /**
     * Builds a Webtor.io HLS streaming page.
     * Loaded via loadDataWithBaseURL("https://webtor.io", ...) so Webtor SDK runs with proper origin context.
     */
    private fun buildWebTorrentHtml(magnet: String): String {
        val escapedMagnet = magnet.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "")
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Aura - Torrent Stream</title>
    <style>
        html, body {
            margin: 0;
            padding: 0;
            width: 100%;
            height: 100%;
            background-color: #000;
            overflow: hidden;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        #player {
            width: 100vw;
            height: 100vh;
        }
    </style>
</head>
<body>
    <div id="player" class="webtor"></div>
    <script>
        (function() {
            var magnetUri = "$escapedMagnet";
            var fileName = "";
            try {
                // Parse dn parameter from magnet link to target the exact file inside multi-file torrents (e.g. series)
                var urlParams = new URLSearchParams(magnetUri.replace('magnet:', '?'));
                var dn = urlParams.get('dn');
                if (dn) {
                    fileName = decodeURIComponent(dn);
                }
            } catch(e) {}

            window.webtor = window.webtor || [];
            var config = {
                id: 'player',
                magnet: magnetUri,
                width: '100%',
                height: '100%',
                features: {
                    title: false,
                    settings: true,
                    screenshot: false,
                    chromecast: false,
                    keyboard: true,
                    p2p: true,
                    subtitle: true
                }
            };
            
            if (fileName) {
                config.file = fileName;
            }

            window.webtor.push(config);
        })();
    </script>
    <script src="https://cdn.jsdelivr.net/npm/@webtor/embed-sdk-js/dist/index.min.js" charset="utf-8" async></script>
</body>
</html>
        """.trimIndent()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopSavingPosition()
        seekBarJob?.cancel()
        hideControlsJob?.cancel()
        hideWebViewControlsJob?.cancel()
        try {
            exoPlayer?.stop()
            exoPlayer?.release()
        } catch (_: Exception) {}
        exoPlayer = null
        embedWebView?.destroy()
        embedWebView = null
    }
}

private val sharedCookieJar = InMemoryCookieJar()

class InMemoryCookieJar : okhttp3.CookieJar {
    private val cookieStore = HashMap<String, MutableList<okhttp3.Cookie>>()

    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        cookieStore.getOrPut(url.host) { mutableListOf() }.addAll(cookies)
    }

    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        val cookies = mutableListOf<okhttp3.Cookie>()
        cookieStore[url.host]?.let { cookies.addAll(it) }
        return cookies
    }
}

