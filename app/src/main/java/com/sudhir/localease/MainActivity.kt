package com.sudhir.localease

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var currentScreen by remember { mutableStateOf("webview") }
            
            MaterialTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Web, contentDescription = "Web") },
                                label = { Text("App") },
                                selected = currentScreen == "webview",
                                onClick = { currentScreen = "webview" }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.SettingsInputAntenna, contentDescription = "Wireless") },
                                label = { Text("Wireless") },
                                selected = currentScreen == "wireless",
                                onClick = { currentScreen = "wireless" }
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (currentScreen) {
                            "webview" -> WebViewScreen("https://localeasy.vercel.app/")
                            "wireless" -> WirelessDiscoveryScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WirelessDiscoveryScreen() {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    
    var devices by remember { mutableStateOf(setOf<BluetoothDevice>()) }
    var isScanning by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(false) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        permissionGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!permissionGranted) {
            launcher.launch(permissions)
        }
    }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let { devices = devices + it }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isScanning = false
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
            if (permissionGranted) {
                @SuppressLint("MissingPermission")
                bluetoothAdapter?.cancelDiscovery()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Wireless Devices", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = {
                if (permissionGranted && bluetoothAdapter != null) {
                    devices = emptySet()
                    isScanning = true
                    @SuppressLint("MissingPermission")
                    bluetoothAdapter.startDiscovery()
                }
            }, enabled = !isScanning) {
                Icon(Icons.Default.Refresh, contentDescription = "Scan")
            }
        }

        if (isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(devices.toList()) { device ->
                @SuppressLint("MissingPermission")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = device.name ?: "Unknown Device", style = MaterialTheme.typography.titleMedium)
                        Text(text = device.address, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String) {
    var hasError by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                hasError = true
                            }
                        }
                    }
                    settings.javaScriptEnabled = true
                    loadUrl(url)
                    webViewInstance = this
                }
            },
            update = { }
        )

        if (hasError) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Failed to load page.",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        hasError = false
                        webViewInstance?.reload()
                    }) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}
