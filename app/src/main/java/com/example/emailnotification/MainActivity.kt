package com.example.emailnotification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = getSharedPreferences("settings", MODE_PRIVATE)
        requestNotificationPermission()
        setContent {
            EmailNotificationTheme {
                EmailNotificationScreen(
                    initialHost = settings.getString("host", "imap.gmail.com").orEmpty(),
                    initialUsername = settings.getString("username", "").orEmpty(),
                    initialPassword = SecureSettings.getPassword(this),
                    isMonitoring = settings.getBoolean("is_monitoring", false) || ForwardService.isRunning,
                    batteryExempt = isBatteryExempt(),
                    onStart = { host, username, password -> startMonitoring(host, username, password) },
                    onStop = { stopMonitoring() },
                    onBatterySettings = { requestBatteryOptimizationExemption() }
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun startMonitoring(host: String, username: String, password: String) {
        val cleanPassword = password.trim().replace(" ", "")
        if (host.isBlank() || username.isBlank() || cleanPassword.isBlank()) {
            Toast.makeText(this, "Isi host IMAP, email, dan App Password", Toast.LENGTH_SHORT).show()
            return
        }
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putString("host", host.trim())
            .putString("username", username.trim())
            .putBoolean("is_monitoring", true)
            .putLong("monitoring_start_time", System.currentTimeMillis() - 10 * 60 * 1000L)
            .apply()
        SecureSettings.putPassword(this, cleanPassword)
        val intent = Intent(this, ForwardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        RestartWorker.schedule(this)
        Toast.makeText(this, "Monitoring dimulai", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("is_monitoring", false).apply()
        stopService(Intent(this, ForwardService::class.java))
        RestartWorker.cancel(this)
        Toast.makeText(this, "Monitoring dihentikan", Toast.LENGTH_SHORT).show()
    }

    private fun isBatteryExempt(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(packageName)

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

@androidx.compose.runtime.Composable
private fun EmailNotificationTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    MaterialTheme(content = content)
}

@androidx.compose.runtime.Composable
private fun EmailNotificationScreen(
    initialHost: String,
    initialUsername: String,
    initialPassword: String,
    isMonitoring: Boolean,
    batteryExempt: Boolean,
    onStart: (String, String, String) -> Unit,
    onStop: () -> Unit,
    onBatterySettings: () -> Unit
) {
    var host by remember { mutableStateOf(initialHost) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    val scrollState = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Email Notifications", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Dapatkan notifikasi Android untuk email baru, langsung dari perangkat.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Monitoring email", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isMonitoring) "Aktif · memeriksa setiap 60 detik" else "Tidak aktif",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isMonitoring, onCheckedChange = { if (it) onStart(host, username, password) else onStop() })
                }
            }
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Koneksi IMAP", style = MaterialTheme.typography.titleLarge)
                    Text("Untuk Gmail, gunakan App Password, bukan password utama akun.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(host, { host = it }, label = { Text("IMAP host") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(username, { username = it }, label = { Text("Alamat email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        password, { password = it }, label = { Text("App Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onStart(host, username, password) },
                        enabled = !isMonitoring,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Mulai monitoring") }
                    OutlinedButton(
                        onClick = onStop,
                        enabled = isMonitoring,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Hentikan monitoring") }
                }
            }
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Keandalan background", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (batteryExempt) "Battery optimization sudah dinonaktifkan untuk aplikasi ini."
                        else "Nonaktifkan battery optimization agar Android tidak menghentikan monitoring.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onBatterySettings, enabled = !batteryExempt, modifier = Modifier.fillMaxWidth()) {
                        Text(if (batteryExempt) "Battery sudah dioptimalkan" else "Buka pengaturan battery")
                    }
                    Text(
                        "Aplikasi akan mencoba aktif kembali setelah reboot dan memakai WorkManager sebagai fallback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
