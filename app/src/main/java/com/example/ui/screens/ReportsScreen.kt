package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel
import com.example.util.ReportExporter
import com.example.util.ReportPeriod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    viewModel: WarkopViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storeProfile by viewModel.storeProfile.collectAsState()
    val transactions by viewModel.transactionList.collectAsState()
    val stockLogs by viewModel.stockLogList.collectAsState()
    val debts by viewModel.debtList.collectAsState()
    val cashiers by viewModel.cashierList.collectAsState()

    var selectedPeriod by remember { mutableStateOf(ReportPeriod.LAST_7_DAYS) }

    val reportData = remember(selectedPeriod, storeProfile, transactions, stockLogs, debts, cashiers) {
        ReportExporter.buildReportData(
            period = selectedPeriod,
            storeProfile = storeProfile,
            allTransactions = transactions,
            allStockLogs = stockLogs,
            allDebts = debts,
            cashiers = cashiers
        )
    }

    val fileTimestamp = remember(selectedPeriod) {
        SimpleDateFormat("yyyyMMdd_HHmm", Locale("id", "ID")).format(Date())
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.generatePdfReport(reportData, out)
                }
                viewModel.showToast("Laporan PDF berhasil disimpan")
            } catch (e: Exception) {
                viewModel.showToast("Gagal menyimpan PDF: ${e.message}")
            }
        }
    }

    val createExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.generateExcelReport(reportData, out)
                }
                viewModel.showToast("Jurnal Akuntansi Excel (.xlsx) berhasil disimpan")
            } catch (e: Exception) {
                viewModel.showToast("Gagal menyimpan Excel: ${e.message}")
            }
        }
    }

    val dateTimeFmt = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale("id", "ID")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Laporan Keuangan & Akuntansi",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Ekspor PDF (Penjualan, Pembelian, Chart Trend, PJ Piket) & Excel Akuntansi (.xlsx)",
            fontSize = 12.sp,
            color = MutedGreyText
        )

        Spacer(modifier = Modifier.height(10.dp))

        // FILTER PERIODE WAKTU
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReportPeriod.entries.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { selectedPeriod = period },
                    label = { Text(period.label, fontSize = 11.sp) },
                    modifier = Modifier.testTag("filter_report_${period.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // KARTU AKSI EKSPOR PDF & EXCEL
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Unduh & Bagikan Dokumen (${reportData.periodRangeLabel})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // PDF Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            createPdfLauncher.launch("Laporan_Keuangan_${selectedPeriod.name}_$fileTimestamp.pdf")
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .tactilePress()
                            .testTag("btn_save_pdf")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val uri = ReportExporter.writePdfToCacheUri(
                                    context,
                                    reportData,
                                    "Laporan_Keuangan_${selectedPeriod.name}_$fileTimestamp.pdf"
                                )
                                ReportExporter.shareFile(context, uri, "application/pdf", "Bagikan Laporan PDF")
                            } catch (e: Exception) {
                                viewModel.showToast("Gagal membuat PDF: ${e.message}")
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .tactilePress()
                            .testTag("btn_share_pdf")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bagikan PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Excel Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            createExcelLauncher.launch("Jurnal_Akuntansi_${selectedPeriod.name}_$fileTimestamp.xlsx")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberCoffee),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .tactilePress()
                            .testTag("btn_save_excel")
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val uri = ReportExporter.writeExcelToCacheUri(
                                    context,
                                    reportData,
                                    "Jurnal_Akuntansi_${selectedPeriod.name}_$fileTimestamp.xlsx"
                                )
                                ReportExporter.shareFile(
                                    context,
                                    uri,
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "Bagikan Jurnal Excel"
                                )
                            } catch (e: Exception) {
                                viewModel.showToast("Gagal membuat Excel: ${e.message}")
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .tactilePress()
                            .testTag("btn_share_excel")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bagikan Excel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PRATINJAU LABA RUGI AKUNTANSI
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pratinjau Laba Rugi (${selectedPeriod.label})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                ReportMetricRow("Penjualan Netto (${reportData.transactions.size} trx)", ReportExporter.formatRp(reportData.netSales), ForestGreen)
                ReportMetricRow("HPP Bahan Baku (BOM)", "-${ReportExporter.formatRp(reportData.totalHpp)}", MutedGreyText)
                ReportMetricRow("Laba Kotor", ReportExporter.formatRp(reportData.grossProfit), MaterialTheme.colorScheme.onSurface, bold = true)
                ReportMetricRow(
                    if (storeProfile.isOpen) "Beban Operasional" else "Beban Operasional (Ditunda)",
                    "-${ReportExporter.formatRp(reportData.periodOpsCost)}",
                    AmberCoffee
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                ReportMetricRow(
                    "LABA BERSIH (Net Profit)",
                    ReportExporter.formatRp(reportData.netProfit),
                    if (reportData.netProfit >= 0) SuccessGreen else WarningRed,
                    bold = true
                )
                ReportMetricRow("Total Pembelian Stok Masuk", ReportExporter.formatRp(reportData.totalPurchases), MaterialTheme.colorScheme.primary)
                ReportMetricRow("Piutang Kasbon Aktif", ReportExporter.formatRp(reportData.totalKasbonReceivable), WarningRed)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // PRATINJAU CHART TREND
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Grafik Tren Omzet & Laba Bersih", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(ForestGreen, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Omzet", fontSize = 11.sp, color = MutedGreyText)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(AmberCoffee, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Est. Laba Bersih", fontSize = 11.sp, color = MutedGreyText)
                    }
                }

                val points = reportData.trendPoints
                val maxVal = remember(points) {
                    val m = points.maxOfOrNull { maxOf(it.revenue, kotlin.math.abs(it.netProfit)) } ?: 100000.0
                    if (m <= 0.0) 100000.0 else m
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(vertical = 6.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        if (points.isEmpty()) return@Canvas
                        val step = w / points.size
                        val barW = (step * 0.4f).coerceAtMost(32f)

                        drawLine(Color.LightGray.copy(alpha = 0.4f), Offset(0f, h), Offset(w, h), 1f)
                        drawLine(Color.LightGray.copy(alpha = 0.25f), Offset(0f, h / 2f), Offset(w, h / 2f), 1f)

                        val linePts = mutableListOf<Offset>()
                        points.forEachIndexed { idx, pt ->
                            val cx = idx * step + step / 2f
                            val barH = ((pt.revenue / maxVal) * h * 0.85f).toFloat().coerceAtLeast(3f)
                            drawRect(
                                color = ForestGreen,
                                topLeft = Offset(cx - barW / 2f, h - barH),
                                size = Size(barW, barH)
                            )
                            val netH = ((pt.netProfit.coerceAtLeast(0.0) / maxVal) * h * 0.85f).toFloat()
                            linePts.add(Offset(cx, h - netH))
                        }

                        if (linePts.size > 1) {
                            val path = Path().apply {
                                moveTo(linePts[0].x, linePts[0].y)
                                for (i in 1 until linePts.size) {
                                    lineTo(linePts[i].x, linePts[i].y)
                                }
                            }
                            drawPath(path, AmberCoffee, style = Stroke(width = 4f))
                            linePts.forEach { drawCircle(AmberCoffee, radius = 5f, center = it) }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    points.forEach { pt ->
                        Text(pt.label, fontSize = 10.sp, color = MutedGreyText)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // REKAP PJ SETIAP WAKTU PIKET
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("PJ Setiap Waktu Piket (${reportData.pjSummaries.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (reportData.pjSummaries.isEmpty()) {
                    Text("Belum ada catatan piket pada periode ini.", fontSize = 12.sp, color = MutedGreyText)
                } else {
                    reportData.pjSummaries.forEach { pj ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(pj.pjName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = ReportExporter.formatRp(pj.salesRevenue),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ForestGreen
                                )
                            }
                            Text(
                                text = "Shift: ${pj.shiftLabel} · ${pj.salesCount} penjualan · Kasbon: ${ReportExporter.formatRp(pj.kasbonTotal)} · Stok/Beli: ${pj.stockActionCount} (${ReportExporter.formatRp(pj.stockPurchaseCost)})",
                                fontSize = 11.sp,
                                color = MutedGreyText
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // RIWAYAT PEMBELIAN & RESTOCK BAHAN
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Riwayat Pembelian / Stok Masuk (${reportData.stockLogs.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (reportData.stockLogs.isEmpty()) {
                    Text("Belum ada log pembelian atau restock pada periode ini.", fontSize = 12.sp, color = MutedGreyText)
                } else {
                    reportData.stockLogs.take(15).forEach { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val deltaStr = if (log.delta >= 0) "+${log.delta.toInt()}" else "${log.delta.toInt()}"
                                Text("${log.ingredientName} ($deltaStr)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    text = "${dateTimeFmt.format(Date(log.timestamp))} · ${log.reason} · PJ: ${log.authorizedBy}",
                                    fontSize = 11.sp,
                                    color = MutedGreyText
                                )
                            }
                            Text(
                                text = ReportExporter.formatRp(log.costImpact),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = AmberCoffee
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // JURNAL AKUNTANSI RINGKAS
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Entri Jurnal Akuntansi (${reportData.journalEntries.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Diekspor lengkap ke sheet Jurnal_Akuntansi pada file Excel (.xlsx).", fontSize = 11.sp, color = MutedGreyText)
                Spacer(modifier = Modifier.height(8.dp))

                if (reportData.journalEntries.isEmpty()) {
                    Text("Belum ada entri jurnal akuntansi pada periode ini.", fontSize = 12.sp, color = MutedGreyText)
                } else {
                    reportData.journalEntries.take(10).forEach { entry ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${entry.refNo} · ${dateTimeFmt.format(Date(entry.timestamp))}", fontSize = 11.sp, color = MutedGreyText)
                                Text(ReportExporter.formatRp(entry.amount), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ForestGreen)
                            }
                            Text("D: ${entry.debitAccount}", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Text("K: ${entry.creditAccount}", fontSize = 11.5.sp, color = MutedGreyText)
                            Text("${entry.description} · PJ: ${entry.pjName}", fontSize = 10.5.sp, color = MutedGreyText)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ReportMetricRow(
    label: String,
    value: String,
    valueColor: Color,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
