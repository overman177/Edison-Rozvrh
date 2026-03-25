package cz.tal0052.edisonrozvrh.ui.home

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.tal0052.edisonrozvrh.R
import cz.tal0052.edisonrozvrh.data.auth.loadEdisonCredentials
import cz.tal0052.edisonrozvrh.data.parser.RoundcubeInboxMessage
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeCookieProvider
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
    val transport = remember { RoundcubeOkHttpTransport() }
    val client = remember(transport) { RoundcubeLoginClient(transport = transport) }
    val listState = rememberLazyListState()
    var inboxResult by remember { mutableStateOf<RoundcubeInboxFetchResult?>(null) }
    var isInboxLoading by remember { mutableStateOf(false) }
    var openedMessage by remember { mutableStateOf<RoundcubeInboxMessage?>(null) }
    var detailResult by remember { mutableStateOf<RoundcubeMessageDetailFetchResult?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    var remoteContentRequested by remember { mutableStateOf(false) }
    var inboxMessages by remember { mutableStateOf<List<RoundcubeInboxMessage>>(emptyList()) }
    var visibleMessages by remember { mutableStateOf<List<RoundcubeInboxMessage>>(emptyList()) }
    var globallySortedMessages by remember { mutableStateOf<List<RoundcubeInboxMessage>>(emptyList()) }
    var inboxPageSize by remember { mutableStateOf(50) }
    var currentPage by remember { mutableStateOf(1) }
    var flaggedFirst by remember { mutableStateOf(false) }

    fun showMessagesForCurrentMode(
        messages: List<RoundcubeInboxMessage>,
        targetPage: Int = currentPage
    ) {
        if (!flaggedFirst) {
            globallySortedMessages = emptyList()
            visibleMessages = messages
            return
        }

        val sortedMessages = reorderInboxMessages(messages, flaggedFirst = true)
        globallySortedMessages = sortedMessages
        val maxPage = ((sortedMessages.size - 1) / inboxPageSize) + 1
        val resolvedPage = targetPage.coerceIn(1, maxPage.coerceAtLeast(1))
        currentPage = resolvedPage
        visibleMessages = sliceInboxPage(
            messages = sortedMessages,
            currentPage = resolvedPage,
            pageSize = inboxPageSize
        )
    }

    fun refreshInbox(targetPage: Int = currentPage) {
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
        remoteContentRequested = false
        currentPage = targetPage.coerceAtLeast(1)

        scope.launch {
            val freshResult = withContext(Dispatchers.IO) {
                if (flaggedFirst) {
                    client.loginAndFetchAllInboxPages(
                        username = storedCredentials.username,
                        password = storedCredentials.password
                    )
                } else {
                    client.loginAndFetchInbox(
                        username = storedCredentials.username,
                        password = storedCredentials.password,
                        page = currentPage
                    )
                }
            }

            val freshMessages = freshResult.inboxPage?.messages.orEmpty()
            if (freshMessages.isNotEmpty() && !flaggedFirst) {
                inboxPageSize = freshMessages.size
            }

            inboxResult = freshResult
            inboxMessages = freshMessages
            showMessagesForCurrentMode(
                messages = freshMessages,
                targetPage = targetPage
            )
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
        remoteContentRequested = false
        val updatedMessages = inboxMessages.map { current ->
            if (current.uid == message.uid) current.copy(unread = false) else current
        }
        inboxMessages = updatedMessages
        showMessagesForCurrentMode(updatedMessages)

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

        scope.launch(Dispatchers.IO) {
            client.setMessageFlag(
                username = storedCredentials.username,
                password = storedCredentials.password,
                uid = message.uid,
                flag = "read"
            )
        }
    }

    fun toggleFlag(message: RoundcubeInboxMessage) {
        val storedCredentials = credentials ?: return
        val newFlaggedState = !message.flagged

        val updatedMessages = inboxMessages.map { current ->
            if (current.uid == message.uid) current.copy(flagged = newFlaggedState) else current
        }
        inboxMessages = updatedMessages
        showMessagesForCurrentMode(updatedMessages)

        scope.launch {
            val actionResult = withContext(Dispatchers.IO) {
                client.setMessageFlag(
                    username = storedCredentials.username,
                    password = storedCredentials.password,
                    uid = message.uid,
                    flag = if (newFlaggedState) "flagged" else "unflagged"
                )
            }

            if (!actionResult.success) {
                val revertedMessages = inboxMessages.map { current ->
                    if (current.uid == message.uid) current.copy(flagged = message.flagged) else current
                }
                inboxMessages = revertedMessages
                showMessagesForCurrentMode(revertedMessages)
            }
        }
    }

    fun loadRemoteContent(message: RoundcubeInboxMessage) {
        if (isDetailLoading) return

        val storedCredentials = credentials
        if (storedCredentials == null) return

        openedMessage = message
        isDetailLoading = true
        remoteContentRequested = true

        scope.launch {
            val fetchedDetail = withContext(Dispatchers.IO) {
                client.fetchMessageDetail(
                    username = storedCredentials.username,
                    password = storedCredentials.password,
                    detailUrl = message.detailUrl,
                    loadRemoteContent = true
                )
            }

            detailResult = fetchedDetail
            isDetailLoading = false
        }
    }

    LaunchedEffect(credentials?.username) {
        if (credentials != null && inboxResult == null && !isInboxLoading) {
            refreshInbox(1)
        }
    }

    val messages = visibleMessages
    val pageInfo = if (flaggedFirst && inboxMessages.isNotEmpty()) {
        buildInboxPageInfo(
            total = inboxMessages.size,
            currentPage = currentPage,
            pageSize = inboxPageSize
        )
    } else {
        remember(inboxResult?.inboxPage?.countText, currentPage, messages.size) {
            parseInboxPageInfo(
                countText = inboxResult?.inboxPage?.countText.orEmpty(),
                currentPage = currentPage,
                loadedCount = messages.size
            )
        }
    }
    val inboxLabel = if (pageInfo != null) "INBOX ${pageInfo.total}" else "INBOX"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_full),
            contentDescription = "Logo Edison Rozvrh",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(118.dp),
            contentScale = ContentScale.FillWidth
        )

        Spacer(modifier = Modifier.height(10.dp))

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
                    text = inboxLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    onClick = ::refreshInbox,
                    enabled = !isInboxLoading
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

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (flaggedFirst) {
                            Color(0xFFFF3B30).copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (flaggedFirst) {
                            Color(0xFFFF3B30).copy(alpha = 0.44f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                        }
                    ),
                    modifier = Modifier.clickable {
                        flaggedFirst = !flaggedFirst
                        currentPage = 1
                        scope.launch {
                            listState.scrollToItem(0)
                        }
                        refreshInbox(1)
                    }
                ) {
                    Text(
                        text = "Sort: ${if (flaggedFirst) "Dulezite nahoru" else "Bez trideni"}",
                        color = if (flaggedFirst) {
                            Color(0xFFFF6B61)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
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
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = messages,
                            key = { message -> message.uid }
                        ) { message ->
                            InboxMessageRow(
                                message = message,
                                onOpenMessage = { openMessage(message) },
                                onToggleFlag = { toggleFlag(message) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }

        if (pageInfo != null) {
            Spacer(modifier = Modifier.height(12.dp))
            InboxPaginationControls(
                pageInfo = pageInfo,
                isLoading = isInboxLoading,
                onPreviousPage = {
                    if (pageInfo.hasPreviousPage) {
                        if (flaggedFirst && globallySortedMessages.isNotEmpty()) {
                            currentPage = pageInfo.previousPage
                            visibleMessages = sliceInboxPage(
                                messages = globallySortedMessages,
                                currentPage = pageInfo.previousPage,
                                pageSize = inboxPageSize
                            )
                            scope.launch { listState.scrollToItem(0) }
                        } else {
                            refreshInbox(pageInfo.previousPage)
                        }
                    }
                },
                onNextPage = {
                    if (pageInfo.hasNextPage) {
                        if (flaggedFirst && globallySortedMessages.isNotEmpty()) {
                            currentPage = pageInfo.nextPage
                            visibleMessages = sliceInboxPage(
                                messages = globallySortedMessages,
                                currentPage = pageInfo.nextPage,
                                pageSize = inboxPageSize
                            )
                            scope.launch { listState.scrollToItem(0) }
                        } else {
                            refreshInbox(pageInfo.nextPage)
                        }
                    }
                }
            )
        }
    }

    if (openedMessage != null) {
        MessageDetailDialog(
            isLoading = isDetailLoading,
            detailResult = detailResult,
            fallbackBaseUrl = openedMessage?.detailUrl.orEmpty(),
            cookieProvider = transport,
            remoteContentRequested = remoteContentRequested,
            onLoadRemoteContent = openedMessage?.let { message ->
                { loadRemoteContent(message) }
            },
            onDismiss = {
                openedMessage = null
                detailResult = null
                isDetailLoading = false
                remoteContentRequested = false
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
private fun InboxPaginationControls(
    pageInfo: InboxPageInfo,
    isLoading: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPreviousPage,
                enabled = pageInfo.hasPreviousPage && !isLoading
            ) {
                Text(pageInfo.previousRangeLabel)
            }

            Text(
                text = "${pageInfo.start}-${pageInfo.end}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Button(
                onClick = onNextPage,
                enabled = pageInfo.hasNextPage && !isLoading
            ) {
                Text(pageInfo.nextRangeLabel)
            }
        }
    }
}

@Composable
private fun InboxMessageRow(
    message: RoundcubeInboxMessage,
    onOpenMessage: () -> Unit,
    onToggleFlag: () -> Unit
) {
    val flaggedAccent = Color(0xFFFF3B30)
    val containerColor = when {
        message.flagged -> flaggedAccent.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)
    }
    val borderColor = when {
        message.flagged -> flaggedAccent.copy(alpha = if (message.unread) 0.48f else 0.34f)
        message.unread -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenMessage)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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

                BookmarkToggle(
                    flagged = message.flagged,
                    onClick = onToggleFlag
                )
            }

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
                    if (message.hasAttachment) add("Priloha")
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
private fun BookmarkToggle(
    flagged: Boolean,
    onClick: () -> Unit
) {
    val accent = if (flagged) {
        Color(0xFFFF3B30)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(width = 24.dp, height = 28.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bookmarkPath = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.08f)
                lineTo(size.width * 0.78f, size.height * 0.08f)
                lineTo(size.width * 0.78f, size.height * 0.90f)
                lineTo(size.width * 0.50f, size.height * 0.72f)
                lineTo(size.width * 0.22f, size.height * 0.90f)
                close()
            }

            if (flagged) {
                drawPath(
                    path = bookmarkPath,
                    color = accent
                )
            } else {
                drawPath(
                    path = bookmarkPath,
                    color = accent,
                    style = Stroke(width = size.minDimension * 0.10f)
                )
            }
        }
    }
}

@Composable
private fun MessageDetailDialog(
    isLoading: Boolean,
    detailResult: RoundcubeMessageDetailFetchResult?,
    fallbackBaseUrl: String,
    cookieProvider: RoundcubeCookieProvider,
    remoteContentRequested: Boolean,
    onLoadRemoteContent: (() -> Unit)?,
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
                val canLoadRemoteImages = remember(detailResult.responseHtml, detail.bodyHtml) {
                    containsPotentialRemoteContent(detailResult.responseHtml, detail.bodyHtml)
                }

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

                    if (canLoadRemoteImages && !remoteContentRequested && onLoadRemoteContent != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Obrazky jsou zatim blokovane kvuli soukromi.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(onClick = onLoadRemoteContent) {
                                Text("Nacist obrazky")
                            }
                        }
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
                            baseUrl = detail.bodyBaseUrl.ifBlank { fallbackBaseUrl },
                            cookieProvider = cookieProvider,
                            allowRemoteContent = remoteContentRequested || !canLoadRemoteImages,
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
    baseUrl: String,
    cookieProvider: RoundcubeCookieProvider,
    allowRemoteContent: Boolean,
    modifier: Modifier = Modifier
) {
    val resolvedBaseUrl = remember(baseUrl) {
        baseUrl.ifBlank { "https://posta.vsb.cz/roundcube/" }
    }
    val cookieValues = remember(cookieProvider, resolvedBaseUrl, html, fallbackText) {
        cookieProvider.cookiesFor(resolvedBaseUrl)
    }
    val documentHtml = remember(html, fallbackText, resolvedBaseUrl) {
        buildString {
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
            append("<base href=\"")
            append(escapeHtmlAttribute(resolvedBaseUrl))
            append("\">")
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
                settings.allowContentAccess = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieValues.forEach { cookie ->
                cookieManager.setCookie("https://posta.vsb.cz", cookie)
                cookieManager.setCookie(resolvedBaseUrl, cookie)
            }
            cookieManager.flush()
            webView.settings.blockNetworkImage = !allowRemoteContent

            webView.loadDataWithBaseURL(
                resolvedBaseUrl,
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

private fun escapeHtmlAttribute(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

private fun containsPotentialRemoteContent(responseHtml: String, bodyHtml: String): Boolean {
    val visibleRemoteWarning = responseHtml.contains("remote resources have been blocked", ignoreCase = true) &&
        !responseHtml.contains("id=\"remote-objects-message\" class=\"notice\" style=\"display: none\"", ignoreCase = true)

    if (visibleRemoteWarning) return true

    val remoteImageRegex = Regex("""(?i)<img[^>]+src\s*=\s*["'](?:https?:)?//""")
    val anyImageRegex = Regex("""(?i)<img\b""")
    val remoteStyleRegex = Regex("""(?i)url\((["']?)(?:https?:)?//""")

    return remoteImageRegex.containsMatchIn(bodyHtml) ||
        remoteStyleRegex.containsMatchIn(bodyHtml) ||
        anyImageRegex.containsMatchIn(bodyHtml)
}

private fun reorderInboxMessages(
    messages: List<RoundcubeInboxMessage>,
    flaggedFirst: Boolean
): List<RoundcubeInboxMessage> {
    if (!flaggedFirst) return messages

    val (flaggedMessages, regularMessages) = messages.partition { it.flagged }
    return flaggedMessages + regularMessages
}

private fun sliceInboxPage(
    messages: List<RoundcubeInboxMessage>,
    currentPage: Int,
    pageSize: Int
): List<RoundcubeInboxMessage> {
    if (messages.isEmpty()) return emptyList()

    val safePageSize = pageSize.coerceAtLeast(1)
    val startIndex = ((currentPage - 1).coerceAtLeast(0)) * safePageSize
    if (startIndex >= messages.size) return emptyList()

    val endIndex = (startIndex + safePageSize).coerceAtMost(messages.size)
    return messages.subList(startIndex, endIndex)
}

private fun buildInboxPageInfo(
    total: Int,
    currentPage: Int,
    pageSize: Int
): InboxPageInfo? {
    if (total <= 0) return null

    val safePageSize = pageSize.coerceAtLeast(1)
    val maxPage = ((total - 1) / safePageSize) + 1
    val resolvedPage = currentPage.coerceIn(1, maxPage)
    val start = ((resolvedPage - 1) * safePageSize) + 1
    val end = (start + safePageSize - 1).coerceAtMost(total)

    return InboxPageInfo(
        start = start,
        end = end,
        total = total,
        currentPage = resolvedPage,
        pageSize = safePageSize
    )
}

private data class InboxPageInfo(
    val start: Int,
    val end: Int,
    val total: Int,
    val currentPage: Int,
    val pageSize: Int
) {
    val hasPreviousPage: Boolean get() = start > 1
    val hasNextPage: Boolean get() = end < total
    val previousPage: Int get() = (currentPage - 1).coerceAtLeast(1)
    val nextPage: Int get() = currentPage + 1

    val previousRangeLabel: String
        get() {
            if (!hasPreviousPage) return "Predchozi"
            val prevStart = (start - pageSize).coerceAtLeast(1)
            val prevEnd = start - 1
            return "$prevStart-$prevEnd"
        }

    val nextRangeLabel: String
        get() {
            if (!hasNextPage) return "Dalsi"
            val nextStart = end + 1
            val nextEnd = (end + pageSize).coerceAtMost(total)
            return "$nextStart-$nextEnd"
        }
}

private fun parseInboxPageInfo(
    countText: String,
    currentPage: Int,
    loadedCount: Int
): InboxPageInfo? {
    val match = COUNT_RANGE_REGEX.find(countText) ?: return null
    val start = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val end = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
    val total = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return null
    val pageSize = (end - start + 1).coerceAtLeast(loadedCount.coerceAtLeast(1))

    return InboxPageInfo(
        start = start,
        end = end,
        total = total,
        currentPage = currentPage,
        pageSize = pageSize
    )
}

private val COUNT_RANGE_REGEX = Regex("""(\d+)\s+to\s+(\d+)\s+of\s+(\d+)""", RegexOption.IGNORE_CASE)
