package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.CashierSubAccount
import com.example.data.model.DebtAccount
import com.example.data.model.StockLog
import com.example.data.model.StoreProfile
import com.example.data.model.Transaction
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

enum class ReportPeriod(val label: String, val daysCount: Int) {
    TODAY("Hari Ini", 1),
    LAST_7_DAYS("7 Hari Terakhir", 7),
    LAST_30_DAYS("30 Hari Terakhir", 30),
    ALL("Semua Waktu", 0)
}

data class DailyTrendPoint(
    val label: String,
    val revenue: Double,
    val netProfit: Double
)

data class ShiftPjSummary(
    val pjName: String,
    val shiftLabel: String,
    val salesCount: Int,
    val salesRevenue: Double,
    val kasbonTotal: Double,
    val stockActionCount: Int,
    val stockPurchaseCost: Double
)

data class JournalEntry(
    val timestamp: Long,
    val refNo: String,
    val debitAccount: String,
    val creditAccount: String,
    val description: String,
    val pjName: String,
    val amount: Double
)

data class FinancialReportData(
    val storeProfile: StoreProfile,
    val period: ReportPeriod,
    val periodRangeLabel: String,
    val generatedAt: Long,
    val transactions: List<Transaction>,
    val stockLogs: List<StockLog>,
    val debts: List<DebtAccount>,
    val grossSales: Double,
    val totalDiscount: Double,
    val netSales: Double,
    val totalHpp: Double,
    val grossProfit: Double,
    val periodOpsCost: Double,
    val netProfit: Double,
    val totalPurchases: Double,
    val totalKasbonReceivable: Double,
    val trendPoints: List<DailyTrendPoint>,
    val pjSummaries: List<ShiftPjSummary>,
    val journalEntries: List<JournalEntry>
)

object ReportExporter {

    private val idLocale = Locale("id", "ID")
    private val rupiahFmt: NumberFormat = NumberFormat.getNumberInstance(idLocale)
    private val dateTimeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", idLocale)
    private val dateOnlyFmt = SimpleDateFormat("dd/MM/yyyy", idLocale)
    private val shortDayFmt = SimpleDateFormat("dd/MM", idLocale)

    fun formatRp(value: Double): String = "Rp ${rupiahFmt.format(value.toLong())}"

    fun buildReportData(
        period: ReportPeriod,
        storeProfile: StoreProfile,
        allTransactions: List<Transaction>,
        allStockLogs: List<StockLog>,
        allDebts: List<DebtAccount>,
        cashiers: List<CashierSubAccount>
    ): FinancialReportData {
        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startMillis = when (period) {
            ReportPeriod.TODAY -> startOfToday
            ReportPeriod.LAST_7_DAYS -> startOfToday - (6L * 86400000L)
            ReportPeriod.LAST_30_DAYS -> startOfToday - (29L * 86400000L)
            ReportPeriod.ALL -> 0L
        }

        val filteredTx = allTransactions
            .filter { it.timestamp in startMillis..now }
            .sortedByDescending { it.timestamp }
        val filteredLogs = allStockLogs
            .filter { it.timestamp in startMillis..now }
            .sortedByDescending { it.timestamp }

        val grossSales = filteredTx.sumOf { it.subtotal }
        val totalDiscount = filteredTx.sumOf { it.discount }
        val netSales = filteredTx.sumOf { it.total }
        val totalHpp = filteredTx.sumOf { tx -> tx.items.sumOf { it.cost * it.quantity } }
        val grossProfit = netSales - totalHpp

        val activeDays = when (period) {
            ReportPeriod.TODAY -> 1
            ReportPeriod.LAST_7_DAYS -> 7
            ReportPeriod.LAST_30_DAYS -> 30
            ReportPeriod.ALL -> {
                val earliest = listOfNotNull(
                    filteredTx.minOfOrNull { it.timestamp },
                    filteredLogs.minOfOrNull { it.timestamp }
                ).minOrNull() ?: startOfToday
                max(1, (((now - earliest) / 86400000L) + 1).toInt())
            }
        }

        val dailyOps = if (storeProfile.isOpen) storeProfile.operatingCost.dailyTotal() else 0.0
        val periodOpsCost = dailyOps * activeDays
        val netProfit = grossProfit - periodOpsCost
        val totalPurchases = filteredLogs.filter { it.delta > 0 }.sumOf { it.costImpact }
        val totalKasbonReceivable = allDebts.sumOf { it.remainingDebt }

        // Chart trend points (7 points for 7/30/All or hourly buckets for Today)
        val trendPoints = buildTrendPoints(period, now, startOfToday, filteredTx, storeProfile)

        // PJ Shift summaries
        val pjMap = mutableMapOf<String, ShiftPjSummary>()
        filteredTx.forEach { tx ->
            val pjName = tx.cashierName.ifBlank { storeProfile.managerName }
            val shift = tx.cashierShift.ifBlank {
                cashiers.find { it.name.equals(pjName, ignoreCase = true) }?.shift
                    ?: if (pjName.equals(storeProfile.managerName, ignoreCase = true)) "Manager (Full Shift)" else "Shift Umum"
            }
            val current = pjMap[pjName] ?: ShiftPjSummary(
                pjName = pjName,
                shiftLabel = shift,
                salesCount = 0,
                salesRevenue = 0.0,
                kasbonTotal = 0.0,
                stockActionCount = 0,
                stockPurchaseCost = 0.0
            )
            val isKasbon = tx.paymentMethod.contains("Kasbon", ignoreCase = true) || tx.paymentMethod.contains("Utang", ignoreCase = true)
            pjMap[pjName] = current.copy(
                salesCount = current.salesCount + 1,
                salesRevenue = current.salesRevenue + tx.total,
                kasbonTotal = current.kasbonTotal + (if (isKasbon) tx.total else 0.0)
            )
        }

        filteredLogs.forEach { log ->
            val pjName = log.authorizedBy.ifBlank { storeProfile.managerName }
            val current = pjMap[pjName] ?: ShiftPjSummary(
                pjName = pjName,
                shiftLabel = "Otorisasi Manager / Stok",
                salesCount = 0,
                salesRevenue = 0.0,
                kasbonTotal = 0.0,
                stockActionCount = 0,
                stockPurchaseCost = 0.0
            )
            pjMap[pjName] = current.copy(
                stockActionCount = current.stockActionCount + 1,
                stockPurchaseCost = current.stockPurchaseCost + (if (log.delta > 0) log.costImpact else 0.0)
            )
        }

        // Build Accounting Journal Entries (Double Entry)
        val journal = mutableListOf<JournalEntry>()
        var refSeq = 1
        filteredTx.sortedBy { it.timestamp }.forEach { tx ->
            val ref = "JRN-${refSeq.toString().padStart(4, '0')}"
            refSeq++
            val isKasbon = tx.paymentMethod.contains("Kasbon", ignoreCase = true) || tx.paymentMethod.contains("Utang", ignoreCase = true)
            val debitAcc = when {
                isKasbon -> "1120 - Piutang Usaha (Kasbon)"
                tx.paymentMethod.equals("QRIS", true) || tx.paymentMethod.equals("Transfer", true) -> "1112 - Bank / QRIS Warkop"
                else -> "1111 - Kas Tunai Kasir"
            }
            val desc = "Penjualan ${tx.items.joinToString { "${it.quantity}x ${it.name}" }}" +
                if (tx.customerName.isNotBlank()) " (${tx.customerName})" else ""
            val pj = "${tx.cashierName} (${tx.cashierShift.ifBlank { "Shift" }})"
            journal.add(
                JournalEntry(
                    timestamp = tx.timestamp,
                    refNo = ref,
                    debitAccount = debitAcc,
                    creditAccount = "4100 - Pendapatan Penjualan",
                    description = desc,
                    pjName = pj,
                    amount = tx.total
                )
            )

            val hpp = tx.items.sumOf { it.cost * it.quantity }
            if (hpp > 0) {
                journal.add(
                    JournalEntry(
                        timestamp = tx.timestamp,
                        refNo = "$ref-HPP",
                        debitAccount = "5100 - Beban Pokok Penjualan (HPP)",
                        creditAccount = "1130 - Persediaan Bahan Baku",
                        description = "Pemakaian bahan resep: $desc",
                        pjName = pj,
                        amount = hpp
                    )
                )
            }
        }

        // Debt installment payments
        allDebts.forEach { debt ->
            debt.payments.filter { it.timestamp in startMillis..now }.forEach { pay ->
                val ref = "KBN-${refSeq.toString().padStart(4, '0')}"
                refSeq++
                journal.add(
                    JournalEntry(
                        timestamp = pay.timestamp,
                        refNo = ref,
                        debitAccount = "1111 - Kas Tunai Kasir",
                        creditAccount = "1120 - Piutang Usaha (Kasbon)",
                        description = "Pelunasan/Cicilan Kasbon ${debt.customerName}: ${pay.note}",
                        pjName = pay.cashierName,
                        amount = pay.amount
                    )
                )
            }
        }

        // Stock Purchases / Restocks
        filteredLogs.sortedBy { it.timestamp }.forEach { log ->
            val absCost = kotlin.math.abs(log.costImpact)
            if (absCost > 0) {
                val ref = "STK-${refSeq.toString().padStart(4, '0')}"
                refSeq++
                if (log.delta > 0) {
                    journal.add(
                        JournalEntry(
                            timestamp = log.timestamp,
                            refNo = ref,
                            debitAccount = "1130 - Persediaan Bahan Baku",
                            creditAccount = "1111 - Kas Operasional Pembelian",
                            description = "Pembelian/Restock ${log.ingredientName} (+${log.delta.toInt()}): ${log.reason}",
                            pjName = log.authorizedBy,
                            amount = absCost
                        )
                    )
                } else {
                    journal.add(
                        JournalEntry(
                            timestamp = log.timestamp,
                            refNo = ref,
                            debitAccount = "5290 - Beban Selisih/Penyusutan Stok",
                            creditAccount = "1130 - Persediaan Bahan Baku",
                            description = "Penyesuaian stok ${log.ingredientName} (${log.delta.toInt()}): ${log.reason}",
                            pjName = log.authorizedBy,
                            amount = absCost
                        )
                    )
                }
            }
        }

        // Operational Cost journal entry if store is open
        if (storeProfile.isOpen && periodOpsCost > 0) {
            val ref = "OPS-${refSeq.toString().padStart(4, '0')}"
            journal.add(
                JournalEntry(
                    timestamp = now,
                    refNo = ref,
                    debitAccount = "5200 - Beban Operasional (Air, Listrik, Gas, Tenaga Kerja)",
                    creditAccount = "1111 - Kas / Beban Masih Harus Dibayar",
                    description = "Alokasi operasional $activeDays hari (${period.label})",
                    pjName = storeProfile.managerName,
                    amount = periodOpsCost
                )
            )
        }

        val rangeLabel = if (period == ReportPeriod.ALL) {
            "Semua Riwayat s/d ${dateOnlyFmt.format(Date(now))}"
        } else {
            "${dateOnlyFmt.format(Date(startMillis))} - ${dateOnlyFmt.format(Date(now))}"
        }

        return FinancialReportData(
            storeProfile = storeProfile,
            period = period,
            periodRangeLabel = rangeLabel,
            generatedAt = now,
            transactions = filteredTx,
            stockLogs = filteredLogs,
            debts = allDebts,
            grossSales = grossSales,
            totalDiscount = totalDiscount,
            netSales = netSales,
            totalHpp = totalHpp,
            grossProfit = grossProfit,
            periodOpsCost = periodOpsCost,
            netProfit = netProfit,
            totalPurchases = totalPurchases,
            totalKasbonReceivable = totalKasbonReceivable,
            trendPoints = trendPoints,
            pjSummaries = pjMap.values.sortedByDescending { it.salesRevenue + it.stockPurchaseCost },
            journalEntries = journal.sortedByDescending { it.timestamp }
        )
    }

    private fun buildTrendPoints(
        period: ReportPeriod,
        now: Long,
        startOfToday: Long,
        transactions: List<Transaction>,
        storeProfile: StoreProfile
    ): List<DailyTrendPoint> {
        val days = when (period) {
            ReportPeriod.TODAY -> 1
            ReportPeriod.LAST_7_DAYS -> 7
            ReportPeriod.LAST_30_DAYS -> 10 // group into 3-day buckets or last 10 days for legible chart
            ReportPeriod.ALL -> 7
        }
        val list = mutableListOf<DailyTrendPoint>()
        val dailyOps = if (storeProfile.isOpen) storeProfile.operatingCost.dailyTotal() else 0.0

        if (period == ReportPeriod.TODAY) {
            // 4 shift blocks today: 06-10, 10-14, 14-18, 18-24
            val blocks = listOf(
                0 to 10 to "Pagi",
                10 to 14 to "Siang",
                14 to 18 to "Sore",
                18 to 24 to "Malam"
            )
            blocks.forEach { (hours, label) ->
                val calStart = Calendar.getInstance().apply {
                    timeInMillis = startOfToday
                    set(Calendar.HOUR_OF_DAY, hours.first)
                }.timeInMillis
                val calEnd = Calendar.getInstance().apply {
                    timeInMillis = startOfToday
                    set(Calendar.HOUR_OF_DAY, hours.second)
                }.timeInMillis
                val blockTx = transactions.filter { it.timestamp in calStart until calEnd }
                val rev = blockTx.sumOf { it.total }
                val hpp = blockTx.sumOf { tx -> tx.items.sumOf { it.cost * it.quantity } }
                val net = rev - hpp - (dailyOps / 4.0)
                list.add(DailyTrendPoint(label, rev, net))
            }
            return list
        }

        val bucketDays = if (period == ReportPeriod.LAST_30_DAYS) 3 else 1
        for (i in (days - 1) downTo 0) {
            val endDay = startOfToday - (i.toLong() * bucketDays - 1L) * 86400000L
            val startDay = startOfToday - ((i.toLong() + 1L) * bucketDays - 1L) * 86400000L
            val bucketTx = transactions.filter { it.timestamp in startDay until (endDay + 86400000L) }
            val rev = bucketTx.sumOf { it.total }
            val hpp = bucketTx.sumOf { tx -> tx.items.sumOf { it.cost * it.quantity } }
            val net = rev - hpp - (dailyOps * bucketDays)
            val label = shortDayFmt.format(Date(startDay))
            list.add(DailyTrendPoint(label, rev, net))
        }
        return list
    }

    // =========================================================================
    // 1. GENERATOR LAPORAN PDF (Dengan Chart Trend, Pembelian, Penjualan, PJ Piket)
    // =========================================================================
    fun generatePdfReport(data: FinancialReportData, outputStream: OutputStream) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val contentWidth = pageWidth - margin * 2

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var y = 40f

        val paintHeaderBg = Paint().apply { color = Color.parseColor("#1D6B4F") }
        val paintAccent = Paint().apply { color = Color.parseColor("#E0903A") }
        val paintWhiteTitle = Paint().apply {
            color = Color.WHITE
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintWhiteSub = Paint().apply {
            color = Color.parseColor("#E3F0EA")
            textSize = 9.5f
            isAntiAlias = true
        }
        val paintSectionTitle = Paint().apply {
            color = Color.parseColor("#1D6B4F")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintBold = Paint().apply {
            color = Color.parseColor("#1F2421")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintBody = Paint().apply {
            color = Color.parseColor("#1F2421")
            textSize = 8.8f
            isAntiAlias = true
        }
        val paintMuted = Paint().apply {
            color = Color.parseColor("#69706B")
            textSize = 8f
            isAntiAlias = true
        }
        val paintLine = Paint().apply {
            color = Color.parseColor("#D8DDD9")
            strokeWidth = 0.8f
        }
        val paintTableHeaderBg = Paint().apply {
            color = Color.parseColor("#E3F0EA")
        }

        fun nextPage(continuedTitle: String = "") {
            // Footer on current page
            canvas.drawText(
                "Halaman $pageNumber · ${data.storeProfile.storeName}",
                margin,
                pageHeight - 20f,
                paintMuted
            )
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
            if (continuedTitle.isNotBlank()) {
                canvas.drawText(continuedTitle, margin, y, paintSectionTitle)
                y += 16f
            }
        }

        fun ensureSpace(neededHeight: Float, continuedTitle: String = "") {
            if (y + neededHeight > pageHeight - 40f) {
                nextPage(continuedTitle)
            }
        }

        // --- HEADER BANNER ---
        canvas.drawRoundRect(margin, y, margin + contentWidth, y + 66f, 8f, 8f, paintHeaderBg)
        canvas.drawText("LAPORAN KEUANGAN - ${data.storeProfile.storeName.uppercase()}", margin + 14f, y + 24f, paintWhiteTitle)
        canvas.drawText(
            "Periode: ${data.period.label} (${data.periodRangeLabel}) · Manager: ${data.storeProfile.managerName}",
            margin + 14f,
            y + 41f,
            paintWhiteSub
        )
        canvas.drawText(
            "Dicetak: ${dateTimeFmt.format(Date(data.generatedAt))} · Status Toko: ${if (data.storeProfile.isOpen) "Buka (Ops Aktif)" else "Tutup (Ops Ditunda)"}",
            margin + 14f,
            y + 55f,
            paintWhiteSub
        )
        y += 84f

        // --- 1. RINGKASAN LABA RUGI ---
        canvas.drawText("1. Ringkasan Laba Rugi & Arus Kas", margin, y, paintSectionTitle)
        y += 14f

        val summaryRows = listOf(
            "Total Penjualan Kotor (Gross Sales)" to formatRp(data.grossSales),
            "Potongan Diskon" to "-${formatRp(data.totalDiscount)}",
            "Pendapatan Penjualan Netto" to formatRp(data.netSales),
            "Harga Pokok Penjualan (HPP Bahan)" to "-${formatRp(data.totalHpp)}",
            "Laba Kotor (Gross Profit)" to formatRp(data.grossProfit),
            "Beban Operasional Periode" to "-${formatRp(data.periodOpsCost)}",
            "LABA BERSIH (Net Profit)" to formatRp(data.netProfit),
            "Total Pembelian / Restock Bahan Baku" to formatRp(data.totalPurchases),
            "Piutang Kasbon Pelanggan Aktif" to formatRp(data.totalKasbonReceivable)
        )

        summaryRows.forEachIndexed { idx, (label, valStr) ->
            ensureSpace(16f)
            val isHighlight = label.startsWith("LABA BERSIH")
            if (isHighlight) {
                canvas.drawRect(margin, y - 10f, margin + contentWidth, y + 5f, paintTableHeaderBg)
            }
            val p = if (isHighlight) paintBold else paintBody
            canvas.drawText(label, margin + 6f, y, p)
            val valWidth = p.measureText(valStr)
            canvas.drawText(valStr, margin + contentWidth - valWidth - 6f, y, p)
            y += 15f
            if (idx == 4 || idx == 6) {
                canvas.drawLine(margin, y - 9f, margin + contentWidth, y - 9f, paintLine)
            }
        }
        y += 12f

        // --- 2. CHART TREND PENDAPATAN & LABA ---
        ensureSpace(170f)
        canvas.drawText("2. Grafik Tren Pendapatan & Laba Bersih", margin, y, paintSectionTitle)
        y += 10f
        drawPdfTrendChart(canvas, margin, y, contentWidth, 130f, data.trendPoints, paintHeaderBg, paintAccent, paintMuted, paintLine)
        y += 148f

        // --- 3. PENANGGUNG JAWAB (PJ) SETIAP WAKTU PIKET ---
        ensureSpace(65f)
        canvas.drawText("3. Ringkasan Penanggung Jawab (PJ) Waktu Piket", margin, y, paintSectionTitle)
        y += 12f

        // Table Header
        canvas.drawRect(margin, y, margin + contentWidth, y + 18f, paintTableHeaderBg)
        canvas.drawText("Nama PJ", margin + 4f, y + 12f, paintBold)
        canvas.drawText("Waktu Piket / Shift", margin + 115f, y + 12f, paintBold)
        canvas.drawText("Trx", margin + 255f, y + 12f, paintBold)
        canvas.drawText("Omzet Jual", margin + 295f, y + 12f, paintBold)
        canvas.drawText("Kasbon", margin + 385f, y + 12f, paintBold)
        canvas.drawText("Stok/Beli", margin + 460f, y + 12f, paintBold)
        y += 26f

        if (data.pjSummaries.isEmpty()) {
            canvas.drawText("Belum ada aktivitas piket pada periode ini.", margin + 4f, y, paintMuted)
            y += 16f
        } else {
            data.pjSummaries.forEach { pj ->
                ensureSpace(18f, "3. Ringkasan PJ Waktu Piket (Lanjutan)")
                canvas.drawText(pj.pjName.take(18), margin + 4f, y, paintBody)
                canvas.drawText(pj.shiftLabel.take(24), margin + 115f, y, paintMuted)
                canvas.drawText("${pj.salesCount}", margin + 255f, y, paintBody)
                canvas.drawText(formatRp(pj.salesRevenue), margin + 295f, y, paintBody)
                canvas.drawText(formatRp(pj.kasbonTotal), margin + 385f, y, paintBody)
                canvas.drawText("${pj.stockActionCount} (${formatRp(pj.stockPurchaseCost)})", margin + 460f, y, paintMuted)
                y += 14f
                canvas.drawLine(margin, y - 9f, margin + contentWidth, y - 9f, paintLine)
            }
        }
        y += 12f

        // --- 4. RIWAYAT PENJUALAN PRODUK ---
        ensureSpace(65f)
        canvas.drawText("4. Riwayat Penjualan Produk (${data.transactions.size} Transaksi)", margin, y, paintSectionTitle)
        y += 12f

        canvas.drawRect(margin, y, margin + contentWidth, y + 18f, paintTableHeaderBg)
        canvas.drawText("Waktu", margin + 4f, y + 12f, paintBold)
        canvas.drawText("Produk Terjual", margin + 88f, y + 12f, paintBold)
        canvas.drawText("Metode", margin + 280f, y + 12f, paintBold)
        canvas.drawText("PJ Piket", margin + 355f, y + 12f, paintBold)
        canvas.drawText("Total", margin + 455f, y + 12f, paintBold)
        y += 26f

        if (data.transactions.isEmpty()) {
            canvas.drawText("Tidak ada riwayat penjualan pada periode ini.", margin + 4f, y, paintMuted)
            y += 16f
        } else {
            data.transactions.forEach { tx ->
                ensureSpace(18f, "4. Riwayat Penjualan Produk (Lanjutan)")
                val itemsStr = tx.items.joinToString { "${it.quantity}x ${it.name}" }.take(34)
                val pjStr = "${tx.cashierName} (${tx.cashierShift.take(10)})".take(18)
                canvas.drawText(dateTimeFmt.format(Date(tx.timestamp)), margin + 4f, y, paintMuted)
                canvas.drawText(itemsStr, margin + 88f, y, paintBody)
                canvas.drawText(tx.paymentMethod.take(13), margin + 280f, y, paintBody)
                canvas.drawText(pjStr, margin + 355f, y, paintMuted)
                canvas.drawText(formatRp(tx.total), margin + 455f, y, paintBold)
                y += 14f
                canvas.drawLine(margin, y - 9f, margin + contentWidth, y - 9f, paintLine)
            }
        }
        y += 12f

        // --- 5. RIWAYAT PEMBELIAN & RESTOCK BAHAN PRODUK ---
        ensureSpace(65f)
        canvas.drawText("5. Riwayat Pembelian & Penyesuaian Stok Bahan (${data.stockLogs.size} Catatan)", margin, y, paintSectionTitle)
        y += 12f

        canvas.drawRect(margin, y, margin + contentWidth, y + 18f, paintTableHeaderBg)
        canvas.drawText("Waktu", margin + 4f, y + 12f, paintBold)
        canvas.drawText("Bahan & Perubahan", margin + 88f, y + 12f, paintBold)
        canvas.drawText("Keterangan", margin + 230f, y + 12f, paintBold)
        canvas.drawText("PJ Otorisasi", margin + 355f, y + 12f, paintBold)
        canvas.drawText("Nilai Biaya", margin + 455f, y + 12f, paintBold)
        y += 26f

        if (data.stockLogs.isEmpty()) {
            canvas.drawText("Tidak ada catatan pembelian atau restock pada periode ini.", margin + 4f, y, paintMuted)
            y += 16f
        } else {
            data.stockLogs.forEach { log ->
                ensureSpace(18f, "5. Riwayat Pembelian & Stok (Lanjutan)")
                val deltaStr = if (log.delta >= 0) "+${log.delta.toInt()}" else "${log.delta.toInt()}"
                canvas.drawText(dateTimeFmt.format(Date(log.timestamp)), margin + 4f, y, paintMuted)
                canvas.drawText("${log.ingredientName} ($deltaStr)".take(24), margin + 88f, y, paintBody)
                canvas.drawText(log.reason.take(22), margin + 230f, y, paintMuted)
                canvas.drawText(log.authorizedBy.take(18), margin + 355f, y, paintMuted)
                canvas.drawText(formatRp(log.costImpact), margin + 455f, y, paintBold)
                y += 14f
                canvas.drawLine(margin, y - 9f, margin + contentWidth, y - 9f, paintLine)
            }
        }

        // Final page footer
        canvas.drawText(
            "Halaman $pageNumber · ${data.storeProfile.storeName}",
            margin,
            pageHeight - 20f,
            paintMuted
        )
        pdfDocument.finishPage(page)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    private fun drawPdfTrendChart(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        points: List<DailyTrendPoint>,
        barPaint: Paint,
        lineStrokeBase: Paint,
        labelPaint: Paint,
        gridPaint: Paint
    ) {
        val chartBgPaint = Paint().apply { color = Color.parseColor("#FAF9F6") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#DCE2DE")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(left, top, left + width, top + height, 6f, 6f, chartBgPaint)
        canvas.drawRoundRect(left, top, left + width, top + height, 6f, 6f, borderPaint)

        // Legend
        canvas.drawRect(left + 12f, top + 10f, left + 20f, top + 18f, barPaint)
        canvas.drawText("Omzet Penjualan", left + 24f, top + 18f, labelPaint)
        canvas.drawRect(left + 115f, top + 10f, left + 123f, top + 18f, lineStrokeBase)
        canvas.drawText("Est. Laba Bersih", left + 127f, top + 18f, labelPaint)

        val plotTop = top + 28f
        val plotBottom = top + height - 22f
        val plotHeight = plotBottom - plotTop
        val plotLeft = left + 16f
        val plotWidth = width - 32f

        // Baseline and Midline
        canvas.drawLine(plotLeft, plotBottom, plotLeft + plotWidth, plotBottom, gridPaint)
        canvas.drawLine(plotLeft, plotTop + plotHeight / 2f, plotLeft + plotWidth, plotTop + plotHeight / 2f, gridPaint)

        if (points.isEmpty()) return

        val maxVal = max(10000.0, points.maxOf { max(it.revenue, kotlin.math.abs(it.netProfit)) })
        val step = plotWidth / points.size
        val barWidth = (step * 0.42f).coerceAtMost(28f)

        val profitLinePaint = Paint().apply {
            color = Color.parseColor("#E0903A")
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            isAntiAlias = true
        }
        val profitDotPaint = Paint().apply {
            color = Color.parseColor("#E0903A")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val path = Path()
        val coords = mutableListOf<Pair<Float, Float>>()

        points.forEachIndexed { idx, pt ->
            val cx = plotLeft + idx * step + step / 2f
            val barH = ((pt.revenue / maxVal) * plotHeight * 0.9f).toFloat().coerceAtLeast(2f)
            canvas.drawRect(cx - barWidth / 2f, plotBottom - barH, cx + barWidth / 2f, plotBottom, barPaint)

            val netRatio = (pt.netProfit.coerceAtLeast(0.0) / maxVal) * plotHeight * 0.9f
            val py = plotBottom - netRatio.toFloat()
            coords.add(cx to py)

            // X label
            val lblW = labelPaint.measureText(pt.label)
            canvas.drawText(pt.label, cx - lblW / 2f, top + height - 7f, labelPaint)
        }

        if (coords.isNotEmpty()) {
            path.moveTo(coords[0].first, coords[0].second)
            for (i in 1 until coords.size) {
                path.lineTo(coords[i].first, coords[i].second)
            }
            canvas.drawPath(path, profitLinePaint)
            coords.forEach { (x, y) ->
                canvas.drawCircle(x, y, 3.2f, profitDotPaint)
            }
        }
    }

    // =========================================================================
    // 2. GENERATOR EXCEL (.xlsx OOXML) - JURNAL AKUNTANSI, LABA RUGI, & PJ PIKET
    // =========================================================================
    private sealed class XlsxCell {
        data class Text(val value: String, val style: Int = 0) : XlsxCell()
        data class Number(val value: Double, val style: Int = 0) : XlsxCell()
    }

    fun generateExcelReport(data: FinancialReportData, outputStream: OutputStream) {
        // Style indices in styles.xml:
        // 0 = Normal text
        // 1 = Bold Title / Section
        // 2 = Table Header (White bold on ForestGreen)
        // 3 = Currency (Rp #,##0)
        // 4 = Bold Currency (Total / Laba Bersih)

        // Sheet 1: Laba Rugi
        val sheet1Rows = mutableListOf<List<XlsxCell>>()
        sheet1Rows.add(listOf(XlsxCell.Text("LAPORAN LABA RUGI - ${data.storeProfile.storeName.uppercase()}", 1)))
        sheet1Rows.add(listOf(XlsxCell.Text("Periode: ${data.period.label} (${data.periodRangeLabel})", 0)))
        sheet1Rows.add(listOf(XlsxCell.Text("Manager: ${data.storeProfile.managerName} | Status Toko: ${if (data.storeProfile.isOpen) "Buka" else "Tutup (Ops Ditunda)"}", 0)))
        sheet1Rows.add(emptyList())
        sheet1Rows.add(listOf(XlsxCell.Text("Komponen Akuntansi", 2), XlsxCell.Text("Keterangan", 2), XlsxCell.Text("Nominal (Rp)", 2)))
        sheet1Rows.add(listOf(XlsxCell.Text("Penjualan Kotor (Gross Sales)"), XlsxCell.Text("${data.transactions.size} transaksi"), XlsxCell.Number(data.grossSales, 3)))
        sheet1Rows.add(listOf(XlsxCell.Text("Potongan Diskon Penjualan"), XlsxCell.Text("Pengurang pendapatan"), XlsxCell.Number(-data.totalDiscount, 3)))
        sheet1Rows.add(listOf(XlsxCell.Text("Pendapatan Penjualan Netto", 1), XlsxCell.Text("Omzet bersih"), XlsxCell.Number(data.netSales, 4)))
        sheet1Rows.add(listOf(XlsxCell.Text("Harga Pokok Penjualan (HPP Bahan)"), XlsxCell.Text("Pemakaian resep BOM"), XlsxCell.Number(-data.totalHpp, 3)))
        sheet1Rows.add(listOf(XlsxCell.Text("Laba Kotor (Gross Profit)", 1), XlsxCell.Text("Omzet Netto - HPP"), XlsxCell.Number(data.grossProfit, 4)))
        sheet1Rows.add(listOf(XlsxCell.Text("Beban Operasional Harian (Air/Listrik/Gas/Tenaga Kerja)"), XlsxCell.Text(if (data.storeProfile.isOpen) "Aktif" else "Ditunda (Toko Tutup)"), XlsxCell.Number(-data.periodOpsCost, 3)))
        sheet1Rows.add(listOf(XlsxCell.Text("LABA BERSIH (NET PROFIT)", 1), XlsxCell.Text("Laba Kotor - Beban Operasional"), XlsxCell.Number(data.netProfit, 4)))
        sheet1Rows.add(emptyList())
        val marginPct = if (data.netSales > 0) (data.netProfit / data.netSales) * 100.0 else 0.0
        sheet1Rows.add(listOf(XlsxCell.Text("Margin Laba Bersih (%)"), XlsxCell.Text("Rasio Profitabilitas"), XlsxCell.Number(marginPct, 0)))
        sheet1Rows.add(listOf(XlsxCell.Text("Total Pembelian / Restock Persediaan"), XlsxCell.Text("Pembelian stok masuk"), XlsxCell.Number(data.totalPurchases, 3)))
        sheet1Rows.add(listOf(XlsxCell.Text("Saldo Piutang Kasbon Pelanggan"), XlsxCell.Text("Belum terbayar"), XlsxCell.Number(data.totalKasbonReceivable, 3)))

        // Sheet 2: Jurnal Akuntansi
        val sheet2Rows = mutableListOf<List<XlsxCell>>()
        sheet2Rows.add(listOf(XlsxCell.Text("JURNAL AKUNTANSI UMUM - ${data.storeProfile.storeName.uppercase()}", 1)))
        sheet2Rows.add(listOf(XlsxCell.Text("Periode: ${data.periodRangeLabel}", 0)))
        sheet2Rows.add(emptyList())
        sheet2Rows.add(
            listOf(
                XlsxCell.Text("Tanggal", 2),
                XlsxCell.Text("No. Bukti", 2),
                XlsxCell.Text("Akun Debit", 2),
                XlsxCell.Text("Akun Kredit", 2),
                XlsxCell.Text("Keterangan", 2),
                XlsxCell.Text("PJ Waktu Piket", 2),
                XlsxCell.Text("Debit (Rp)", 2),
                XlsxCell.Text("Kredit (Rp)", 2)
            )
        )
        var totalJournalAmt = 0.0
        data.journalEntries.forEach { entry ->
            totalJournalAmt += entry.amount
            sheet2Rows.add(
                listOf(
                    XlsxCell.Text(dateTimeFmt.format(Date(entry.timestamp))),
                    XlsxCell.Text(entry.refNo),
                    XlsxCell.Text(entry.debitAccount),
                    XlsxCell.Text(entry.creditAccount),
                    XlsxCell.Text(entry.description),
                    XlsxCell.Text(entry.pjName),
                    XlsxCell.Number(entry.amount, 3),
                    XlsxCell.Number(entry.amount, 3)
                )
            )
        }
        sheet2Rows.add(
            listOf(
                XlsxCell.Text("", 1),
                XlsxCell.Text("", 1),
                XlsxCell.Text("TOTAL JURNAL (SEIMBANG)", 1),
                XlsxCell.Text("", 1),
                XlsxCell.Text("${data.journalEntries.size} entri", 1),
                XlsxCell.Text("", 1),
                XlsxCell.Number(totalJournalAmt, 4),
                XlsxCell.Number(totalJournalAmt, 4)
            )
        )

        // Sheet 3: Riwayat Penjualan & Pembelian
        val sheet3Rows = mutableListOf<List<XlsxCell>>()
        sheet3Rows.add(listOf(XlsxCell.Text("RIWAYAT PENJUALAN PRODUK & PEMBELIAN BAHAN", 1)))
        sheet3Rows.add(emptyList())
        sheet3Rows.add(
            listOf(
                XlsxCell.Text("Jenis", 2),
                XlsxCell.Text("Waktu", 2),
                XlsxCell.Text("Item / Bahan", 2),
                XlsxCell.Text("Metode / Alasan", 2),
                XlsxCell.Text("PJ Piket / Otorisasi", 2),
                XlsxCell.Text("Shift", 2),
                XlsxCell.Text("Nominal (Rp)", 2)
            )
        )
        data.transactions.forEach { tx ->
            sheet3Rows.add(
                listOf(
                    XlsxCell.Text("PENJUALAN"),
                    XlsxCell.Text(dateTimeFmt.format(Date(tx.timestamp))),
                    XlsxCell.Text(tx.items.joinToString { "${it.quantity}x ${it.name}" }),
                    XlsxCell.Text("${tx.paymentMethod}${if (tx.customerName.isNotBlank()) " (${tx.customerName})" else ""}"),
                    XlsxCell.Text(tx.cashierName),
                    XlsxCell.Text(tx.cashierShift),
                    XlsxCell.Number(tx.total, 3)
                )
            )
        }
        data.stockLogs.forEach { log ->
            val jenis = if (log.delta >= 0) "PEMBELIAN / RESTOCK" else "KOREKSI STOK"
            sheet3Rows.add(
                listOf(
                    XlsxCell.Text(jenis),
                    XlsxCell.Text(dateTimeFmt.format(Date(log.timestamp))),
                    XlsxCell.Text("${log.ingredientName} (${if (log.delta >= 0) "+" else ""}${log.delta.toInt()})"),
                    XlsxCell.Text(log.reason),
                    XlsxCell.Text(log.authorizedBy),
                    XlsxCell.Text("Otorisasi Stok"),
                    XlsxCell.Number(log.costImpact, 3)
                )
            )
        }

        // Sheet 4: Rekap PJ Piket
        val sheet4Rows = mutableListOf<List<XlsxCell>>()
        sheet4Rows.add(listOf(XlsxCell.Text("REKAP PENANGGUNG JAWAB (PJ) SETIAP WAKTU PIKET", 1)))
        sheet4Rows.add(emptyList())
        sheet4Rows.add(
            listOf(
                XlsxCell.Text("Nama PJ", 2),
                XlsxCell.Text("Waktu Piket / Shift", 2),
                XlsxCell.Text("Jumlah Transaksi", 2),
                XlsxCell.Text("Total Omzet (Rp)", 2),
                XlsxCell.Text("Total Kasbon (Rp)", 2),
                XlsxCell.Text("Aktivitas Stok", 2),
                XlsxCell.Text("Nilai Pembelian Stok (Rp)", 2)
            )
        )
        data.pjSummaries.forEach { pj ->
            sheet4Rows.add(
                listOf(
                    XlsxCell.Text(pj.pjName),
                    XlsxCell.Text(pj.shiftLabel),
                    XlsxCell.Number(pj.salesCount.toDouble(), 0),
                    XlsxCell.Number(pj.salesRevenue, 3),
                    XlsxCell.Number(pj.kasbonTotal, 3),
                    XlsxCell.Number(pj.stockActionCount.toDouble(), 0),
                    XlsxCell.Number(pj.stockPurchaseCost, 3)
                )
            )
        }

        val sheets = listOf(
            "Laba_Rugi" to sheet1Rows,
            "Jurnal_Akuntansi" to sheet2Rows,
            "Penjualan_Pembelian" to sheet3Rows,
            "Rekap_PJ_Piket" to sheet4Rows
        )
        writeXlsxWorkbook(sheets, outputStream)
    }

    private fun writeXlsxWorkbook(
        sheets: List<Pair<String, List<List<XlsxCell>>>>,
        outputStream: OutputStream
    ) {
        ZipOutputStream(outputStream).use { zip ->
            // 1. [Content_Types].xml
            val sheetOverrides = sheets.indices.joinToString("\n") { i ->
                """  <Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/contenttypes">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
$sheetOverrides
</Types>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 2. _rels/.rels
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 3. xl/workbook.xml
            val sheetTags = sheets.mapIndexed { i, (name, _) ->
                """    <sheet name="${xmlEscape(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
            }.joinToString("\n")
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
$sheetTags
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 4. xl/_rels/workbook.xml.rels
            val sheetRels = sheets.indices.joinToString("\n") { i ->
                """  <Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>"""
            }
            val styleRelId = "rId${sheets.size + 1}"
            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$sheetRels
  <Relationship Id="$styleRelId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 5. xl/styles.xml
            zip.putNextEntry(ZipEntry("xl/styles.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="&quot;Rp &quot;#,##0"/>
  </numFmts>
  <fonts count="3">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1D6B4F"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="5">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    <xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/>
  </cellXfs>
</styleSheet>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 6. xl/worksheets/sheetN.xml
            sheets.forEachIndexed { idx, (_, rows) ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet${idx + 1}.xml"))
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                sb.append("""<cols><col min="1" max="1" width="22" customWidth="1"/><col min="2" max="2" width="22" customWidth="1"/><col min="3" max="5" width="30" customWidth="1"/><col min="6" max="8" width="20" customWidth="1"/></cols>""")
                sb.append("<sheetData>")
                rows.forEachIndexed { rowIdx, cells ->
                    val rNum = rowIdx + 1
                    sb.append("""<row r="$rNum">""")
                    cells.forEachIndexed { colIdx, cell ->
                        val ref = "${colLetter(colIdx)}$rNum"
                        when (cell) {
                            is XlsxCell.Text -> {
                                sb.append("""<c r="$ref" t="inlineStr" s="${cell.style}"><is><t>${xmlEscape(cell.value)}</t></is></c>""")
                            }
                            is XlsxCell.Number -> {
                                sb.append("""<c r="$ref" s="${cell.style}"><v>${cell.value}</v></c>""")
                            }
                        }
                    }
                    sb.append("</row>")
                }
                sb.append("</sheetData></worksheet>")
                zip.write(sb.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun colLetter(index: Int): String {
        var idx = index
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.insert(0, ('A' + (idx % 26)))
            idx = idx / 26 - 1
        }
        return sb.toString()
    }

    private fun xmlEscape(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // =========================================================================
    // 3. FILE PROVIDER CACHE & SHARE HELPERS
    // =========================================================================
    fun writePdfToCacheUri(context: Context, data: FinancialReportData, fileName: String): Uri {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            generatePdfReport(data, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun writeExcelToCacheUri(context: Context, data: FinancialReportData, fileName: String): Uri {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            generateExcelReport(data, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun openFile(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Buka Dokumen"))
    }
}
