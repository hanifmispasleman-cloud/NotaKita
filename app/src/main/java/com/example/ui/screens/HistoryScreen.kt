package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Transaction
import com.example.ui.components.SecurityPinBiometricDialog
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: WarkopViewModel,
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.transactionList.collectAsState()

    var voidTargetTx by remember { mutableStateOf<Transaction?>(null) }
    var timeFilter by remember { mutableStateOf("0") } // "0": Hari ini, "y": Kemarin, "7": 7 hari, "30": 30 hari, "all": Semua
    var methodFilter by remember { mutableStateOf("Semua") }

    val now = remember { System.currentTimeMillis() }
    val timeRangeMillis = remember(timeFilter) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis

        when (timeFilter) {
            "0" -> Pair(startOfToday, Long.MAX_VALUE)
            "y" -> Pair(startOfToday - 86400000L, startOfToday)
            "7" -> Pair(startOfToday - (6 * 86400000L), Long.MAX_VALUE)
            "30" -> Pair(startOfToday - (29 * 86400000L), Long.MAX_VALUE)
            else -> Pair(0L, Long.MAX_VALUE)
        }
    }

    val filteredList = remember(transactions, timeRangeMillis, methodFilter) {
        transactions.filter { tx ->
            val matchTime = tx.timestamp in timeRangeMillis.first..timeRangeMillis.second
            val matchMethod = if (methodFilter == "Semua") true else tx.paymentMethod.equals(methodFilter, ignoreCase = true)
            matchTime && matchMethod
        }
    }

    val totalItemsSold = remember(filteredList) {
        filteredList.sumOf { tx -> tx.items.sumOf { it.quantity } }
    }
    val totalRevenue = remember(filteredList) {
        filteredList.sumOf { it.total }
    }
    val avgPerTx = remember(filteredList, totalRevenue) {
        if (filteredList.isNotEmpty()) totalRevenue / filteredList.size else 0.0
    }

    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Riwayat Transaksi Penjualan",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        // FILTER WAKTU
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "0" to "Hari Ini",
                "y" to "Kemarin",
                "7" to "7 Hari",
                "30" to "30 Hari",
                "all" to "Semua"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = timeFilter == key,
                    onClick = { timeFilter = key },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        // FILTER METODE BAYAR
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Semua", "Tunai", "QRIS", "Transfer", "Utang/Kasbon").forEach { m ->
                FilterChip(
                    selected = methodFilter == m,
                    onClick = { methodFilter = m },
                    label = { Text(m, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // KPI SUMMARY
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Transaksi", fontSize = 10.sp, color = MutedGreyText)
                    Text("${filteredList.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Item Terjual", fontSize = 10.sp, color = MutedGreyText)
                    Text("$totalItemsSold", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1.4f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Omzet Netto", fontSize = 10.sp, color = MutedGreyText)
                    Text("Rp ${viewModel.formatRupiah(totalRevenue)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // DAFTAR TRANSAKSI
        if (filteredList.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Tidak ada transaksi pada filter yang dipilih.",
                    fontSize = 13.sp,
                    color = MutedGreyText,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            filteredList.forEach { tx ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("history_tx_${tx.id}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sdf.format(Date(tx.timestamp)),
                                fontSize = 12.sp,
                                color = MutedGreyText
                            )

                            Text(
                                text = tx.paymentMethod,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (tx.status.contains("Kasbon")) Color(0xFF6E3E00) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(
                                        if (tx.status.contains("Kasbon")) AmberCoffeeContainer else MintLight,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = tx.items.joinToString { "${it.quantity}x ${it.name}" },
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )

                        if (tx.customerName.isNotBlank()) {
                            Text(
                                text = "Pelanggan: ${tx.customerName} ${if (tx.note.isNotBlank()) "(${tx.note})" else ""}",
                                fontSize = 12.sp,
                                color = if (tx.status.contains("Kasbon")) WarningRed else MutedGreyText
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PJ: ${tx.cashierName} (${tx.cashierShift.ifBlank { "Shift" }})",
                                    fontSize = 11.sp,
                                    color = MutedGreyText
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Rp ${viewModel.formatRupiah(tx.total)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedButton(
                                    onClick = { voidTargetTx = tx },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                                    modifier = Modifier.height(30.dp).tactilePress().testTag("btn_void_history_${tx.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus Transaksi", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hapus", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (voidTargetTx != null) {
        val target = voidTargetTx!!
        SecurityPinBiometricDialog(
            title = "Hapus Transaksi",
            subtitle = "Transaksi ${sdf.format(Date(target.timestamp))} senilai Rp ${viewModel.formatRupiah(target.total)}.\nMasukkan PIN karyawan/manager atau gunakan sidik jari untuk menghapus transaksi dan mengembalikan stok.",
            pinLabel = "PIN Karyawan / Manager",
            biometricTitle = "Otorisasi Hapus Transaksi",
            biometricSubtitle = "Tempelkan jari untuk memverifikasi penghapusan transaksi",
            confirmButtonText = "Hapus Transaksi",
            onDismiss = { voidTargetTx = null },
            onConfirmPin = { pin ->
                val ok = viewModel.verifyEmployeePinAndVoid(target, inputPin = pin, useBiometric = false)
                if (ok) voidTargetTx = null
                ok
            },
            onBiometricSuccess = {
                viewModel.verifyEmployeePinAndVoid(target, inputPin = "", useBiometric = true)
                voidTargetTx = null
            }
        )
    }
}
