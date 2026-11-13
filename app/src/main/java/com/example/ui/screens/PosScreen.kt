package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MenuItem
import com.example.data.model.Transaction
import com.example.ui.components.SecurityPinBiometricDialog
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: WarkopViewModel,
    modifier: Modifier = Modifier
) {
    val storeProfile by viewModel.storeProfile.collectAsState()
    val menuList by viewModel.menuList.collectAsState()
    val inventoryList by viewModel.inventoryList.collectAsState()
    val transactions by viewModel.transactionList.collectAsState()
    val cartState by viewModel.cartState.collectAsState()
    val activeOperator by viewModel.activeOperator.collectAsState()

    var voidTargetTx by remember { mutableStateOf<Transaction?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }

    val categories = remember(menuList) {
        listOf("Semua") + menuList.map { it.category }.distinct()
    }

    val filteredMenu = remember(menuList, selectedCategoryFilter) {
        if (selectedCategoryFilter == "Semua") menuList
        else menuList.filter { it.category == selectedCategoryFilter }
    }

    val criticalStock = remember(inventoryList) {
        inventoryList.filter { it.minStock > 0 && it.stock <= it.minStock }
    }

    val todayTransactions = remember(transactions) {
        val startOfToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        transactions.filter { it.timestamp >= startOfToday }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Toko Tutup banner
        if (!storeProfile.isOpen) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Status: Toko Tutup (Biaya Operasional Ditunda)",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Peringatan Stok Kritis
        if (criticalStock.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDE8E4)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Peringatan Stok Kritis: Restock segera ${criticalStock.joinToString { "${it.name} (${it.stock.toInt()} ${it.unit})" }}",
                        fontSize = 12.sp,
                        color = WarningRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Kategori Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategoryFilter == cat,
                    onClick = { selectedCategoryFilter = cat },
                    label = { Text(cat, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Grid Menu Produk
        Text(
            text = "Katalog Menu",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.heightIn(max = 380.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredMenu) { menu ->
                    val inCart = cartState.items[menu.id]?.quantity ?: 0
                    val available = viewModel.calculatePortionsAvailable(menu, inventoryList) - inCart
                    val isAvailable = available > 0

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .tactilePress(enabled = isAvailable) {
                                if (isAvailable) viewModel.addToCart(menu)
                            }
                            .testTag("menu_card_${menu.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = menu.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Rp ${viewModel.formatRupiah(menu.price)}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (available > 0) "Sisa: $available porsi" else "Habis",
                                color = if (available > 5) MutedGreyText else WarningRed,
                                fontSize = 11.sp,
                                fontWeight = if (available <= 5) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card Keranjang Kasir
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keranjang Pembayaran",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (cartState.items.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearCart() }) {
                            Text("Kosongkan", color = WarningRed, fontSize = 12.sp)
                        }
                    }
                }

                if (cartState.items.isEmpty()) {
                    Text(
                        text = "Keranjang masih kosong. Pilih menu di atas untuk mulai bertransaksi.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    cartState.items.values.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Rp ${viewModel.formatRupiah(item.price)} / porsi",
                                    fontSize = 12.sp,
                                    color = MutedGreyText
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.updateCartQuantity(item.menuId, -1) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(32.dp).tactilePress()
                                ) {
                                    Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = "${item.quantity}",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )

                                OutlinedButton(
                                    onClick = { viewModel.updateCartQuantity(item.menuId, 1) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(32.dp).tactilePress()
                                ) {
                                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                text = "Rp ${viewModel.formatRupiah(item.price * item.quantity)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Diskon row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = if (cartState.discountValue > 0) cartState.discountValue.toString() else "",
                            onValueChange = {
                                val v = it.toDoubleOrNull() ?: 0.0
                                viewModel.setDiscount(cartState.discountType, v)
                            },
                            label = { Text("Diskon (${cartState.discountType})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        Row {
                            FilterChip(
                                selected = cartState.discountType == "%",
                                onClick = { viewModel.setDiscount("%", cartState.discountValue) },
                                label = { Text("%") }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = cartState.discountType == "Rp",
                                onClick = { viewModel.setDiscount("Rp", cartState.discountValue) },
                                label = { Text("Rp") }
                            )
                        }
                    }

                    if (cartState.discountAmount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Potongan Diskon:", fontSize = 13.sp, color = MutedGreyText)
                            Text("-Rp ${viewModel.formatRupiah(cartState.discountAmount)}", color = WarningRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Pembayaran:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Rp ${viewModel.formatRupiah(cartState.total)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Metode Bayar
                    Text("Metode Pembayaran", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Tunai", "QRIS", "Transfer", "Utang/Kasbon").forEach { method ->
                            FilterChip(
                                selected = cartState.paymentMethod == method,
                                onClick = { viewModel.setPaymentMethod(method) },
                                label = { Text(method, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (method == "Utang/Kasbon") AmberCoffeeContainer else MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = if (method == "Utang/Kasbon") Color(0xFF6E3E00) else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Conditional Input Fields
                    if (cartState.paymentMethod == "Tunai") {
                        OutlinedTextField(
                            value = if (cartState.cashReceived > 0) cartState.cashReceived.toLong().toString() else "",
                            onValueChange = {
                                val amt = it.toDoubleOrNull() ?: 0.0
                                viewModel.setCashReceived(amt)
                            },
                            label = { Text("Uang Tunai Diterima (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_cash_received")
                        )

                        if (cartState.cashReceived >= cartState.total && cartState.cashReceived > 0) {
                            Text(
                                text = "Kembalian: Rp ${viewModel.formatRupiah(cartState.change)}",
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    if (cartState.paymentMethod == "Utang/Kasbon") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AmberCoffeeContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Entri Piutang Kasbon / Bond",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF6E3E00)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = cartState.customerName,
                                    onValueChange = { viewModel.setCustomerName(it) },
                                    label = { Text("Nama Pelanggan (Wajib)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_debt_customer_name")
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = cartState.note,
                                    onValueChange = { viewModel.setNote(it) },
                                    label = { Text("Keterangan / Nomor Kontak / Meja") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_debt_note")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.processPayment() },
                        enabled = !cartState.isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .tactilePress()
                            .testTag("btn_process_payment")
                    ) {
                        if (cartState.isProcessing) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (cartState.paymentMethod == "Utang/Kasbon") "Catat ke Buku Kasbon" else "Selesaikan Pembayaran",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                // Struk Terakhir
                if (cartState.lastReceipt != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Struk Transaksi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.Receipt, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = cartState.lastReceipt!!,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Riwayat Transaksi Hari Ini
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Riwayat Penjualan Hari Ini (${todayTransactions.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (todayTransactions.isEmpty()) {
                    Text(
                        text = "Belum ada transaksi hari ini.",
                        fontSize = 13.sp,
                        color = MutedGreyText
                    )
                } else {
                    val sdf = remember { SimpleDateFormat("HH:mm", Locale("id")) }
                    todayTransactions.take(20).forEach { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${sdf.format(Date(tx.timestamp))} · ${tx.items.joinToString { "${it.quantity}x ${it.name}" }}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Metode: ${tx.paymentMethod} ${if (tx.customerName.isNotBlank()) "(${tx.customerName})" else ""} · Kasir: ${tx.cashierName}",
                                    fontSize = 11.sp,
                                    color = if (tx.status.contains("Kasbon")) Color(0xFFB4402B) else MutedGreyText
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Rp ${viewModel.formatRupiah(tx.total)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )

                                OutlinedButton(
                                    onClick = { voidTargetTx = tx },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                                    modifier = Modifier.height(28.dp).tactilePress().testTag("btn_void_pos_${tx.id}")
                                ) {
                                    Text("Hapus / Batal", fontSize = 11.sp)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    if (voidTargetTx != null) {
        val target = voidTargetTx!!
        SecurityPinBiometricDialog(
            title = "Hapus / Batalkan Transaksi",
            subtitle = "Transaksi Rp ${viewModel.formatRupiah(target.total)} (${target.items.joinToString { "${it.quantity}x ${it.name}" }}).\nMasukkan PIN Karyawan / Manager atau verifikasi sidik jari untuk menghapus transaksi dan mengembalikan stok.",
            pinLabel = "PIN Karyawan atau Manager",
            biometricTitle = "Hapus Transaksi",
            biometricSubtitle = "Tempelkan sidik jari karyawan/manager untuk konfirmasi",
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
