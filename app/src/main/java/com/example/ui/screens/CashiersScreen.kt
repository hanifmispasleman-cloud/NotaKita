package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel

@Composable
fun CashiersScreen(
    viewModel: WarkopViewModel,
    modifier: Modifier = Modifier
) {
    val storeProfile by viewModel.storeProfile.collectAsState()
    val cashierList by viewModel.cashierList.collectAsState()
    val activeOperator by viewModel.activeOperator.collectAsState()
    val activeShift by viewModel.activeShift.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var newCashierName by remember { mutableStateOf("") }
    var newCashierPin by remember { mutableStateOf("") }
    var newCashierShift by remember { mutableStateOf("Pagi (07:00 - 15:00)") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // PETUGAS KASIR PIKET SAAT INI
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Petugas Kasir Sedang Piket:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = activeOperator,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Waktu Piket: $activeShift",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Setiap transaksi penjualan dan pembayaran utang akan dicatat atas nama petugas ini.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // GANTI PETUGAS PIKET
        Text(
            text = "Pilih Operator / Kasir yang Bertugas",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Option 1: Manager
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = activeOperator == storeProfile.managerName || activeOperator == "Manager",
                                onClick = { viewModel.setActiveOperator(storeProfile.managerName, "Manager (Full Shift)") }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "${storeProfile.managerName} (Akun Manager / Pemilik)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(text = "Hak akses penuh seluruh sistem", fontSize = 11.sp, color = MutedGreyText)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Sub-akun kasir
                    cashierList.forEach { cashier ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = activeOperator == cashier.name,
                                    onClick = { viewModel.setActiveOperator(cashier.name, cashier.shift) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = cashier.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Shift: ${cashier.shift} · PIN Kasir aktif",
                                        fontSize = 11.sp,
                                        color = MutedGreyText
                                    )
                                }
                            }

                        IconButton(
                            onClick = { viewModel.deleteCashier(cashier.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Hapus", tint = WarningRed, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showAddDialog = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).tactilePress().testTag("btn_add_cashier_modal")
        ) {
            Icon(Icons.Default.PersonAdd, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tambah Sub-Akun Kasir Baru")
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Pendaftaran Sub-Akun Kasir", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sub-akun kasir berada di bawah warkop ${storeProfile.storeName} dan dapat langsung bertugas di kasir.",
                        fontSize = 12.sp,
                        color = MutedGreyText
                    )
                    OutlinedTextField(
                        value = newCashierName,
                        onValueChange = { newCashierName = it },
                        label = { Text("Nama Kasir") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_cashier_name")
                    )
                    OutlinedTextField(
                        value = newCashierPin,
                        onValueChange = { newCashierPin = it },
                        label = { Text("PIN Kasir (4-6 angka)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_cashier_pin")
                    )
                    OutlinedTextField(
                        value = newCashierShift,
                        onValueChange = { newCashierShift = it },
                        label = { Text("Waktu Piket / Shift") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_cashier_shift")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Pagi (07:00 - 15:00)", "Sore (15:00 - 23:00)").forEach { preset ->
                            FilterChip(
                                selected = newCashierShift == preset,
                                onClick = { newCashierShift = preset },
                                label = { Text(preset.substringBefore(" "), fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCashier(newCashierName, newCashierPin, newCashierShift)
                        showAddDialog = false
                        newCashierName = ""
                        newCashierPin = ""
                    },
                    modifier = Modifier.tactilePress()
                ) {
                    Text("Daftarkan Kasir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
