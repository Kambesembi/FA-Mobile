package com.kambesembi.famobile.ui.login

import com.kambesembi.famobile.BuildConfig
import android.view.WindowManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.kambesembi.famobile.FaApp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.AppLanguage
import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.ui.web.FaWebViewClient
import com.kambesembi.famobile.ui.web.configureForFa
import org.json.JSONObject

/**
 * Logging in: the username and password are typed into the app's own fields, and FA's login page
 * below only runs Cloudflare's check. "Log in" puts both into FA's form and submits it, so the
 * password still goes only to furaffinity.net.
 *
 * Typing straight into FA's page inside a WebView lost focus after a character or two (the page
 * reacts when the keyboard changes size), which also cut password-manager fills short. Native fields
 * don't have that problem, and password managers fill them like any app login. Finishes with
 * RESULT_OK once a page shows the logged-in avatar.
 */
class LoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var button: Button
    private lateinit var status: TextView
    private lateinit var palette: Palette
    private var reported = false

    /** A login waiting for Cloudflare's check to pass; retried every second for a minute. */
    private var waitingForCheck = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = if (intent.getBooleanExtra(EXTRA_DARK, true)) Palette.Dark else Palette.Light

        // Not in screenshots, screen recordings or the recents preview. (Debug builds: CI takes its pictures.)
        if (!BuildConfig.DEBUG) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
        }

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(palette.accent)
            progressBackgroundTintList = ColorStateList.valueOf(palette.background)
        }

        webView = WebView(this).apply {
            configureForFa((application as FaApp).container.userAgent, palette.background)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress < 100) View.VISIBLE else View.INVISIBLE
                }
            }
            webViewClient = FaWebViewClient { view, _ -> onPageFinished(view) }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
            addView(header(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(dp(12), dp(8), dp(12), dp(8))
            })
            addView(form(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(dp(12), 0, dp(12), dp(8))
            })
            addView(TextView(context).apply {
                text = getString(R.string.login_check_caption)
                setTextColor(palette.secondaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(20), 0, dp(20), dp(4))
            })
            addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, dp(3)))
            addView(webView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        // Edge to edge. The keyboard covers the WebView rather than resizing it: FA's page reacts to
        // being resized, and the fields above stay visible either way.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else finish()
        }

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(FaUrls.LOGIN)
        }
    }

    private fun onPageFinished(view: WebView) {
        view.evaluateJavascript(LOGGED_IN_JS) { result ->
            if (result == "true" && !reported) {
                reported = true
                password.setText("")
                CookieManager.getInstance().flush()
                setResult(Activity.RESULT_OK)
                finish()
                return@evaluateJavascript
            }
            // Still (or again) on the login page: show FA's message, if any, and the check.
            view.evaluateJavascript(ERROR_JS) { message ->
                val text = message?.takeIf { it != "null" && it != "\"\"" }?.let { runCatching { JSONObject("{\"m\":$it}").getString("m") }.getOrNull() }
                if (button.isEnabled.not() && waitingForCheck == 0) {
                    setBusy(false)
                    if (!text.isNullOrBlank()) showStatus(text, error = true)
                }
            }
            view.evaluateJavascript(SHOW_CHECK_JS, null)
        }
    }

    private fun logIn() {
        val user = username.text.toString().trim()
        val pass = password.text.toString()
        if (user.isEmpty() || pass.isEmpty()) {
            showStatus(getString(R.string.login_missing), error = true)
            return
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(password.windowToken, 0)
        setBusy(true)
        submit(user, pass)
    }

    private fun submit(user: String, pass: String) {
        val js = "$SUBMIT_JS(${JSONObject.quote(user)}, ${JSONObject.quote(pass)})"
        webView.evaluateJavascript(js) { result ->
            when (result?.trim('"')) {
                "sent" -> {
                    waitingForCheck = 0
                    showStatus(getString(R.string.login_working), error = false)
                }
                "check" -> {
                    // Cloudflare's check hasn't passed yet: wait for it, then submit by itself.
                    if (waitingForCheck == 0) showStatus(getString(R.string.login_check_wait), error = false)
                    if (++waitingForCheck <= 60) {
                        handler.postDelayed({ if (!isFinishing) submit(user, pass) }, 1000)
                    } else {
                        waitingForCheck = 0
                        setBusy(false)
                        showStatus(getString(R.string.login_check_wait), error = true)
                    }
                }
                else -> {
                    // FA's form isn't there (yet): the page may still be loading or showing a check.
                    waitingForCheck = 0
                    setBusy(false)
                    showStatus(getString(R.string.login_no_form), error = true)
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        button.isEnabled = !busy
        button.alpha = if (busy) 0.6f else 1f
        button.text = getString(if (busy) R.string.login_working_short else R.string.login_button)
        username.isEnabled = !busy
        password.isEnabled = !busy
    }

    private fun showStatus(text: String, error: Boolean) {
        status.text = text
        status.setTextColor(if (error) palette.error else palette.secondaryText)
        status.visibility = View.VISIBLE
    }

    private fun form(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = panel()

        username = field(R.string.login_username).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setAutofillHints(View.AUTOFILL_HINT_USERNAME)
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        password = field(R.string.login_password).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSaveEnabled = false // never into the saved instance state
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    logIn()
                    true
                } else {
                    false
                }
            }
        }
        status = TextView(context).apply {
            visibility = View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        button = Button(context).apply {
            text = getString(R.string.login_button)
            isAllCaps = false
            setTextColor(palette.onAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(palette.accent)
            }
            stateListAnimator = null
            setOnClickListener { logIn() }
        }
        addView(username, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
        addView(password, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
        addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(button, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)))
    }

    private fun field(hint: Int): EditText = EditText(this).apply {
        this.hint = getString(hint)
        setHintTextColor(palette.secondaryText)
        setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isSingleLine = true
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(palette.background)
            setStroke(dp(1), palette.border)
        }
    }

    private fun panel() = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(palette.panel)
        setStroke(dp(1), palette.border)
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(10), dp(16), dp(12))
        background = panel()
        addView(ImageButton(context).apply {
            setImageResource(R.drawable.ic_arrow_back)
            imageTintList = ColorStateList.valueOf(palette.text)
            background = null
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = getString(R.string.login_title)
                setTextColor(palette.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = getString(R.string.login_fields_hint)
                setTextColor(palette.secondaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setPadding(0, dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** The app's colours (ui/theme) as plain ARGB, for this screen without Compose. */
    private enum class Palette(
        val dark: Boolean,
        val background: Int,
        val panel: Int,
        val border: Int,
        val text: Int,
        val secondaryText: Int,
        val accent: Int,
        val onAccent: Int,
        val error: Int,
    ) {
        Dark(true, 0xFF1B1B1D.toInt(), 0xFF20242A.toInt(), 0xFF2E323A.toInt(), 0xFFEBEBEB.toInt(), 0xFFB8BCC4.toInt(), 0xFFF0C27A.toInt(), 0xFF2B1D05.toInt(), 0xFFFFB4AB.toInt()),
        Light(false, 0xFFF4F4F6.toInt(), 0xFFFFFFFF.toInt(), 0xFFDFE0E4.toInt(), 0xFF1C1C1F.toInt(), 0xFF50545C.toInt(), 0xFF8A5A0C.toInt(), 0xFFFFFFFF.toInt(), 0xFFBA1A1A.toInt()),
    }

    companion object {
        const val EXTRA_DARK = "dark"
        private const val LOGGED_IN_JS = "!!document.querySelector('img.loggedin_user_avatar')"

        /** Fills FA's own login form and submits it, unless Cloudflare's check hasn't passed yet. */
        private val SUBMIT_JS = """
            (function(user, pass) {
              if (location.protocol !== 'https:' || location.hostname !== 'www.furaffinity.net') return 'noform';
              var form = document.querySelector('form[action*="login"]');
              var name = form && form.querySelector('input[name=name]');
              var pw = form && form.querySelector('input[name=pass]');
              if (!name || !pw) return 'noform';
              name.value = user;
              pw.value = pass;
              var token = form.querySelector('input[name="cf-turnstile-response"]');
              if (form.querySelector('.cf-turnstile') && !(token && token.value)) {
                var check = document.querySelector('.cf-turnstile');
                if (check) check.scrollIntoView({block: 'center'});
                return 'check';
              }
              var submit = form.querySelector('input[type=submit], button[type=submit]');
              if (submit) submit.click(); else form.submit();
              return 'sent';
            })
        """.trimIndent().replace('\n', ' ')

        /** FA's message after a failed login ("wrong password" and the like), or null. */
        private val ERROR_JS = """
            (function() {
              var e = document.querySelector('.notice-message .redirect-message, .notice-message .section-body, .error-message, form[action*="login"] .alert, .login-error');
              return e ? e.innerText.trim().slice(0, 300) : null;
            })()
        """.trimIndent().replace('\n', ' ')

        /** Brings Cloudflare's check into view, so a checkbox is easy to find if one appears. */
        private const val SHOW_CHECK_JS =
            "(function(){var c=document.querySelector('.cf-turnstile');if(c)c.scrollIntoView({block:'center'});})()"

        fun intent(context: Context, dark: Boolean): Intent =
            Intent(context, LoginActivity::class.java).putExtra(EXTRA_DARK, dark)
    }
}
