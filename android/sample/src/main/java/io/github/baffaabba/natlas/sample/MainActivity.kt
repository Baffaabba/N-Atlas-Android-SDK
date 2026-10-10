package io.github.baffaabba.natlas.sample

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.baffaabba.natlas.Language
import io.github.baffaabba.natlas.NAtlasClient
import io.github.baffaabba.natlas.Role

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                SampleScreen(viewModel())
            }
        }
    }
}

private val Language.label: String
    get() = when (this) {
        Language.HAUSA -> "Hausa"
        Language.IGBO -> "Igbo"
        Language.YORUBA -> "Yorùbá"
        Language.ENGLISH -> "Nigerian English"
    }

@Composable
fun SampleScreen(vm: SampleViewModel) {
    val context = LocalContext.current
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRationale by remember { mutableStateOf(false) }
    var micDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micDenied = false
            pendingMicAction?.invoke()
        } else {
            micDenied = true
        }
        pendingMicAction = null
    }

    // Ask for the microphone only when the user taps a button that needs it, with a reason first.
    fun withMic(action: () -> Unit) {
        if (NAtlasClient.hasMicPermission(context)) {
            action()
        } else {
            pendingMicAction = action
            showRationale = true
        }
    }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.transcribeFile(uri)
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false; pendingMicAction = null },
            title = { Text("Allow the microphone") },
            text = { Text("The app needs the microphone to hear you. Nothing is recorded until you tap Start.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false; pendingMicAction = null }) { Text("Not now") }
            },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("N-ATLAS Sample", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Speak or type in Hausa, Igbo, Yorùbá or Nigerian English. N-ATLAS writes down what " +
                    "you say and replies in text.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SettingsCard(vm)

            if (micDenied) {
                ErrorText(
                    "Without the microphone, the app can't hear you. You can turn it on in " +
                        "Settings > Apps > N-ATLAS Sample > Permissions.",
                )
            }

            Section("Transcribe a file") {
                Text("Pick a recording. We'll write down what was said.", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { pickAudio.launch(arrayOf("audio/*")) },
                    enabled = vm.isConfigured && vm.fileState !is TaskState.Working,
                ) { Text("Pick an audio file") }
                when (val s = vm.fileState) {
                    TaskState.Idle -> Unit
                    is TaskState.Working -> Busy(s.label)
                    is TaskState.Done -> Text(s.text)
                    is TaskState.Failed -> ErrorText(s.message)
                }
            }

            Section("Live transcription") {
                Text(
                    "Tap Start and speak. Text appears about once a second, and is finished when you pause.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (vm.liveRunning) {
                    Button(onClick = vm::stopLive) { Text("Stop recording") }
                } else {
                    Button(
                        onClick = { withMic(vm::startLive) },
                        enabled = vm.isConfigured && vm.voicePhase == VoicePhase.IDLE,
                    ) { Text("Start recording") }
                }
                vm.liveStatus?.let { Busy(it) }
                if (vm.liveFinalText.isNotEmpty() || vm.livePartialText.isNotEmpty()) {
                    Text(vm.liveFinalText)
                    if (vm.livePartialText.isNotEmpty()) {
                        Text(vm.livePartialText, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.outline)
                    }
                } else if (!vm.liveRunning && vm.liveError == null) {
                    Text("Nothing yet.", color = MaterialTheme.colorScheme.outline)
                }
                vm.liveError?.let { ErrorText(it) }
            }

            Section("Chat with N-ATLAS") {
                if (vm.chatHistory.isEmpty() && vm.chatReply.isEmpty() && !vm.chatBusy) {
                    Text("Ask anything, for example \"Sannu! Yaya kake?\"", color = MaterialTheme.colorScheme.outline)
                }
                vm.chatHistory.forEach { m ->
                    val who = if (m.role == Role.USER) "You" else "N-ATLAS"
                    Text("$who: ${m.content}")
                }
                if (vm.chatBusy) {
                    if (vm.chatReply.isEmpty()) Busy("Thinking…") else Text("N-ATLAS: ${vm.chatReply}")
                }
                vm.chatError?.let { ErrorText(it) }
                OutlinedTextField(
                    value = vm.chatInput,
                    onValueChange = { vm.chatInput = it },
                    label = { Text("Your message") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = vm::sendChat,
                        enabled = vm.isConfigured && !vm.chatBusy && vm.chatInput.isNotBlank(),
                    ) { Text("Ask N-ATLAS") }
                    OutlinedButton(onClick = vm::clearChat, enabled = !vm.chatBusy && vm.chatHistory.isNotEmpty()) {
                        Text("Clear")
                    }
                }
            }

            Section("Talk to N-ATLAS") {
                Text(
                    "Say something. N-ATLAS replies in text. It has no voice yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = vm.voiceAutoStop,
                        onCheckedChange = { vm.voiceAutoStop = it },
                        enabled = vm.voicePhase == VoicePhase.IDLE,
                    )
                    Text("Stop listening when I pause")
                }
                when (vm.voicePhase) {
                    VoicePhase.IDLE -> Button(
                        onClick = { withMic(vm::startVoice) },
                        enabled = vm.isConfigured && !vm.liveRunning,
                    ) { Text("Talk to N-ATLAS") }
                    VoicePhase.LISTENING -> {
                        Busy("Listening…")
                        Button(onClick = vm::stopVoice) { Text("I'm done talking") }
                    }
                    VoicePhase.THINKING -> Busy("Thinking…")
                    VoicePhase.REPLYING -> Unit
                }
                if (vm.voiceHeard.isNotEmpty()) Text("You said: ${vm.voiceHeard}")
                if (vm.voiceReply.isNotEmpty()) Text("N-ATLAS: ${vm.voiceReply}")
                vm.voiceError?.let { ErrorText(it) }
            }

            Section("About") {
                Text(NAtlasClient.ATTRIBUTION)
                Text(
                    "Free use of the N-ATLAS models is limited to 1,000 active users in any 30 days. " +
                        "Beyond that, an app needs a commercial licence from Awarri (datasupport@awarri.com).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "This app only uses the N-ATLAS models (NCAIR1). Audio is sent to the server you set " +
                        "above and is not stored on this phone. SDK version ${NAtlasClient.VERSION}.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(vm: SampleViewModel) {
    Section("Settings") {
        OutlinedTextField(
            value = vm.baseUrl,
            onValueChange = vm::updateBaseUrl,
            label = { Text("Server address") },
            placeholder = { Text("https://your-server.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.apiKey,
            onValueChange = vm::updateApiKey,
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!vm.isConfigured) {
            Text(
                "Add the server address and API key to start. Ask whoever runs your N-ATLAS server for them, " +
                    "or set natlas.baseUrl and natlas.apiKey in local.properties.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("Language", style = MaterialTheme.typography.labelLarge)
        Column {
            Language.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { lang ->
                        FilterChip(
                            selected = vm.language == lang,
                            onClick = { vm.language = lang },
                            label = { Text(lang.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Busy(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
}
