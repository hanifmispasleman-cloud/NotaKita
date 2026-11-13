package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DrawerDestination
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CashierDashboardScreen(
    viewModel: WarkopViewModel,
    onNavigate: (DrawerDestination) -> Unit,
    onLockSwitchRole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storeProfile by viewModel.storeProfile.collectAsState()
    val activeOperator by viewModel.activeOperator.collectAsState()
    val activeShift by viewModel.activeShift.collectAsState()
    val transactions by viewModel.transactionList.collectAsState()
    val inventoryList by viewModel.inventoryList.collectAsState()

    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val shiftTransactionsToday = remember(transactions, activeOperator, startOfToday) {
        transactions.filter {
            it.timestamp >= startOfToday && it.cashierName.equals(activeOperator, ignoreCase = true)
        }
    }

    val shiftRevenue = remember(shiftTransactionsToday) {
        shiftTransactionsToday.sumOf { it.total }
    }
    val shiftKasbon = remember(shiftTransactionsToday) {
        shiftTransactionsToday
            .filter { it.paymentMethod.contains("Kasbon", true) || it.paymentMethod.contains("Utang", true) }
            .sumOf { it.total }
    }
    val criticalStock = remember(inventoryList) {
        inventoryList.filter { it.minStock > 0 && it.stock <= it.minStock }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // KARTU PROFIL KASIR PIKET
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AmberCoffeeContainer),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cashier_dashboard_header")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DASHBOARD KASIR LAPANGAN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7A4400)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeOperator,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3E2200)
                        )
                        Text(
                            text = "Waktu Piket: $activeShift",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF5E3500)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (storeProfile.isOpen) ForestGreen else WarningRed
                    ) {
                        Text(
                            text = if (storeProfile.isOpen) "TOKO BUKA" else "TOKO TUTUP",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${storeProfile.storeName} · Di bawah pengawasan Manager ${storeProfile.managerName}",
                    fontSize = 11.5.sp,
                    color = Color(0xFF5E3500).copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (criticalStock.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDE8E4)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stok menipis: ${criticalStock.joinToString { "${it.name} (${it.stock.toInt()} ${it.unit})" }}. Hubungi Manager untuk otorisasi restock.",
                        fontSize = 12.sp,
                        color = WarningRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // KPI SHIFT KASIR HARI INI
        Text(
            text = "Ringkasan Tugas Shift Hari Ini",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1.2f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Setoran Shift Ini", fontSize = 11.sp, color = MutedGreyText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rp ${viewModel.formatRupiah(shiftRevenue)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(0.8f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Transaksi", fontSize = 11.sp, color = MutedGreyText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${shiftTransactionsToday.size}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Kasbon Shift", fontSize = 11.sp, color = MutedGreyText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rp ${viewModel.formatRupiah(shiftKasbon)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberCoffee
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AKSI CEPAT OPERASIONAL KASIR
        Text(
            text = "Akses Operasional Kasir",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CashierQuickActionCard(
                title = "Mesin Kasir (POS)",
                subtitle = "Layani pesanan baru",
                icon = Icons.Default.PointOfSale,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = { onNavigate(DrawerDestination.POS) },
                modifier = Modifier.weight(1f).testTag("quick_pos")
            )
            CashierQuickActionCard(
                title = "Buku Kasbon",
                subtitle = "Catat / cicil utang",
                icon = Icons.Default.ReceiptLong,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = { onNavigate(DrawerDestination.DEBTS) },
                modifier = Modifier.weight(1f).testTag("quick_debts")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CashierQuickActionCard(
                title = "Cek Stok & Resep",
                subtitle = "Restock butuh otorisasi",
                icon = Icons.Default.Inventory2,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = { onNavigate(DrawerDestination.INVENTORY) },
                modifier = Modifier.weight(1f).testTag("quick_inventory")
            )
            CashierQuickActionCard(
                title = "Riwayat Shift",
                subtitle = "Lihat & batal transaksi",
                icon = Icons.Default.History,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = { onNavigate(DrawerDestination.HISTORY) },
                modifier = Modifier.weight(1f).testTag("quick_history")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AKTIVITAS TRANSAKSI SHIFT INI
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Transaksi Terakhir Anda Hari Ini",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (shiftTransactionsToday.isEmpty()) {
                    Text(
                        text = "Belum ada transaksi pada shift Anda hari ini. Ketuk Mesin Kasir (POS) untuk memulai.",
                        fontSize = 12.sp,
                        color = MutedGreyText
                    )
                } else {
                    shiftTransactionsToday.take(8).forEach { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${timeFmt.format(Date(tx.timestamp))} · ${tx.items.joinToString { "${it.quantity}x ${it.name}" }}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Metode: ${tx.paymentMethod}",
                                    fontSize = 11.sp,
                                    color = MutedGreyText
                                )
                            }
                            Text(
                                text = "Rp ${viewModel.formatRupiah(tx.total)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onLockSwitchRole,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .tactilePress()
                .testTag("btn_cashier_switch_role")
        ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ganti Shift / Kunci Layar", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CashierQuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.tactilePress(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = contentColor
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = contentColor.copy(alpha = 0.78f)
            )
        }
    }
}
