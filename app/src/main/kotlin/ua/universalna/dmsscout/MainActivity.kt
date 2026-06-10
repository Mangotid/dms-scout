package ua.universalna.dmsscout

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.jsoup.Jsoup

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = result.data?.data?.let { arrayOf(it) }
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
        web.addJavascriptInterface(Bridge(), "Android")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                return when (url.scheme) {
                    "tel", "mailto" -> { startActivity(Intent(Intent.ACTION_VIEW, url)); true }
                    "http", "https" -> { startActivity(Intent(Intent.ACTION_VIEW, url)); true }
                    else -> false
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                fileChooser.launch(Intent.createChooser(intent, "Оберіть CSV"))
                return true
            }
        }

        web.loadUrl("file:///android_asset/index.html")
    }

    inner class Bridge {
        @JavascriptInterface
        fun saveFile(filename: String, content: String) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Збережено: Download/$filename", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Помилка збереження: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun share(text: String) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, "Поділитись"))
        }

        @JavascriptInterface
        fun scrapeWebsite(url: String, callbackId: String) {
            Thread {
                try {
                    val foundEmails = mutableSetOf<String>()
                    val foundPhones = mutableSetOf<String>()
                    val foundStaff = mutableSetOf<String>()

                    var targetUrl = url.trim()
                    if (!targetUrl.startsWith("http")) {
                        targetUrl = "https://$targetUrl"
                    }

                    // 1. Скануємо головну сторінку
                    val docMain = Jsoup.connect(targetUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .timeout(12000)
                        .followRedirects(true)
                        .get()

                    extractContactsAndStaff(docMain, foundEmails, foundPhones, foundStaff)

                    // 2. Шукаємо лінк на сторінку контактів чи команди
                    val contactElement = docMain.select("a[href]").firstOrNull { el ->
                        val href = el.attr("href").lowercase()
                        val linkText = el.text().lowercase()
                        href.contains("contact") || href.contains("kontak") || href.contains("about") || href.contains("team") || href.contains("personal") ||
                        linkText.contains("контакт") || linkText.contains("про нас") || linkText.contains("команда") || linkText.contains("наші люди")
                    }

                    // Якщо знайшли підсторінку — скануємо її додатково
                    if (contactElement != null) {
                        val contactUrl = contactElement.absUrl("href")
                        if (contactUrl.isNotEmpty() && contactUrl != targetUrl) {
                            try {
                                val docContacts = Jsoup.connect(contactUrl)
                                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                    .timeout(8000)
                                    .get()
                                
                                extractContactsAndStaff(docContacts, foundEmails, foundPhones, foundStaff)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    val staffString = foundStaff.joinToString("; ").replace("\"", "\\\"")
                    val jsonResult = """{
                        "emails": [${foundEmails.joinToString(",") { "\"$it\"" }}],
                        "phones": [${foundPhones.joinToString(",") { "\"$it\"" }}],
                        "hr_staff": "$staffString"
                    }"""

                    runOnUiThread {
                        web.evaluateJavascript("if(window.onScrapeSuccess) window.onScrapeSuccess('$callbackId', $jsonResult)", null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        web.evaluateJavascript("if(window.onScrapeError) window.onScrapeError('$callbackId', '${e.message?.replace("'", "\\'")}')", null)
                    }
                }
            }.start()
        }

        private fun extractContactsAndStaff(
            doc: org.jsoup.nodes.Document, 
            emails: MutableSet<String>, 
            phones: MutableSet<String>,
            staff: MutableSet<String>
        ) {
            // Збір посилань href
            doc.select("a[href^=mailto:]").forEach { emails.add(it.attr("href").replace("mailto:", "").trim().lowercase()) }
            doc.select("a[href^=tel:]").forEach { 
                val rawPhone = it.attr("href").replace("tel:", "").trim()
                if (rawPhone.length >= 9) phones.add(rawPhone)
            }

            val fullText = doc.text()

            // Пошук email регулярним виразом
            val emailMatcher = java.util.regex.Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}").matcher(fullText)
            while (emailMatcher.find()) {
                emails.add(emailMatcher.group().lowercase())
            }

            // Пошук українських номерів телефонів
            val phoneMatcher = java.util.regex.Pattern.compile("(?:\\+?38)?(?:\\s*\\(?0\\d{2}\\)?\\s*\\d{3}\\s*\\d{2}\\s*\\d{2}|\\s*\\(?0\\d{2}\\)?\\s*\\d{3}\\s*\\d{1}\\s*\\d{3})").matcher(fullText)
            while (phoneMatcher.find()) {
                val cleaned = phoneMatcher.group().replace(Regex("[^+\\d]"), "")
                if (cleaned.length >= 10) phones.add(cleaned)
            }

            // Пошук згадок ключових осіб (CEO, HR, HRD, рекрутери тощо)
            val textBlocks = doc.select("p, span, div, li, h2, h3, h4")
            for (block in textBlocks) {
                val text = block.text().trim()
                if (text.length in 10..120) {
                    val lowerText = text.lowercase()
                    if (lowerText.contains("hr") || lowerText.contains("hrd") || 
                        lowerText.contains("recruiter") || lowerText.contains("рекрутер") || 
                        lowerText.contains("персонал") || lowerText.contains("кадри") ||
                        lowerText.contains("директор з") || lowerText.contains("ceo") || lowerText.contains("керівник")) {
                        staff.add(text)
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
