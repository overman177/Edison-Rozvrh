package cz.tal0052.edisonrozvrh.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tal0052.edisonrozvrh.data.auth.loadEdisonCredentials
import cz.tal0052.edisonrozvrh.data.auth.saveEdisonCredentials
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeInboxFetchResult
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeLoginClient
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeOkHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EmailVerificationTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storedCredentials = remember { loadEdisonCredentials(context) }
    var username by remember { mutableStateOf(storedCredentials?.username.orEmpty()) }
    var password by remember { mutableStateOf(storedCredentials?.password.orEmpty()) }
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RoundcubeInboxFetchResult?>(null) }

    fun refreshInbox() {
        if (isLoading) return

        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank() || password.isBlank()) {
            result = RoundcubeInboxFetchResult(
                success = false,
                usernameUsed = normalizedUsername,
                statusCode = 0,
                responseHtml = "",
                responseHeaders = emptyMap(),
                errorMessage = "Vypln username a heslo pro Roundcube."
            )
            return
        }

        isLoading = true

        scope.launch {
            val freshResult = withContext(Dispatchers.IO) {
                RoundcubeLoginClient(
                    transport = RoundcubeOkHttpTransport()
                ).loginAndFetchInbox(
                    username = normalizedUsername,
                    password = password
                )
            }

            result = freshResult
            if (freshResult.success) {
                saveEdisonCredentials(context, normalizedUsername, password)
            }
            isLoading = false
        }
    }

    val statusTitle = when {
        isLoading -> "Nacitam inbox"
        result == null -> "Pripraveno"
        result?.success == true -> "Inbox nacten"
        else -> "Nacteni selhalo"
    }
    val statusBody = when {
        isLoading -> "Roundcube overuje login a stahuje zpravy."
        result?.success == true -> result?.inboxPage?.countText?.ifBlank { "Zpravy byly nacteny." }.orEmpty()
        result != null -> result?.errorMessage?.ifBlank { "Roundcube vratil chybu." }.orEmpty()
        else -> "Nahoře muzes kdykoli kliknout na Obnovit."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    text = "Roundcube inbox",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = ::refreshInbox,
                enabled = !isLoading,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                if (isLoading) {
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

        StatusCard(
            title = statusTitle,
            body = statusBody,
            isLoading = isLoading,
            isSuccess = result?.success == true
        )

        CredentialCard(
            username = username,
            password = password,
            onUsernameChange = { username = it },
            onPasswordChange = { password = it }
        )

        result?.let { loadedResult ->
            InboxSummaryCard(result = loadedResult)
            loadedResult.inboxPage?.messages?.take(6)?.let { messages ->
                if (messages.isNotEmpty()) {
                    InboxPreviewCard(messages = messages)
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    isLoading: Boolean,
    isSuccess: Boolean
) {
    val accent = when {
        isLoading -> MaterialTheme.colorScheme.primary
        isSuccess -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.18f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = title,
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun CredentialCard(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Prihlaseni",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Predvyplneno z centralniho loginu. Kdyz vrati 401, uprav to tady a zkus Obnovit znovu.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Roundcube username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Roundcube heslo") },
                singleLine = true,
                visualTransformation = if (password.isEmpty()) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InboxSummaryCard(result: RoundcubeInboxFetchResult) {
    val accent = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (result.success) "Spojeni s Roundcube je aktivni" else "Roundcube vratil chybu",
                color = accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Pouzity ucet: ${result.usernameUsed.ifBlank { "-" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Text(
                text = "HTTP status: ${result.statusCode}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            result.inboxPage?.let { inbox ->
                if (inbox.folders.isNotEmpty()) {
                    Text(
                        text = "Slozky: ${inbox.folders.joinToString { folder -> folder.name }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            result.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun InboxPreviewCard(
    messages: List<cz.tal0052.edisonrozvrh.data.parser.RoundcubeInboxMessage>
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Inbox preview",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            messages.forEach { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (message.unread) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (message.unread) "NEW" else "MAIL",
                            color = if (message.unread) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = message.sender.ifBlank { "Neznamy odesilatel" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = if (message.unread) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = message.subject,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOf(message.date, message.size).filter { it.isNotBlank() }.joinToString(" | "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
