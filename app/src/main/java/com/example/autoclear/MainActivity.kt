package com.example.autoclear

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SettingsAccessibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.autoclear.ui.theme.AutoClearTheme
import com.example.autoclear.ui.theme.DeepAqua
import com.example.autoclear.ui.theme.Graphite
import com.example.autoclear.ui.theme.MintGlow
import com.example.autoclear.ui.theme.SlatePanel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AutoClearTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AutoClearScreen()
                }
            }
        }
    }
}

@Composable
private fun AutoClearScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsRepository = remember(context) { SettingsRepository(context) }

    var featureEnabled by remember { mutableStateOf(settingsRepository.isEnabled()) }
    var serviceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                featureEnabled = settingsRepository.isEnabled()
                serviceEnabled = isAccessibilityServiceEnabled(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = padding.calculateTopPadding() + 20.dp,
                end = 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeroCard(
                    featureEnabled = featureEnabled,
                    serviceEnabled = serviceEnabled,
                )
            }

            item {
                ToggleCard(
                    enabled = featureEnabled,
                    onToggle = { enabled ->
                        featureEnabled = enabled
                        settingsRepository.setEnabled(enabled)
                    },
                )
            }

            item {
                SetupCard(
                    serviceEnabled = serviceEnabled,
                    onOpenAccessibility = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    },
                )
            }

            item {
                NotesCard()
            }
        }
    }
}

@Composable
private fun HeroCard(
    featureEnabled: Boolean,
    serviceEnabled: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlatePanel),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Graphite, DeepAqua, SlatePanel),
                    ),
                )
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.babag_clear_launcher_foreground),
                        contentDescription = "BabaG Clear logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                width = 1.dp,
                                color = MintGlow.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(18.dp),
                            ),
                    )

                    Column {
                        Text(
                            text = "BabaG Clear",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "stealth recents cleaner",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = "Shows a right-edge circular clear control when the system overview is open, then sweeps away your recent apps with the BabaG black-and-green look.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        label = if (featureEnabled) "Feature on" else "Feature off",
                        active = featureEnabled,
                    )
                    StatusChip(
                        label = if (serviceEnabled) "Accessibility ready" else "Needs accessibility",
                        active = serviceEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Overlay switch",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Turns the right-side Clear recents button on or off without removing BabaG Clear.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Graphite,
                    checkedTrackColor = MintGlow,
                ),
            )
        }
    }
}

@Composable
private fun SetupCard(
    serviceEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SettingsAccessibility,
                    contentDescription = null,
                    tint = MintGlow,
                )
                Text(
                    text = "Setup",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            StepRow("1", "Enable the AutoClear accessibility service.")
            StepRow("2", "Open Recents like normal.")
            StepRow("3", "Tap the circular Clear control on the right edge.")

            Text(
                text = if (serviceEnabled) {
                    "Accessibility is enabled, so the overlay should appear when Recents is detected."
                } else {
                    "Accessibility is still off. Turn it on first or the overlay cannot appear."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenAccessibility) {
                    Text("Open accessibility settings")
                }
                OutlinedButton(onClick = onOpenAccessibility) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Refresh status")
                }
            }
        }
    }
}

@Composable
private fun NotesCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "What this app is doing",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Android does not let normal apps directly edit the system Recents UI. BabaG Clear uses an accessibility overlay to place a tappable button over the Recents screen and then automates the clear action as far as the launcher allows.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
            Text(
                text = "It is fully open as a Kotlin + Compose Android Studio project, so we can keep tuning the overlay position, logo, and clearing behavior for your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun StepRow(
    step: String,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = MintGlow.copy(alpha = 0.16f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelLarge,
                color = MintGlow,
            )
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatusChip(
    label: String,
    active: Boolean,
) {
    val background = if (active) {
        MintGlow.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    }

    val foreground = if (active) MintGlow else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .background(
                color = background,
                shape = RoundedCornerShape(100.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()

    val component = ComponentName(context, RecentsAccessibilityService::class.java).flattenToString()
    return enabledServices.split(':').any { it.equals(component, ignoreCase = true) }
}

@Preview(showBackground = true)
@Composable
private fun AutoClearPreview() {
    AutoClearTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HeroCard(
                featureEnabled = true,
                serviceEnabled = true,
            )
        }
    }
}
