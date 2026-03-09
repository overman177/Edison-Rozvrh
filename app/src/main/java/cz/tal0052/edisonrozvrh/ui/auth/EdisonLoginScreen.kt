package cz.tal0052.edisonrozvrh.ui.auth

import cz.tal0052.edisonrozvrh.app.Lesson
import cz.tal0052.edisonrozvrh.app.saveCurrentResults
import cz.tal0052.edisonrozvrh.app.saveSchedule
import cz.tal0052.edisonrozvrh.app.saveStudyInfo
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultsData
import cz.tal0052.edisonrozvrh.data.parser.StudyInfoData
import cz.tal0052.edisonrozvrh.data.repository.EdisonRepository

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EdisonLoginScreen(
    onDataLoaded: (List<Lesson>, CurrentResultsData?, StudyInfoData?) -> Unit
) {

    AndroidView(
        factory = { context ->

            WebView(context).apply {

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                var loadStarted = false

                webViewClient = object : WebViewClient() {

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (loadStarted) return
                        if (url.isNullOrBlank()) return
                        if (!url.contains("/wps/myportal")) return

                        val cookies = CookieManager.getInstance()
                            .getCookie("https://edison.sso.vsb.cz")

                        if (cookies.isNullOrBlank()) return

                        loadStarted = true

                        Thread {
                            val lessons = EdisonRepository.downloadDetailedSchedule(cookies)

                            if (lessons.isNullOrEmpty()) {
                                loadStarted = false
                                return@Thread
                            }

                            val currentResults = EdisonRepository.downloadCurrentResults(cookies)
                            val studyInfo = EdisonRepository.downloadStudyInfo(cookies)

                            saveSchedule(context, lessons)
                            if (currentResults != null) {
                                saveCurrentResults(context, currentResults)
                            }
                            if (studyInfo != null) {
                                saveStudyInfo(context, studyInfo)
                            }

                            view?.post {
                                onDataLoaded(lessons, currentResults, studyInfo)
                            }
                        }.start()
                    }
                }

                loadUrl("https://edison.sso.vsb.cz/wps/myportal")
            }
        }
    )
}

