package cz.tal0052.edisonrozvrh.ui.home

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.tal0052.edisonrozvrh.data.auth.loadEdisonCredentials
import cz.tal0052.edisonrozvrh.data.parser.RoundcubeInboxMessage
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeInboxFetchResult
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeLoginClient
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeMessageDetailFetchResult
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeOkHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EmailVerificationTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentials = remember { loadEdisonCredentials(context) }
    val client = remember { RoundcubeLoginClient(transport = RoundcubeOkHttpTransport()) }
    var inboxResult by remember { mutableStateOf<RoundcubeInboxFetchResult?>(null) }
    var isInboxLoading by remember { mutableStateOf(false) }
    var openedMessage by remember { mutableStateOf<RoundcubeInboxMessage?>(null) }
    var detailResult by remember { mutableStateOf<RoundcubeMessageDetailFetchResult?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }

    fun refreshInbox() {
        if (isInboxLoading) return

        val storedCredentials = credentials
        if (storedCredentials == null) {
            inboxResult = RoundcubeInboxFetchResult(
                success = false,
                usernameUsed = "",
                statusCode = 0,
                responseHtml = "",
                responseHeaders = emptyMap(),
                errorMessage = "Chybi centralni prihlaseni aplikace."
            )
            return
        }

        isInboxLoading = true
        openedMessage = null
        detailResult = null

        scope.launch {
            val freshResult = withContext(Dispatchers.IO) {
                client.loginAndFetchInbox(
                    username = storedCredentials.username,
                    password = storedCredentials.password
                )
            }

            inboxResult = freshResult
            isInboxLoading = false
        }
    }

    fun openMessage(message: RoundcubeInboxMessage) {
        if (isDetailLoading) return

        val storedCredentials = credentials
        if (storedCredentials == null) {
            detailResult = RoundcubeMessageDetailFetchResult(
                success = false,
                usernameUsed = "",
                statusCode = 0,
                responseHtml = "",
                responseHeaders = emptyMap(),
                errorMessage = "Chybi centralni prihlaseni aplikace."
            )
            openedMessage = message
            return
        }

        openedMessage = message
        detailResult = null
        isDetailLoading = true

        scope.launch {
            val fetchedDetail = withContext(Dispatchers.IO) {
                client.fetchMessageDetail(
                    username = storedCredentials.username,
                    password = storedCredentials.password,
                    detailUrl = message.detailUrl
                )
            }

            detailResult = fetchedDetail
            isDetailLoading = false
        }
    }

    LaunchedEffect(credentials?.username) {
        if (credentials != null && inboxResult == null && !isInboxLoading) {
            refreshInbox()
        }
    }

    val messages = inboxResult?.inboxPage?.messages.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Email",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = inboxResult?.inboxPage?.countText?.ifBlank { "Roundcube inbox" } ?: "Roundcube inbox",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = ::refreshInbox,
                enabled = !isInboxLoading,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                if (isInboxLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(16.dp)
                        )
                        Text("Obnovuji")
                    }
                } else {
                    Text("Obnovit")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                isInboxLoading && inboxResult == null -> {
                    InboxInfoCard(
                        title = "Nacitam inbox",
                        body = "Roundcube overuje login a stahuje seznam zprav."
                    )
                }

                inboxResult?.success == false -> {
                    InboxInfoCard(
                        title = "Nacteni selhalo",
                        body = inboxResult?.errorMessage ?: "Roundcube vratil chybu.",
                        isError = true
                    )
                }

                messages.isEmpty() -> {
                    InboxInfoCard(
                        title = "Inbox je prazdny",
                        body = "Zkus Obnovit nebo otevri jinou slozku v dalsim kroku."
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = messages,
                            key = { message -> message.uid }
                        ) { message ->
                            InboxMessageRow(
                                message = message,
                                onOpenMessage = { openMessage(message) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }

    if (openedMessage != null) {
        MessageDetailDialog(
            isLoading = isDetailLoading,
            detailResult = detailResult,
            onDismiss = {
                openedMessage = null
                detailResult = null
                isDetailLoading = false
            }
        )
    }
}

@Composable
private fun InboxInfoCard(
    title: String,
    body: String,
    isError: Boolean = false
) {
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun InboxMessageRow(
    message: RoundcubeInboxMessage,
    onOpenMessage: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)
        ),
        border = BorderStroke(
            1.dp,
            if (message.unread) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenMessage)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(10.dp)
                    .background(
                        color = if (message.unread) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        },
                        shape = CircleShape
                    )
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = message.sender.ifBlank { "Neznamy odesilatel" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = if (message.unread) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = message.date,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                Text(
                    text = message.subject,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = buildList {
                    if (message.size.isNotBlank()) add(message.size)
                    if (message.hasAttachment) add("Priloha")
                    if (message.flagged) add("Flag")
                }.joinToString(" | ")

                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageDetailDialog(
    isLoading: Boolean,
    detailResult: RoundcubeMessageDetailFetchResult?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Email detail",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Zavrit",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onDismiss)
                    )
                }

                if (isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(18.dp)
                        )
                        Text(
                            text = "Nacitam obsah emailu",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                    return@Column
                }

                if (detailResult?.success != true || detailResult.messageDetail == null) {
                    Text(
                        text = detailResult?.errorMessage ?: "Detail zpravy se nepodarilo nacist.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    return@Column
                }

                val detail = detailResult.messageDetail
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = detail.subject,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Od: ${detail.from}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Komu: ${detail.to}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Datum: ${detail.date}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )

                    if (detail.attachments.isNotEmpty()) {
                        Text(
                            text = "Prilohy: ${detail.attachments.joinToString { attachment -> attachment.name }}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                    ) {
                        EmailHtmlBodyView(
                            html = detail.bodyHtml,
                            fallbackText = detail.bodyText,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmailHtmlBodyView(
    html: String,
    fallbackText: String,
    modifier: Modifier = Modifier
) {
    val documentHtml = remember(html, fallbackText) {
        buildString {
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
            append(
                """
                <style>
                    html, body { margin: 0; padding: 0; background: #ffffff; color: #111111; font-family: sans-serif; }
                    body { padding: 12px; line-height: 1.45; }
                    img { max-width: 100%; height: auto; }
                    table { max-width: 100% !important; }
                    * { box-sizing: border-box; max-width: 100%; }
                    .pre { white-space: pre-wrap; font-family: monospace; }
                    a { color: #0b7fab; }
                </style>
                """.trimIndent()
            )
            append("</head><body>")
            if (html.isBlank()) {
                append("<div class=\"pre\">")
                append(escapeHtml(fallbackText))
                append("</div>")
            } else {
                append(html)
            }
            append("</body></html>")
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://posta.vsb.cz",
                documentHtml,
                "text/html",
                "utf-8",
                null
            )
        }
    )
}

private fun escapeHtml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
        .replace("\n", "<br>")
}
