package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "Manager", // "Manager" atau "Kasir"
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OperatingCost(
    val waterUsage: Double = 0.5,
    val waterTariff: Double = 12000.0,
    val electricityUsage: Double = 8.0,
    val electricityTariff: Double = 1500.0,
    val gasUsage: Double = 1.0,
    val gasTariff: Double = 22000.0,
    val laborHours: Double = 10.0,
    val laborTariff: Double = 12000.0,
    val targetUnitsPerDay: Double = 80.0
) {
    fun dailyTotal(): Double {
        return (waterUsage * waterTariff) +
                (electricityUsage * electricityTariff) +
                (gasUsage * gasTariff) +
                (laborHours * laborTariff)
    }

    fun perUnitCost(): Double {
        val units = if (targetUnitsPerDay > 0) targetUnitsPerDay else 1.0
        return dailyTotal() / units
    }
}

@IgnoreExtraProperties
data class StoreProfile(
    @DocumentId val id: String = "default_store",
    val userId: String = "",
    val storeName: String = "Warkop Emak",
    val managerName: String = "Emak",
    val managerEmail: String = "",
    val isOpen: Boolean = true,
    val managerPin: String = "1234",
    val operatingCost: OperatingCost = OperatingCost(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val unit: String = "pcs",
    val stock: Double = 0.0,
    val costPerUnit: Double = 0.0,
    val minStock: Double = 0.0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RecipeItem(
    val ingredientId: String = "",
    val ingredientName: String = "",
    val quantity: Double = 0.0,
    val unit: String = ""
)

@IgnoreExtraProperties
data class MenuItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val category: String = "Kopi",
    val recipe: List<RecipeItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CartItem(
    val menuId: String = "",
    val name: String = "",
    val category: String = "Lainnya",
    val price: Double = 0.0,
    val cost: Double = 0.0,
    val quantity: Int = 1
)

@IgnoreExtraProperties
data class Transaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val items: List<CartItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val paymentMethod: String = "Tunai", // "Tunai", "QRIS", "Transfer", "Kartu", "Utang/Kasbon"
    val cashierName: String = "Kasir",
    val cashierShift: String = "Shift Umum",
    val customerName: String = "",
    val note: String = "",
    val status: String = "Lunas", // "Lunas", "Belum Lunas - Kasbon"
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DebtRecord(
    val transactionId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val amount: Double = 0.0,
    val note: String = ""
)

@IgnoreExtraProperties
data class PaymentRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val amount: Double = 0.0,
    val cashierName: String = "Kasir",
    val note: String = ""
)

@IgnoreExtraProperties
data class DebtAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val totalDebt: Double = 0.0,
    val remainingDebt: Double = 0.0,
    val status: String = "Belum Lunas", // "Belum Lunas", "Cicilan", "Lunas"
    val entries: List<DebtRecord> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StockLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val ingredientName: String = "",
    val delta: Double = 0.0,
    val costImpact: Double = 0.0,
    val authorizedBy: String = "Manager",
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CashierSubAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val pin: String = "0000",
    val shift: String = "Pagi (07:00 - 15:00)",
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)
