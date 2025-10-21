package com.creativebird.esp32

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.creativebird.esp32.ui.theme.Esp32Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.*

class MainActivity : ComponentActivity() {
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestBluetoothPermissions()

        setContent {
            Esp32Theme {
                VacuumControlScreen(
                    onConnect = { deviceAddress -> connectToDevice(deviceAddress) },
                    onDisconnect = { disconnectDevice() },
                    onCommand = { command -> sendCommand(command) }
                )
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun connectToDevice(deviceAddress: String): Boolean {
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }

            val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

            bluetoothSocket = device?.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream

            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    private fun disconnectDevice() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            bluetoothSocket = null
            outputStream = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun sendCommand(command: String) {
        try {
            outputStream?.write(command.toByteArray())
            outputStream?.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectDevice()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VacuumControlScreen(
    onConnect: (String) -> Boolean,
    onDisconnect: () -> Unit,
    onCommand: (String) -> Unit
) {
    var isConnected by remember { mutableStateOf(false) }
    var deviceAddress by remember { mutableStateOf("") }
    var speed by remember { mutableFloatStateOf(50f) }
    var isVacuumOn by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ESP32 Staubsauger", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected)
                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isConnected) "Verbunden" else "Nicht verbunden",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isConnected) Color(0xFF4CAF50) else Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isConnected) {
                        OutlinedTextField(
                            value = deviceAddress,
                            onValueChange = { deviceAddress = it },
                            label = { Text("ESP32 MAC Adresse") },
                            placeholder = { Text("z.B. AA:BB:CC:DD:EE:FF") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isConnected = withContext(Dispatchers.IO) {
                                        onConnect(deviceAddress)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Verbinden")
                        }
                    } else {
                        Button(
                            onClick = {
                                onDisconnect()
                                isConnected = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Trennen")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Vacuum Control
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Saugfunktion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            isVacuumOn = !isVacuumOn
                            onCommand(if (isVacuumOn) "V1\n" else "V0\n")
                        },
                        enabled = isConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVacuumOn) Color(0xFFFF5722) else Color(0xFF4CAF50)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = if (isVacuumOn) "Saugen STOPPEN" else "Saugen STARTEN",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Speed Control
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Geschwindigkeit: ${speed.toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        onValueChangeFinished = {
                            onCommand("S${speed.toInt()}\n")
                        },
                        valueRange = 0f..100f,
                        enabled = isConnected
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Direction Control
            Text(
                text = "Steuerung",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Up button
            DirectionButton(
                text = "↑",
                onClick = { onCommand("F\n") },
                onRelease = { onCommand("X\n") },
                enabled = isConnected
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Left, Stop, Right buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DirectionButton(
                    text = "←",
                    onClick = { onCommand("L\n") },
                    onRelease = { onCommand("X\n") },
                    enabled = isConnected
                )

                DirectionButton(
                    text = "■",
                    onClick = { onCommand("X\n") },
                    onRelease = { },
                    enabled = isConnected,
                    modifier = Modifier.size(80.dp)
                )

                DirectionButton(
                    text = "→",
                    onClick = { onCommand("R\n") },
                    onRelease = { onCommand("X\n") },
                    enabled = isConnected
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Down button
            DirectionButton(
                text = "↓",
                onClick = { onCommand("B\n") },
                onRelease = { onCommand("X\n") },
                enabled = isConnected
            )
        }
    }
}

@Composable
fun DirectionButton(
    text: String,
    onClick: () -> Unit,
    onRelease: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(80.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
    }
}