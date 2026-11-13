package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CartItem
import com.example.data.model.CashierSubAccount
import com.example.data.model.DebtAccount
import com.example.data.model.OperatingCost
import com.example.data.model.StockLog
import com.example.data.model.StoreProfile
import com.example.data.model.Transaction
import com.example.ui.viewmodel.SessionRole
import com.example.ui.viewmodel.WarkopViewModel
import com.example.util.ReportExporter
import com.example.util.ReportPeriod
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportAndSecurityTest {

    private fun sampleReportData() = ReportExporter.buildReportData(
        period = ReportPeriod.LAST_7_DAYS,
        storeProfile = StoreProfile(
            storeName = "Warkop Emak",
            managerName = "Emak",
            isOpen = true,
            managerPin = "1234",
            operatingCost = OperatingCost(targetUnitsPerDay = 80.0)
        ),
        allTransactions = listOf(
            Transaction(
                id = "tx1",
                timestamp = System.currentTimeMillis(),
                items = listOf(
                    CartItem(menuId = "m1", name = "Kopi Susu", price = 18000.0, cost = 7150.0, quantity = 2)
                ),
                subtotal = 36000.0,
                discount = 0.0,
                total = 36000.0,
                paymentMethod = "Tunai",
                cashierName = "Kasir Pagi",
                cashierShift = "Pagi (07:00 - 15:00)"
            ),
            Transaction(
                id = "tx2",
                timestamp = System.currentTimeMillis(),
                items = listOf(
                    CartItem(menuId = "m2", name = "Americano", price = 15000.0, cost = 4000.0, quantity = 1)
                ),
                subtotal = 15000.0,
                discount = 0.0,
                total = 15000.0,
                paymentMethod = "Utang/Kasbon",
                cashierName = "Kasir Sore",
                cashierShift = "Sore (15:00 - 23:00)",
                customerName = "Pak Budi",
                status = "Belum Lunas - Kasbon"
            )
        ),
        allStockLogs = listOf(
            StockLog(
                id = "log1",
                timestamp = System.currentTimeMillis(),
                ingredientName = "Kopi",
                delta = 1000.0,
                costImpact = 200000.0,
                authorizedBy = "Emak (Otorisasi PIN)",
                reason = "Restock harian"
            )
        ),
        allDebts = listOf(
            DebtAccount(
                id = "d1",
                customerName = "Pak Budi",
                totalDebt = 15000.0,
                remainingDebt = 15000.0,
                status = "Belum Lunas"
            )
        ),
        cashiers = listOf(
            CashierSubAccount(id = "c1", name = "Kasir Pagi", pin = "1111", shift = "Pagi (07:00 - 15:00)"),
            CashierSubAccount(id = "c2", name = "Kasir Sore", pin = "2222", shift = "Sore (15:00 - 23:00)")
        )
    )

    @Test
    fun `report data aggregates sales purchases profit trend and pj shifts`() {
        val data = sampleReportData()
        assertEquals(51000.0, data.netSales, 0.01)
        assertEquals(18300.0, data.totalHpp, 0.01)
        assertEquals(32700.0, data.grossProfit, 0.01)
        assertEquals(200000.0, data.totalPurchases, 0.01)
        assertEquals(15000.0, data.totalKasbonReceivable, 0.01)
        assertEquals(3, data.pjSummaries.size)
        assertEquals(7, data.trendPoints.size)
        assertTrue(data.journalEntries.any { it.debitAccount.contains("Piutang") })
        assertTrue(data.journalEntries.any { it.debitAccount.contains("Persediaan") })
        assertTrue(data.journalEntries.any { it.debitAccount.contains("Beban Operasional") })
    }

    @Test
    fun `excel generator writes valid multi-sheet xlsx workbook with accounting journal`() {
        val data = sampleReportData()
        val out = ByteArrayOutputStream()
        ReportExporter.generateExcelReport(data, out)
        val bytes = out.toByteArray()
        assertTrue(bytes.size > 500)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/styles.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet3.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet4.xml"))

        val sheet1 = entries["xl/worksheets/sheet1.xml"]!!
        val sheet2 = entries["xl/worksheets/sheet2.xml"]!!
        val sheet4 = entries["xl/worksheets/sheet4.xml"]!!
        assertTrue(sheet1.contains("LABA BERSIH"))
        assertTrue(sheet2.contains("TOTAL JURNAL (SEIMBANG)"))
        assertTrue(sheet4.contains("Kasir Pagi"))
        assertTrue(sheet4.contains("Kasir Sore"))
    }

    @Test
    fun `registerManagerAccount validates email and pin and unlocks Manager session`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(app)
        }
        val vm = WarkopViewModel(app)

        assertFalse(vm.registerManagerAccount("Warkop Emak", "Budi", "bukan-email", "5678"))
        assertTrue(vm.sessionRole.value is SessionRole.Locked)

        assertFalse(vm.registerManagerAccount("Warkop Emak", "Budi", "budi@warkop.id", "12"))
        assertTrue(vm.sessionRole.value is SessionRole.Locked)

        val ok = vm.registerManagerAccount("Warkop Jaya", "Budi", "budi@warkop.id", "5678")
        assertTrue(ok)
        assertEquals(SessionRole.Manager("Budi"), vm.sessionRole.value)
        assertEquals("Budi", vm.activeOperator.value)
    }
}
