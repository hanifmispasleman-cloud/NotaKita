package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.WarkopRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class SessionRole {
    data object Locked : SessionRole()
    data class Manager(val name: String) : SessionRole()
    data class Cashier(val cashier: CashierSubAccount) : SessionRole()
}

data class PosCartState(
    val items: Map<String, CartItem> = emptyMap(),
    val discountType: String = "%", // "%" atau "Rp"
    val discountValue: Double = 0.0,
    val paymentMethod: String = "Tunai", // "Tunai", "QRIS", "Transfer", "Kartu", "Utang/Kasbon"
    val cashReceived: Double = 0.0,
    val customerName: String = "",
    val note: String = "",
    val lastReceipt: String? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val subtotal: Double
        get() = items.values.sumOf { it.price * it.quantity }

    val discountAmount: Double
        get() {
            return if (discountType == "%") {
                val pct = discountValue.coerceIn(0.0, 100.0)
                (subtotal * pct / 100.0)
            } else {
                discountValue.coerceIn(0.0, subtotal)
            }
        }

    val total: Double
        get() = (subtotal - discountAmount).coerceAtLeast(0.0)

    val change: Double
        get() = if (paymentMethod == "Tunai" && cashReceived >= total) cashReceived - total else 0.0
}

class WarkopViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WarkopRepository(application)

    val storeProfile: StateFlow<StoreProfile> = repository.observeStoreProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreProfile())

    val inventoryList: StateFlow<List<InventoryItem>> = repository.observeInventory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val menuList: StateFlow<List<MenuItem>> = repository.observeMenuItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionList: StateFlow<List<Transaction>> = repository.observeTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debtList: StateFlow<List<DebtAccount>> = repository.observeDebts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashierList: StateFlow<List<CashierSubAccount>> = repository.observeCashiers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockLogList: StateFlow<List<StockLog>> = repository.observeStockLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Role-Based Security Session (Manager vs Kasir Lapangan)
    private val _sessionRole = MutableStateFlow<SessionRole>(SessionRole.Locked)
    val sessionRole: StateFlow<SessionRole> = _sessionRole.asStateFlow()

    // Active duty operator & shift piket
    private val _activeOperator = MutableStateFlow("Manager")
    val activeOperator: StateFlow<String> = _activeOperator.asStateFlow()

    private val _activeShift = MutableStateFlow("Manager (Full Shift)")
    val activeShift: StateFlow<String> = _activeShift.asStateFlow()

    // POS Cart State
    private val _cartState = MutableStateFlow(PosCartState())
    val cartState: StateFlow<PosCartState> = _cartState.asStateFlow()

    // UI Message feedback
    private val _uiToast = MutableStateFlow<String?>(null)
    val uiToast: StateFlow<String?> = _uiToast.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedDefaultDataIfEmpty()
        }
    }

    fun clearToast() {
        _uiToast.value = null
    }

    fun showToast(msg: String) {
        _uiToast.value = msg
    }

    // --- SISTEM PENGAMAN LOGIN (MANAGER VS KASIR LAPANGAN) ---
    fun loginManagerWithPin(pin: String): Boolean {
        val valid = pin == storeProfile.value.managerPin
        if (valid) {
            val mgr = storeProfile.value.managerName.ifBlank { "Manager" }
            _sessionRole.value = SessionRole.Manager(mgr)
            _activeOperator.value = mgr
            _activeShift.value = "Manager (Full Shift)"
            showToast("Selamat datang, Manager $mgr")
        } else {
            showToast("PIN Manager salah!")
        }
        return valid
    }

    fun loginManagerWithBiometric() {
        val mgr = storeProfile.value.managerName.ifBlank { "Manager" }
        _sessionRole.value = SessionRole.Manager(mgr)
        _activeOperator.value = mgr
        _activeShift.value = "Manager (Full Shift)"
        showToast("Verifikasi Sidik Jari berhasil. Selamat datang, Manager $mgr")
    }

    fun loginCashierWithPin(cashier: CashierSubAccount, pin: String): Boolean {
        if (!cashier.isActive) {
            showToast("Akun kasir ini sedang non-aktif")
            return false
        }
        val valid = pin == cashier.pin
        if (valid) {
            _sessionRole.value = SessionRole.Cashier(cashier)
            _activeOperator.value = cashier.name
            _activeShift.value = cashier.shift.ifBlank { "Shift Umum" }
            showToast("Masuk sebagai Kasir: ${cashier.name} (${_activeShift.value})")
        } else {
            showToast("PIN Kasir untuk ${cashier.name} salah!")
        }
        return valid
    }

    fun loginCashierWithBiometric(cashier: CashierSubAccount) {
        if (!cashier.isActive) {
            showToast("Akun kasir ini sedang non-aktif")
            return
        }
        _sessionRole.value = SessionRole.Cashier(cashier)
        _activeOperator.value = cashier.name
        _activeShift.value = cashier.shift.ifBlank { "Shift Umum" }
        showToast("Sidik Jari terverifikasi. Shift dimulai: ${cashier.name}")
    }

    fun logoutSession() {
        _sessionRole.value = SessionRole.Locked
        clearCart()
        showToast("Sesi dikunci. Silakan masuk kembali.")
    }

    fun setActiveOperator(name: String, shift: String = "Shift Umum") {
        _activeOperator.value = name
        _activeShift.value = shift
        showToast("Operator aktif diubah ke: $name")
    }

    // --- POS ACTIONS ---
    fun addToCart(menu: MenuItem) {
        val current = _cartState.value
        val existing = current.items[menu.id]
        val newQty = (existing?.quantity ?: 0) + 1

        // Check portion availability
        val availablePortions = calculatePortionsAvailable(menu, inventoryList.value)
        if (newQty > availablePortions) {
            showToast("Stok bahan untuk ${menu.name} tidak mencukupi!")
            return
        }

        val updatedMap = current.items.toMutableMap()
        val cost = menu.recipe.sumOf { r ->
            val ing = inventoryList.value.find { it.id == r.ingredientId }
            (ing?.costPerUnit ?: 0.0) * r.quantity
        }
        updatedMap[menu.id] = CartItem(
            menuId = menu.id,
            name = menu.name,
            category = menu.category,
            price = menu.price,
            cost = cost,
            quantity = newQty
        )
        _cartState.value = current.copy(items = updatedMap)
    }

    fun updateCartQuantity(menuId: String, delta: Int) {
        val current = _cartState.value
        val existing = current.items[menuId] ?: return
        val newQty = existing.quantity + delta
        val menu = menuList.value.find { it.id == menuId }

        if (menu != null && delta > 0) {
            val availablePortions = calculatePortionsAvailable(menu, inventoryList.value)
            if (newQty > availablePortions) {
                showToast("Stok bahan tidak mencukupi untuk menambah porsi!")
                return
            }
        }

        val updatedMap = current.items.toMutableMap()
        if (newQty <= 0) {
            updatedMap.remove(menuId)
        } else {
            updatedMap[menuId] = existing.copy(quantity = newQty)
        }
        _cartState.value = current.copy(items = updatedMap)
    }

    fun clearCart() {
        _cartState.value = _cartState.value.copy(
            items = emptyMap(),
            discountValue = 0.0,
            cashReceived = 0.0,
            customerName = "",
            note = "",
            errorMessage = null
        )
    }

    fun setDiscount(type: String, value: Double) {
        _cartState.value = _cartState.value.copy(discountType = type, discountValue = value)
    }

    fun setPaymentMethod(method: String) {
        _cartState.value = _cartState.value.copy(paymentMethod = method)
    }

    fun setCashReceived(amount: Double) {
        _cartState.value = _cartState.value.copy(cashReceived = amount)
    }

    fun setCustomerName(name: String) {
        _cartState.value = _cartState.value.copy(customerName = name)
    }

    fun setNote(note: String) {
        _cartState.value = _cartState.value.copy(note = note)
    }

    fun processPayment() {
        val cart = _cartState.value
        if (cart.items.isEmpty()) {
            showToast("Keranjang belanja masih kosong!")
            return
        }

        if (cart.paymentMethod == "Tunai" && cart.cashReceived < cart.total) {
            showToast("Uang tunai yang diterima kurang!")
            return
        }

        if ((cart.paymentMethod == "Utang/Kasbon" || cart.paymentMethod.contains("Utang")) && cart.customerName.isBlank()) {
            showToast("Nama pelanggan wajib diisi untuk transaksi utang/kasbon!")
            return
        }

        viewModelScope.launch {
            _cartState.value = cart.copy(isProcessing = true)
            val result = repository.processTransaction(
                items = cart.items.values.toList(),
                discount = cart.discountAmount,
                paymentMethod = cart.paymentMethod,
                cashierName = activeOperator.value,
                cashierShift = activeShift.value,
                customerName = cart.customerName.trim(),
                note = cart.note.trim(),
                menuList = menuList.value,
                inventoryList = inventoryList.value
            )

            result.onSuccess { tx ->
                val receipt = buildString {
                    appendLine("=== ${storeProfile.value.storeName.uppercase()} ===")
                    appendLine("Waktu: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("id")).format(java.util.Date(tx.timestamp))}")
                    appendLine("Kasir / PJ: ${tx.cashierName} (${tx.cashierShift})")
                    if (tx.customerName.isNotBlank()) appendLine("Pelanggan: ${tx.customerName}")
                    appendLine("------------------------")
                    tx.items.forEach {
                        appendLine("${it.quantity}x ${it.name}  Rp ${formatRupiah(it.price * it.quantity)}")
                    }
                    appendLine("------------------------")
                    appendLine("Subtotal: Rp ${formatRupiah(tx.subtotal)}")
                    if (tx.discount > 0) appendLine("Diskon: -Rp ${formatRupiah(tx.discount)}")
                    appendLine("TOTAL: Rp ${formatRupiah(tx.total)}")
                    appendLine("Metode: ${tx.paymentMethod}")
                    if (tx.paymentMethod == "Tunai" && cart.cashReceived > 0) {
                        appendLine("Tunai: Rp ${formatRupiah(cart.cashReceived)}")
                        appendLine("Kembali: Rp ${formatRupiah(cart.change)}")
                    }
                    if (tx.status.contains("Kasbon")) {
                        appendLine("STATUS: TERCATAT DI BUKU KASBON")
                    }
                }

                _cartState.value = PosCartState(
                    lastReceipt = receipt,
                    successMessage = "Transaksi berhasil diproses!"
                )
                showToast("Transaksi berhasil disimpan!")
            }.onFailure { err ->
                _cartState.value = cart.copy(
                    isProcessing = false,
                    errorMessage = err.message ?: "Gagal memproses transaksi"
                )
                showToast("Gagal: ${err.message}")
            }
        }
    }

    /**
     * Menghapus / membatalkan transaksi membutuhkan verifikasi PIN karyawan (kasir/manager)
     * atau verifikasi Sidik Jari (Biometric).
     */
    fun verifyEmployeePinAndVoid(
        transaction: Transaction,
        inputPin: String,
        useBiometric: Boolean = false
    ): Boolean {
        val authorizer: String = if (useBiometric) {
            "${activeOperator.value} (Sidik Jari)"
        } else {
            val matchedCashier = cashierList.value.find { it.isActive && it.pin == inputPin }
            when {
                inputPin == storeProfile.value.managerPin -> "${storeProfile.value.managerName} (Manager - PIN)"
                matchedCashier != null -> "${matchedCashier.name} (Kasir - PIN)"
                else -> {
                    showToast("PIN Karyawan / Manager tidak cocok! Penghapusan dibatalkan.")
                    return false
                }
            }
        }

        viewModelScope.launch {
            val res = repository.voidTransaction(
                transaction = transaction,
                menuList = menuList.value,
                inventoryList = inventoryList.value
            )
            res.onSuccess {
                showToast("Transaksi dihapus ($authorizer) dan stok dikembalikan")
            }.onFailure {
                showToast("Gagal membatalkan: ${it.message}")
            }
        }
        return true
    }

    // --- BUKU KASBON ---
    fun payDebt(debtId: String, amount: Double, note: String) {
        if (amount <= 0) {
            showToast("Nominal pembayaran harus lebih dari 0")
            return
        }
        viewModelScope.launch {
            val res = repository.payDebt(
                debtId = debtId,
                amount = amount,
                cashierName = activeOperator.value,
                note = note
            )
            res.onSuccess {
                showToast("Pembayaran kasbon berhasil dicatat")
            }.onFailure {
                showToast("Gagal mencatat pembayaran: ${it.message}")
            }
        }
    }

    // --- PENUNDAAN BIAYA OPERASIONAL & STATUS TOKO ---
    fun toggleStoreOpen() {
        val current = storeProfile.value.isOpen
        viewModelScope.launch {
            repository.toggleStoreOpen(!current)
        }
    }

    fun updateStoreNames(storeName: String, managerName: String) {
        viewModelScope.launch {
            repository.updateStoreNames(storeName.trim(), managerName.trim())
            showToast("Nama toko dan manager berhasil diperbarui")
        }
    }

    fun updateManagerPin(newPin: String) {
        if (newPin.length < 4) {
            showToast("PIN minimal 4 digit")
            return
        }
        viewModelScope.launch {
            repository.updateManagerPin(newPin)
            showToast("PIN Manager berhasil diperbarui")
        }
    }

    fun updateOperatingCost(operatingCost: OperatingCost) {
        viewModelScope.launch {
            repository.updateOperatingCost(operatingCost)
            showToast("Biaya operasional berhasil diperbarui")
        }
    }

    // --- OTORISASI STOK OLEH MANAGER ---
    fun verifyManagerPin(inputPin: String): Boolean {
        val managerPin = storeProfile.value.managerPin
        return inputPin == managerPin
    }

    fun setInventoryStockWithAuth(
        itemId: String,
        newStock: Double,
        managerPin: String,
        reason: String,
        useBiometric: Boolean = false
    ): Boolean {
        if (!useBiometric && !verifyManagerPin(managerPin)) {
            showToast("Verifikasi gagal: PIN Manager tidak valid!")
            return false
        }
        val authLabel = if (useBiometric) {
            "${storeProfile.value.managerName} (Sidik Jari) · PJ: ${activeOperator.value}"
        } else {
            "${storeProfile.value.managerName} (Otorisasi PIN) · PJ: ${activeOperator.value}"
        }
        viewModelScope.launch {
            repository.setInventoryStock(
                itemId = itemId,
                newStock = newStock,
                authorizedBy = authLabel,
                reason = reason.ifBlank { "Penyesuaian stok fisik" }
            )
            showToast("Stok berhasil diperbarui ($authLabel)")
        }
        return true
    }

    fun addInventoryItem(
        item: InventoryItem,
        managerPin: String,
        useBiometric: Boolean = false
    ): Boolean {
        if (!useBiometric && !verifyManagerPin(managerPin)) {
            showToast("Verifikasi gagal: PIN Manager salah!")
            return false
        }
        val authLabel = if (useBiometric) {
            "${storeProfile.value.managerName} (Sidik Jari) · PJ: ${activeOperator.value}"
        } else {
            "${storeProfile.value.managerName} (Otorisasi PIN) · PJ: ${activeOperator.value}"
        }
        viewModelScope.launch {
            repository.addInventoryItem(item, authorizedBy = authLabel)
            showToast("Bahan baku ${item.name} berhasil ditambahkan")
        }
        return true
    }

    fun deleteInventoryItem(itemId: String, managerPin: String, useBiometric: Boolean = false): Boolean {
        if (!useBiometric && !verifyManagerPin(managerPin)) {
            showToast("Verifikasi gagal: PIN Manager salah!")
            return false
        }
        viewModelScope.launch {
            repository.deleteInventoryItem(itemId)
            showToast("Bahan baku berhasil dihapus")
        }
        return true
    }

    // --- MENU MANAGEMENT ---
    fun saveMenuItem(item: MenuItem) {
        viewModelScope.launch {
            repository.saveMenuItem(item)
            showToast("Menu ${item.name} berhasil disimpan")
        }
    }

    fun deleteMenuItem(menuId: String) {
        viewModelScope.launch {
            repository.deleteMenuItem(menuId)
            showToast("Menu berhasil dihapus")
        }
    }

    // --- CASHIER MANAGEMENT ---
    fun addCashier(name: String, pin: String, shift: String = "Pagi (07:00 - 15:00)") {
        if (name.isBlank() || pin.length < 4) {
            showToast("Nama kasir dan PIN (min 4 digit) wajib diisi")
            return
        }
        viewModelScope.launch {
            repository.addCashier(
                name = name.trim(),
                pin = pin.trim(),
                shift = shift.ifBlank { "Shift Umum" }
            )
            showToast("Kasir $name ($shift) berhasil didaftarkan")
        }
    }

    fun deleteCashier(cashierId: String) {
        viewModelScope.launch {
            repository.deleteCashier(cashierId)
            showToast("Sub-akun kasir dihapus")
        }
    }

    // --- HELPER CALCULATIONS ---
    fun calculatePortionsAvailable(menu: MenuItem, inventory: List<InventoryItem>): Int {
        if (menu.recipe.isEmpty()) return 999
        var minPortions = Int.MAX_VALUE
        for (recipeItem in menu.recipe) {
            val ing = inventory.find { it.id == recipeItem.ingredientId }
            if (ing == null || ing.stock <= 0.0 || recipeItem.quantity <= 0.0) {
                return 0
            }
            val possible = (ing.stock / recipeItem.quantity).toInt()
            if (possible < minPortions) {
                minPortions = possible
            }
        }
        return if (minPortions == Int.MAX_VALUE) 0 else minPortions
    }

    fun formatRupiah(amount: Double): String {
        return java.text.NumberFormat.getNumberInstance(java.util.Locale("id")).format(amount.toLong())
    }
}
