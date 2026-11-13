package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DebtAccount
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(
    viewModel: WarkopViewModel,
    modifier: Modifier = Modifier
) {
    val debtList by viewModel.debtList.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("Semua") }

    // Dialog state for paying debt installment
    var payingAccount by remember { mutableStateOf<DebtAccount?>(null) }
    var payAmountInput by remember { mutableStateOf("") }
    var payNoteInput by remember { mutableStateOf("Cicilan kasbon") }

    var expandedAccountId by remember { mutableStateOf<String?>(null) }

    val filteredDebts = remember(debtList, searchQuery, statusFilter) {
        debtList.filter {
            val matchQuery = it.customerName.contains(searchQuery, ignoreCase = true)
            val matchStatus = when (statusFilter) {
                "Belum Lunas" -> it.remainingDebt > 0
                "Lunas" -> it.remainingDebt <= 0
                else -> true
            }
            matchQuery && matchStatus
        }
    }

    val totalOutstandingDebt = remember(debtList) {
        debtList.sumOf { it.remainingDebt }
    }
    val activeDebtorsCount = remember(debtList) {
        debtList.count { it.remainingDebt > 0 }
    }

    val sdf = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale("id")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // KPI BANNER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmberCoffeeContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1.2f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Total Piutang Kasbon",
                        fontSize = 11.sp,
                        color = Color(0xFF6E3E00),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Rp ${viewModel.formatRupiah(totalOutstandingDebt)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5E3500)
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(0.8f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Pelanggan Kasbon",
                        fontSize = 11.sp,
                        color = MutedGreyText
                    )
                    Text(
                        text = "$activeDebtorsCount Orang",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SEARCH & FILTER
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari nama pelanggan kasbon...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("search_debt_customer")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Semua", "Belum Lunas", "Lunas").forEach { st ->
                FilterChip(
                    selected = statusFilter == st,
                    onClick = { statusFilter = st },
                    label = { Text(st, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // DAFTAR KASBON PELANGGAN
        if (filteredDebts.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tidak ada catatan utang / bond yang aktif.",
                        fontSize = 14.sp,
                        color = MutedGreyText
                    )
                }
            }
        } else {
            filteredDebts.forEach { debt ->
                val isExpanded = expandedAccountId == debt.id
                val isPaidOff = debt.remainingDebt <= 0

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPaidOff) MaterialTheme.colorScheme.surface else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("debt_card_${debt.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = debt.customerName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Sisa Utang: Rp ${viewModel.formatRupiah(debt.remainingDebt)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isPaidOff) SuccessGreen else WarningRed
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isPaidOff) "LUNAS" else if (debt.remainingDebt < debt.totalDebt) "CICILAN" else "BELUM LUNAS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPaidOff) SuccessGreen else WarningRed,
                                    modifier = Modifier
                                        .background(
                                            if (isPaidOff) Color(0xFFE8F5E9) else Color(0xFFFDE8E4),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )

                                IconButton(onClick = {
                                    expandedAccountId = if (isExpanded) null else debt.id
                                }) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Rincian"
                                    )
                                }
                            }
                        }

                        // Tombol Cicil / Lunas jika belum lunas
                        if (!isPaidOff) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    payingAccount = debt
                                    payAmountInput = debt.remainingDebt.toLong().toString()
                                    payNoteInput = "Pelunasan kasbon"
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth().tactilePress().testTag("btn_pay_debt_${debt.id}")
                            ) {
                                Icon(Icons.Default.Payment, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bayar / Cicil Kasbon", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }

                        // DETAIL RIWAYAT UTANG & CICILAN
                        AnimatedVisibility(visible = isExpanded) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Rincian Utang Masuk:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MutedGreyText
                                )
                                debt.entries.forEach { entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${sdf.format(Date(entry.timestamp))} · ${entry.note}",
                                            fontSize = 11.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "Rp ${viewModel.formatRupiah(entry.amount)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (debt.payments.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Riwayat Cicilan / Pembayaran:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                    debt.payments.forEach { pay ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${sdf.format(Date(pay.timestamp))} via ${pay.cashierName} · ${pay.note}",
                                                fontSize = 11.sp,
                                                color = SuccessGreen,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "-Rp ${viewModel.formatRupiah(pay.amount)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SuccessGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // DIALOG PEMBAYARAN KASBON
    if (payingAccount != null) {
        val target = payingAccount!!
        AlertDialog(
            onDismissRequest = { payingAccount = null },
            title = {
                Text("Pembayaran Kasbon: ${target.customerName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sisa tanggungan: Rp ${viewModel.formatRupiah(target.remainingDebt)}",
                        fontWeight = FontWeight.SemiBold,
                        color = WarningRed,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = payAmountInput,
                        onValueChange = { payAmountInput = it },
                        label = { Text("Jumlah Dibayar (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = payNoteInput,
                        onValueChange = { payNoteInput = it },
                        label = { Text("Keterangan Pembayaran") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = payAmountInput.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            viewModel.payDebt(target.id, amt, payNoteInput)
                            payingAccount = null
                        }
                    },
                    modifier = Modifier.tactilePress()
                ) {
                    Text("Konfirmasi Pembayaran")
                }
            },
            dismissButton = {
                TextButton(onClick = { payingAccount = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
