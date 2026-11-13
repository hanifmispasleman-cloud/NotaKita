package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: WarkopViewModel,
    onNavigateToReports: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val storeProfile by viewModel.storeProfile.collectAsState()
    val transactions by viewModel.transactionList.collectAsState()

    var showEditStoreDialog by remember { mutableStateOf(false) }
    var editStoreName by remember(storeProfile) { mutableStateOf(storeProfile.storeName) }
    var editManagerName by remember(storeProfile) { mutableStateOf(storeProfile.managerName) }
    var editManagerPin by remember(storeProfile) { mutableStateOf(storeProfile.managerPin) }

    val now = remember { System.currentTimeMillis() }
    val dayFormat = remember { SimpleDateFormat("EEE, d", Locale("id")) }

    // 7 days aggregation
    val dailyStats = remember(transactions, storeProfile) {
        val list = mutableListOf<Triple<String, Double, Double>>() // DayLabel, Revenue, NetProfit

        for (d in 6 downTo 0) {
            val c = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -d)
            }
            val startOfDay = c.timeInMillis
            val endOfDay = startOfDay + 86400000L

            val dayTx = transactions.filter { it.timestamp in startOfDay until endOfDay }
            val rev = dayTx.sumOf { it.total }
            val costBahan = dayTx.sumOf { tx ->
                tx.items.sumOf { it.cost * it.quantity }
            }
            val opsCost = if (storeProfile.isOpen) storeProfile.operatingCost.dailyTotal() else 0.0
            val net = rev - costBahan - opsCost

            list.add(Triple(dayFormat.format(Date(startOfDay)), rev, net))
        }
        list
    }

    val todayRevenue = remember(dailyStats) { dailyStats.lastOrNull()?.second ?: 0.0 }
    val weeklyRevenue = remember(dailyStats) { dailyStats.sumOf { it.second } }
    val weeklyNetProfit = remember(dailyStats) { dailyStats.sumOf { it.third } }
    val weeklyOpsAllocation = remember(storeProfile) {
        if (storeProfile.isOpen) storeProfile.operatingCost.dailyTotal() * 7.0 else 0.0
    }

    // Menu terlaris
    val topSellingItems = remember(transactions) {
        val map = mutableMapOf<String, Int>()
        transactions.forEach { tx ->
            tx.items.forEach { item ->
                map[item.name] = (map[item.name] ?: 0) + item.quantity
            }
        }
        map.entries.sortedByDescending { it.value }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // KARTU DETAIL TOKO & MANAGER DI DASHBOARD
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().testTag("dashboard_store_info_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = storeProfile.storeName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (storeProfile.isOpen) ForestGreen else WarningRed
                            ) {
                                Text(
                                    text = if (storeProfile.isOpen) "BUKA" else "TUTUP",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Manager: ${storeProfile.managerName} · PIN Otorisasi Aktif",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            editStoreName = storeProfile.storeName
                            editManagerName = storeProfile.managerName
                            editManagerPin = storeProfile.managerPin
                            showEditStoreDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.tactilePress().testTag("btn_edit_store_details")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Atur Toko", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onNavigateToReports,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmberCoffee),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .tactilePress()
                .testTag("btn_dashboard_open_reports")
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Buka Laporan PDF & Jurnal Excel Akuntansi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ringkasan Finansial Warkop",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        // KPI CARDS GRID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Omzet Hari Ini", fontSize = 11.sp, color = MutedGreyText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rp ${viewModel.formatRupiah(todayRevenue)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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
                    Text("Omzet 7 Hari", fontSize = 11.sp, color = MutedGreyText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rp ${viewModel.formatRupiah(weeklyRevenue)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Est. Laba Bersih 7 Hari", fontSize = 11.sp, color = MutedGreyText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rp ${viewModel.formatRupiah(weeklyNetProfit)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (weeklyNetProfit >= 0) SuccessGreen else WarningRed
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
                    Text("Alokasi Ops 7 Hari", fontSize = 11.sp, color = MutedGreyText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rp ${viewModel.formatRupiah(weeklyOpsAllocation)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberCoffee
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GRAFIK PENDAPATAN VS LABA BERSIH
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().testTag("dashboard_chart_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tren Pendapatan & Laba (7 Hari)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(ForestGreen, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Omzet Netto", fontSize = 11.sp, color = MutedGreyText)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(AmberCoffee, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Est. Laba Bersih", fontSize = 11.sp, color = MutedGreyText)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val maxVal = remember(dailyStats) {
                    val m = dailyStats.maxOfOrNull { maxOf(it.second, it.third) } ?: 100000.0
                    if (m <= 0) 100000.0 else m
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(vertical = 8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val barSpacing = width / dailyStats.size
                        val barWidth = barSpacing * 0.4f

                        // Grid lines
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.4f),
                            start = Offset(0f, height),
                            end = Offset(width, height),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.2f),
                            start = Offset(0f, height / 2f),
                            end = Offset(width, height / 2f),
                            strokeWidth = 1f
                        )

                        val profitPoints = mutableListOf<Offset>()

                        dailyStats.forEachIndexed { index, triple ->
                            val xCenter = (index * barSpacing) + (barSpacing / 2f)
                            val revHeight = ((triple.second / maxVal) * (height * 0.85f)).toFloat().coerceAtLeast(4f)
                            val profitHeight = ((triple.third / maxVal) * (height * 0.85f)).toFloat().coerceAtLeast(2f)

                            // Revenue bar
                            drawRect(
                                color = ForestGreen,
                                topLeft = Offset(xCenter - barWidth / 2f, height - revHeight),
                                size = Size(barWidth, revHeight)
                            )

                            // Record profit point
                            val py = height - profitHeight
                            profitPoints.add(Offset(xCenter, py))
                        }

                        // Draw profit connecting line
                        if (profitPoints.size > 1) {
                            val path = Path().apply {
                                moveTo(profitPoints[0].x, profitPoints[0].y)
                                for (i in 1 until profitPoints.size) {
                                    lineTo(profitPoints[i].x, profitPoints[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = AmberCoffee,
                                style = Stroke(width = 4f)
                            )
                            profitPoints.forEach { pt ->
                                drawCircle(
                                    color = AmberCoffee,
                                    radius = 5f,
                                    center = pt
                                )
                            }
                        }
                    }
                }

                // X-Axis Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dailyStats.forEach { triple ->
                        Text(
                            text = triple.first.take(3),
                            fontSize = 10.sp,
                            color = MutedGreyText,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // MENU TERLARIS
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Menu Terlaris",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (topSellingItems.isEmpty()) {
                    Text(
                        text = "Belum ada transaksi penjualan yang tercatat.",
                        fontSize = 13.sp,
                        color = MutedGreyText,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    topSellingItems.take(5).forEachIndexed { idx, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#${idx + 1}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(
                                    text = entry.key,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "${entry.value} porsi",
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

        Spacer(modifier = Modifier.height(24.dp))
    }

    // DIALOG PENGATURAN TOKO & DETAIL SETELAH MASUK KE DASHBOARD
    if (showEditStoreDialog) {
        AlertDialog(
            onDismissRequest = { showEditStoreDialog = false },
            title = {
                Text("Detail Profil Toko & Usaha", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Atur nama warkop, nama manager, dan PIN otorisasi stok langsung dari dashboard Anda.",
                        fontSize = 12.sp,
                        color = MutedGreyText
                    )
                    OutlinedTextField(
                        value = editStoreName,
                        onValueChange = { editStoreName = it },
                        label = { Text("Nama Warkop / Usaha") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_store_name_input")
                    )
                    OutlinedTextField(
                        value = editManagerName,
                        onValueChange = { editManagerName = it },
                        label = { Text("Nama Manager / Pemilik") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_manager_name_input")
                    )
                    OutlinedTextField(
                        value = editManagerPin,
                        onValueChange = { editManagerPin = it },
                        label = { Text("PIN Otorisasi Manager (4-6 digit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_manager_pin_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editStoreName.isNotBlank() && editManagerName.isNotBlank()) {
                            viewModel.updateStoreNames(editStoreName, editManagerName)
                            if (editManagerPin.length >= 4) {
                                viewModel.updateManagerPin(editManagerPin)
                            }
                            showEditStoreDialog = false
                        }
                    },
                    modifier = Modifier.tactilePress().testTag("dialog_save_store_details")
                ) {
                    Text("Simpan Perubahan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditStoreDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
