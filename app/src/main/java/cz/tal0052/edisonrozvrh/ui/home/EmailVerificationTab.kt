package cz.tal0052.edisonrozvrh.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
    var verificationResult by remember { mutableStateOf<RoundcubeInboxFetchResult?>(null) }

    fun runVerification() {
        if (isLoading) return

        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank() || password.isBlank()) {
            verificationResult = RoundcubeInboxFetchResult(
                success = false,
                usernameUsed = "",
                statusCode = 0,
                responseHtml = "",
                responseHeaders = emptyMap(),
                errorMessage = "Vypln username a heslo pro Roundcube."
            )
            return
        }

        isLoading = true
        verificationResult = null

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RoundcubeLoginClient(
                    transport = RoundcubeOkHttpTransport()
                ).loginAndFetchInbox(
                    username = normalizedUsername,
                    password = password
                )
            }

            verificationResult = result
            if (result.success) {
                saveEdisonCredentials(context, normalizedUsername, password)
            }
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Email",
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Roundcube verification",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tahle verze zatim umi jen ověřit login a načíst inbox přes uložené credentials.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
                Text(
                    text = "Predvyplneno z centralniho loginu. Kdyz Roundcube vrati 401, muzes to tady rovnou upravit a zkusit znovu.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Roundcube username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Roundcube heslo") },
                    singleLine = true,
                    visualTransformation = if (password.isEmpty()) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = ::runVerification,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(18.dp)
                            )
                            Text("Overuji Roundcube")
                        }
                    } else {
                        Text("Overit login a inbox")
                    }
                }
            }
        }

        verificationResult?.let { result ->
            Spacer(modifier = Modifier.height(12.dp))
            VerificationResultCard(result = result)
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun VerificationResultCard(result: RoundcubeInboxFetchResult) {
    val accent = if (result.success) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (result.success) "Roundcube overeni proslo" else "Roundcube overeni selhalo",
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Pouzite jmeno: ${result.usernameUsed.ifBlank { "-" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Text(
                text = "HTTP status: ${result.statusCode}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            result.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            result.inboxPage?.let { inbox ->
                Text(
                    text = inbox.countText.ifBlank { "Inbox byl nacten." },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (inbox.folders.isNotEmpty()) {
                    Text(
                        text = "Slozky: ${inbox.folders.joinToString { folder -> folder.name }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }

                inbox.messages.take(6).forEach { message ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
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
                                text = listOf(message.date, message.size).filter { it.isNotBlank() }.joinToString(" • "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
