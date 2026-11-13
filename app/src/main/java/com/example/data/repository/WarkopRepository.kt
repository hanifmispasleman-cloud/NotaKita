package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.*
import com.example.util.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class WarkopRepository(private val context: Context) {

    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
    )

    val currentUserId: String?
        get() = auth.currentUser?.uid

    // --- LOCAL FALLBACK STATE (MODE TANPA LOGIN / PREVIEW) ---
    private val localStoreProfile = MutableStateFlow(
        StoreProfile(
            id = "default_store",
            userId = "local_preview",
            storeName = "Warkop Emak",
            managerName = "Emak",
            isOpen = true,
            managerPin = "1234",
            operatingCost = OperatingCost()
        )
    )

    private val localInventory = MutableStateFlow(
        listOf(
            InventoryItem(id = "ing_kopi", userId = "local_preview", name = "Kopi", unit = "g", stock = 5000.0, costPerUnit = 200.0, minStock = 500.0),
            InventoryItem(id = "ing_susu", userId = "local_preview", name = "Susu", unit = "ml", stock = 20000.0, costPerUnit = 18.0, minStock = 2000.0),
            InventoryItem(id = "ing_cup", userId = "local_preview", name = "Cup", unit = "pcs", stock = 300.0, costPerUnit = 400.0, minStock = 30.0),
            InventoryItem(id = "ing_gula", userId = "local_preview", name = "Gula Aren", unit = "ml", stock = 3000.0, costPerUnit = 30.0, minStock = 300.0),
            InventoryItem(id = "ing_teh", userId = "local_preview", name = "Teh", unit = "g", stock = 1000.0, costPerUnit = 100.0, minStock = 100.0)
        )
    )

    private val localMenu = MutableStateFlow(
        listOf(
            MenuItem(
                id = "menu_kopisusu",
                userId = "local_preview",
                name = "Kopi Susu",
                price = 18000.0,
                category = "Kopi",
                recipe = listOf(
                    RecipeItem("ing_kopi", "Kopi", 18.0, "g"),
                    RecipeItem("ing_susu", "Susu", 150.0, "ml"),
                    RecipeItem("ing_cup", "Cup", 1.0, "pcs"),
                    RecipeItem("ing_gula", "Gula Aren", 15.0, "ml")
                )
            ),
            MenuItem(
                id = "menu_americano",
                userId = "local_preview",
                name = "Americano",
                price = 15000.0,
                category = "Kopi",
                recipe = listOf(
                    RecipeItem("ing_kopi", "Kopi", 18.0, "g"),
                    RecipeItem("ing_cup", "Cup", 1.0, "pcs")
                )
            ),
            MenuItem(
                id = "menu_esteh",
                userId = "local_preview",
                name = "Es Teh",
                price = 8000.0,
                category = "Non-Kopi",
                recipe = listOf(
                    RecipeItem("ing_teh", "Teh", 5.0, "g"),
                    RecipeItem("ing_cup", "Cup", 1.0, "pcs"),
                    RecipeItem("ing_gula", "Gula Aren", 15.0, "ml")
                )
            ),
            MenuItem(
                id = "menu_latte",
                userId = "local_preview",
                name = "Latte",
                price = 22000.0,
                category = "Kopi",
                recipe = listOf(
                    RecipeItem("ing_kopi", "Kopi", 18.0, "g"),
                    RecipeItem("ing_susu", "Susu", 200.0, "ml"),
                    RecipeItem("ing_cup", "Cup", 1.0, "pcs")
                )
            )
        )
    )

    private val localTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val localDebts = MutableStateFlow<List<DebtAccount>>(emptyList())
    private val localStockLogs = MutableStateFlow(
        listOf(
            StockLog(
                id = "log_init_kopi",
                userId = "local_preview",
                timestamp = System.currentTimeMillis() - 86400000L,
                ingredientName = "Kopi",
                delta = 5000.0,
                costImpact = 1000000.0,
                authorizedBy = "Emak (Manager)",
                reason = "Pembelian stok awal bahan baku"
            ),
            StockLog(
                id = "log_init_susu",
                userId = "local_preview",
                timestamp = System.currentTimeMillis() - 86400000L,
                ingredientName = "Susu",
                delta = 20000.0,
                costImpact = 360000.0,
                authorizedBy = "Emak (Manager)",
                reason = "Pembelian stok awal bahan baku"
            ),
            StockLog(
                id = "log_init_cup",
                userId = "local_preview",
                timestamp = System.currentTimeMillis() - 43200000L,
                ingredientName = "Cup",
                delta = 300.0,
                costImpact = 120000.0,
                authorizedBy = "Emak (Manager)",
                reason = "Pembelian kemasan cup"
            )
        )
    )
    private val localCashiers = MutableStateFlow(
        listOf(
            CashierSubAccount(
                id = "cashier_1",
                userId = "local_preview",
                name = "Kasir Pagi",
                pin = "1111",
                shift = "Pagi (07:00 - 15:00)",
                isActive = true
            ),
            CashierSubAccount(
                id = "cashier_2",
                userId = "local_preview",
                name = "Kasir Sore",
                pin = "2222",
                shift = "Sore (15:00 - 23:00)",
                isActive = true
            )
        )
    )

    private fun storeDoc(uid: String, storeId: String = "default_store") =
        db.collection("users").document(uid).collection("stores").document(storeId)

    // --- STORE PROFILE & PENUNDAAN BIAYA OPERASIONAL ---
    fun observeStoreProfile(storeId: String = "default_store"): Flow<StoreProfile> {
        val uid = currentUserId ?: return localStoreProfile
        return callbackFlow {
            val docRef = storeDoc(uid, storeId)
            val registration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WarkopRepo", "Error observing store profile", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val profile = snapshot.toObject(StoreProfile::class.java) ?: StoreProfile()
                    trySend(profile)
                } else {
                    val initial = StoreProfile(
                        id = storeId,
                        userId = uid,
                        storeName = "Warkop Emak",
                        managerName = auth.currentUser?.displayName?.ifBlank { "Emak" } ?: "Emak",
                        managerEmail = auth.currentUser?.email ?: "",
                        isOpen = true,
                        managerPin = "1234",
                        operatingCost = OperatingCost()
                    )
                    docRef.set(initial)
                    trySend(initial)
                }
            }
            awaitClose { registration.remove() }
        }
    }

    suspend fun registerManagerProfile(
        storeName: String,
        managerName: String,
        managerEmail: String,
        managerPin: String,
        storeId: String = "default_store"
    ) {
        val cleanStore = storeName.trim().ifBlank { "Warkop Emak" }
        val cleanManager = managerName.trim().ifBlank { "Manager" }
        val cleanEmail = managerEmail.trim()
        val cleanPin = managerPin.trim().ifBlank { "1234" }

        val uid = currentUserId
        if (uid == null) {
            localStoreProfile.value = localStoreProfile.value.copy(
                storeName = cleanStore,
                managerName = cleanManager,
                managerEmail = cleanEmail,
                managerPin = cleanPin
            )
            return
        }

        val userDoc = db.collection("users").document(uid)
        val existingUser = userDoc.get().await()
        val resolvedEmail = cleanEmail.ifBlank { auth.currentUser?.email ?: "" }
        if (!existingUser.exists()) {
            userDoc.set(
                UserProfile(
                    userId = uid,
                    displayName = cleanManager,
                    email = resolvedEmail,
                    role = "Manager"
                )
            ).await()
        } else {
            userDoc.update(
                mapOf(
                    "displayName" to cleanManager,
                    "email" to resolvedEmail,
                    "role" to "Manager"
                )
            ).await()
        }

        val storeRef = storeDoc(uid, storeId)
        val existingStore = storeRef.get().await()
        if (!existingStore.exists()) {
            storeRef.set(
                StoreProfile(
                    id = storeId,
                    userId = uid,
                    storeName = cleanStore,
                    managerName = cleanManager,
                    managerEmail = resolvedEmail,
                    isOpen = true,
                    managerPin = cleanPin,
                    operatingCost = OperatingCost()
                )
            ).await()
        } else {
            storeRef.update(
                mapOf(
                    "storeName" to cleanStore,
                    "managerName" to cleanManager,
                    "managerEmail" to resolvedEmail,
                    "managerPin" to cleanPin,
                    "userId" to uid
                )
            ).await()
        }
    }

    suspend fun ensureCloudUserAndStoreFromGoogle(
        storeName: String? = null,
        managerName: String? = null,
        storeId: String = "default_store"
    ) {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val resolvedName = managerName?.trim()?.takeIf { it.isNotBlank() }
            ?: user.displayName?.trim()?.takeIf { it.isNotBlank() }
            ?: "Manager"
        val resolvedEmail = user.email?.trim() ?: ""
        val resolvedStore = storeName?.trim()?.takeIf { it.isNotBlank() } ?: "Warkop Emak"

        val userDoc = db.collection("users").document(uid)
        val existingUser = userDoc.get().await()
        if (!existingUser.exists()) {
            userDoc.set(
                UserProfile(
                    userId = uid,
                    displayName = resolvedName,
                    email = resolvedEmail,
                    role = "Manager"
                )
            ).await()
        } else {
            userDoc.update(
                mapOf(
                    "displayName" to resolvedName,
                    "email" to resolvedEmail,
                    "role" to "Manager"
                )
            ).await()
        }

        val storeRef = storeDoc(uid, storeId)
        val existingStore = storeRef.get().await()
        if (!existingStore.exists()) {
            storeRef.set(
                StoreProfile(
                    id = storeId,
                    userId = uid,
                    storeName = resolvedStore,
                    managerName = resolvedName,
                    managerEmail = resolvedEmail,
                    isOpen = true,
                    managerPin = "1234",
                    operatingCost = OperatingCost()
                )
            ).await()
        } else if (resolvedEmail.isNotBlank()) {
            val currentStore = existingStore.toObject(StoreProfile::class.java)
            if (currentStore != null && currentStore.managerEmail.isBlank()) {
                storeRef.update(
                    mapOf(
                        "managerEmail" to resolvedEmail,
                        "userId" to uid
                    )
                ).await()
            }
        }
    }

    suspend fun updateStoreProfile(profile: StoreProfile, storeId: String = "default_store") {
        val uid = currentUserId
        if (uid == null) {
            localStoreProfile.value = profile
            return
        }
        storeDoc(uid, storeId).set(profile.copy(userId = uid)).await()
    }

    suspend fun toggleStoreOpen(isOpen: Boolean, storeId: String = "default_store") {
        val uid = currentUserId
        if (uid == null) {
            localStoreProfile.value = localStoreProfile.value.copy(isOpen = isOpen)
        } else {
            storeDoc(uid, storeId).update(
                mapOf(
                    "isOpen" to isOpen,
                    "userId" to uid
                )
            ).await()
        }

        val storeName = localStoreProfile.value.storeName
        NotificationHelper.showNotification(
            context,
            if (isOpen) "Toko Dibuka" else "Toko Ditutup",
            if (isOpen) "$storeName kini buka. Biaya operasional aktif."
            else "$storeName tutup. Beban biaya operasional ditunda."
        )
    }

    suspend fun updateStoreNames(storeName: String, managerName: String, storeId: String = "default_store") {
        val uid = currentUserId
        if (uid == null) {
            localStoreProfile.value = localStoreProfile.value.copy(
                storeName = storeName,
                managerName = managerName
            )
            return
        }
        storeDoc(uid, storeId).update(
            mapOf(
                "storeName" to storeName,
                "managerName" to managerName,
                "userId" to uid
            )
        ).await()
    }

    suspend fun updateManagerPin(newPin: String, storeId: String = "default_store") {
        val uid = currentUserId
        if (uid == null) {
            localStoreProfile.value = localStoreProfile.value.copy(managerPin = newPin)
            return
        }
        storeDoc(uid, storeId).update(
            mapOf(
                "managerPin" to newPin,
                "userId" to uid
            )
        ).await()
    }

    suspend fun updateOperatingCost(operatingCost: OperatingCost, storeId: String = "default_store") {
        val uid = currentUserId
        if (uid == null) {
            localStoreProfile.value = localStoreProfile.value.copy(operatingCost = operatingCost)
            return
        }
        storeDoc(uid, storeId).update(
            mapOf(
                "operatingCost" to operatingCost,
                "userId" to uid
            )
        ).await()
    }

    // --- INVENTARIS & STOK ---
    fun observeInventory(storeId: String = "default_store"): Flow<List<InventoryItem>> {
        val uid = currentUserId ?: return localInventory
        return callbackFlow {
            val col = db.collection("users").document(uid).collection("stores").document(storeId).collection("inventory")
            val registration = col.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WarkopRepo", "Error observing inventory", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(InventoryItem::class.java) ?: emptyList()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    // --- RIWAYAT PEMBELIAN & PENYESUAIAN STOK ---
    fun observeStockLogs(storeId: String = "default_store"): Flow<List<StockLog>> {
        val uid = currentUserId ?: return localStockLogs
        return callbackFlow {
            val col = db.collection("users").document(uid).collection("stores").document(storeId)
                .collection("stock_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
            val registration = col.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WarkopRepo", "Error observing stock logs", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(StockLog::class.java) ?: emptyList()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    suspend fun setInventoryStock(
        itemId: String,
        newStock: Double,
        authorizedBy: String,
        reason: String,
        storeId: String = "default_store"
    ) {
        val uid = currentUserId
        if (uid == null) {
            val existing = localInventory.value.find { it.id == itemId }
            localInventory.value = localInventory.value.map {
                if (it.id == itemId) it.copy(stock = newStock) else it
            }
            if (existing != null) {
                val delta = newStock - existing.stock
                if (delta != 0.0) {
                    val log = StockLog(
                        id = "log_${UUID.randomUUID().toString().take(8)}",
                        userId = "local_preview",
                        timestamp = System.currentTimeMillis(),
                        ingredientName = existing.name,
                        delta = delta,
                        costImpact = delta * existing.costPerUnit,
                        authorizedBy = authorizedBy,
                        reason = reason
                    )
                    localStockLogs.value = listOf(log) + localStockLogs.value
                }
            }
            return
        }
        val doc = db.collection("users").document(uid).collection("stores").document(storeId)
            .collection("inventory").document(itemId)

        val current = doc.get().await().toObject(InventoryItem::class.java) ?: return
        val delta = newStock - current.stock
        val costImpact = delta * current.costPerUnit

        doc.update("stock", newStock).await()

        if (delta != 0.0) {
            val logRef = db.collection("users").document(uid).collection("stores").document(storeId)
                .collection("stock_logs").document()
            val log = StockLog(
                id = logRef.id,
                userId = uid,
                timestamp = System.currentTimeMillis(),
                ingredientName = current.name,
                delta = delta,
                costImpact = costImpact,
                authorizedBy = authorizedBy,
                reason = reason
            )
            logRef.set(log).await()
        }
    }

    suspend fun addInventoryItem(
        item: InventoryItem,
        authorizedBy: String = "Manager",
        storeId: String = "default_store"
    ) {
        val uid = currentUserId
        if (uid == null) {
            val id = item.id.ifBlank { "ing_${UUID.randomUUID().toString().take(8)}" }
            localInventory.value = localInventory.value + item.copy(id = id, userId = "local_preview")
            if (item.stock > 0.0) {
                val log = StockLog(
                    id = "log_${UUID.randomUUID().toString().take(8)}",
                    userId = "local_preview",
                    timestamp = System.currentTimeMillis(),
                    ingredientName = item.name,
                    delta = item.stock,
                    costImpact = item.stock * item.costPerUnit,
                    authorizedBy = authorizedBy,
                    reason = "Pembelian bahan baru (${item.stock.toInt()} ${item.unit})"
                )
                localStockLogs.value = listOf(log) + localStockLogs.value
            }
            return
        }
        val col = db.collection("users").document(uid).collection("stores").document(storeId).collection("inventory")
        val doc = if (item.id.isNotBlank()) col.document(item.id) else col.document()
        doc.set(item.copy(id = doc.id, userId = uid)).await()

        if (item.stock > 0.0) {
            val logRef = db.collection("users").document(uid).collection("stores").document(storeId)
                .collection("stock_logs").document()
            val log = StockLog(
                id = logRef.id,
                userId = uid,
                timestamp = System.currentTimeMillis(),
                ingredientName = item.name,
                delta = item.stock,
                costImpact = item.stock * item.costPerUnit,
                authorizedBy = authorizedBy,
                reason = "Pembelian bahan baru (${item.stock.toInt()} ${item.unit})"
            )
            logRef.set(log).await()
        }
    }

    suspend fun deleteInventoryItem(itemId: String, storeId: String = "default_store") {
        val uid = currentUserId
        if (uid == null) {
            localInventory.value = localInventory.value.filterNot { it.id == itemId }
            return
        }
        db.collection("users").document(uid).collection("stores").document(storeId)
            .collection("inventory").document(itemId).delete().await()
    }

    // --- MENU & RESEP (BOM) ---
    fun observeMenuItems(storeId: String = "default_store"): Flow<List<MenuItem>> {
        val uid = currentUserId ?: return localMenu
        return callbackFlow {
            val col = db.collection("users").document(uid).collection("stores").document(storeId).collection("menu")
            val registration = col.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WarkopRepo", "Error observing menu", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(MenuItem::class.java) ?: emptyList()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    suspend fun saveMenuItem(item: MenuItem, storeId: String = "default_store") {
        val uid = currentUserId
        if (uid == null) {
            val id = item.id.ifBlank { "menu_${UUID.randomUUID().toString().take(8)}" }
            val updatedItem = item.copy(id = id, userId = "local_preview")
            val exists = localMenu.value.any { it.id == id }
            localMenu.value = if (exists) {
                localMenu.value.map { if (it.id == id) updatedItem else it }
            } else {
                localMenu.value + updatedItem
            }
            return
        }
        val col = db.collection("users").document(uid).collection("stores").document(storeId).collection("menu")
        val doc = if (item.id.isNotBlank()) col.document(item.id) else col.document()
        doc.set(item.copy(id = doc.id, userId = uid)).await()
    }

    suspend fun deleteMenuItem(menuId: String, storeId: String = "default_store") {
        val uid = currentUserId
        if (uid == null) {
            localMenu.value = localMenu.value.filterNot { it.id == menuId }
            return
        }
        db.collection("users").document(uid).collection("stores").document(storeId)
            .collection("menu").document(menuId).delete().await()
    }

    // --- TRANSAKSI & POS ---
    fun observeTransactions(storeId: String = "default_store"): Flow<List<Transaction>> {
        val uid = currentUserId ?: return localTransactions
        return callbackFlow {
            val col = db.collection("users").document(uid).collection("stores").document(storeId)
                .collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)

            val registration = col.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WarkopRepo", "Error observing transactions", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(Transaction::class.java) ?: emptyList()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    suspend fun processTransaction(
        items: List<CartItem>,
        discount: Double,
        paymentMethod: String,
        cashierName: String,
        cashierShift: String,
        customerName: String,
        note: String,
        menuList: List<MenuItem>,
        inventoryList: List<InventoryItem>,
        storeId: String = "default_store"
    ): Result<Transaction> {
        val uid = currentUserId
        val subtotal = items.sumOf { it.price * it.quantity }
        val total = (subtotal - discount).coerceAtLeast(0.0)

        // 1. Calculate and verify stock deduction
        val deductions = mutableMapOf<String, Double>()
        items.forEach { cartItem ->
            val menu = menuList.find { it.id == cartItem.menuId || it.name == cartItem.name }
            menu?.recipe?.forEach { recipeItem ->
                deductions[recipeItem.ingredientId] =
                    (deductions[recipeItem.ingredientId] ?: 0.0) + (recipeItem.quantity * cartItem.quantity)
            }
        }

        // Verify sufficient stock
        for ((ingId, needed) in deductions) {
            val ing = inventoryList.find { it.id == ingId }
            if (ing != null && ing.stock < needed) {
                return Result.failure(IllegalStateException("Stok bahan '${ing.name}' tidak mencukupi (sisa ${ing.stock} ${ing.unit}, butuh $needed ${ing.unit})"))
            }
        }

        val isDebt = paymentMethod == "Utang/Kasbon" || paymentMethod.contains("Utang", ignoreCase = true)

        if (uid == null) {
            // Local mode execution
            localInventory.value = localInventory.value.map { ing ->
                val needed = deductions[ing.id] ?: 0.0
                if (needed > 0.0) ing.copy(stock = (ing.stock - needed).coerceAtLeast(0.0)) else ing
            }
            val txId = "trx_${UUID.randomUUID().toString().take(8)}"
            val transaction = Transaction(
                id = txId,
                userId = "local_preview",
                timestamp = System.currentTimeMillis(),
                items = items,
                subtotal = subtotal,
                discount = discount,
                total = total,
                paymentMethod = paymentMethod,
                cashierName = cashierName,
                cashierShift = cashierShift,
                customerName = customerName,
                note = note,
                status = if (isDebt) "Belum Lunas - Kasbon" else "Lunas"
            )
            localTransactions.value = listOf(transaction) + localTransactions.value

            if (isDebt && customerName.isNotBlank()) {
                val newEntry = DebtRecord(
                    transactionId = txId,
                    timestamp = System.currentTimeMillis(),
                    amount = total,
                    note = note.ifBlank { "Pesanan kasir: ${items.joinToString { "${it.quantity}x ${it.name}" }}" }
                )
                val existing = localDebts.value.find { it.customerName.equals(customerName, ignoreCase = true) }
                localDebts.value = if (existing != null) {
                    localDebts.value.map {
                        if (it.id == existing.id) {
                            it.copy(
                                totalDebt = it.totalDebt + total,
                                remainingDebt = it.remainingDebt + total,
                                status = "Belum Lunas",
                                entries = it.entries + newEntry
                            )
                        } else it
                    }
                } else {
                    localDebts.value + DebtAccount(
                        id = "debt_${UUID.randomUUID().toString().take(8)}",
                        userId = "local_preview",
                        customerName = customerName,
                        totalDebt = total,
                        remainingDebt = total,
                        status = "Belum Lunas",
                        entries = listOf(newEntry),
                        payments = emptyList()
                    )
                }
            }

            val notifTitle = if (isDebt) "Utang/Kasbon Tercatat" else "Transaksi Berhasil"
            val notifBody = if (isDebt) {
                "Pelanggan $customerName mencatat utang Rp ${total.toInt()} via $cashierName"
            } else {
                "Total Rp ${total.toInt()} ($paymentMethod) - ${items.size} jenis menu"
            }
            NotificationHelper.showNotification(context, notifTitle, notifBody)
            return Result.success(transaction)
        }

        // 2. Perform stock deductions in Firestore
        val invCol = db.collection("users").document(uid).collection("stores").document(storeId).collection("inventory")
        for ((ingId, needed) in deductions) {
            val ing = inventoryList.find { it.id == ingId }
            if (ing != null) {
                val newStock = (ing.stock - needed).coerceAtLeast(0.0)
                invCol.document(ingId).update("stock", newStock).await()
            }
        }

        // 3. Create transaction record
        val txCol = db.collection("users").document(uid).collection("stores").document(storeId).collection("transactions")
        val txDoc = txCol.document()
        val transaction = Transaction(
            id = txDoc.id,
            userId = uid,
            timestamp = System.currentTimeMillis(),
            items = items,
            subtotal = subtotal,
            discount = discount,
            total = total,
            paymentMethod = paymentMethod,
            cashierName = cashierName,
            cashierShift = cashierShift,
            customerName = customerName,
            note = note,
            status = if (isDebt) "Belum Lunas - Kasbon" else "Lunas"
        )
        txDoc.set(transaction).await()

        // 4. If Utang/Kasbon, record to DebtAccount
        if (isDebt && customerName.isNotBlank()) {
            recordDebtEntry(
                uid = uid,
                customerName = customerName,
                transactionId = txDoc.id,
                amount = total,
                note = note.ifBlank { "Pesanan kasir: ${items.joinToString { "${it.quantity}x ${it.name}" }}" },
                storeId = storeId
            )
        }

        // 5. Trigger local push notification
        val notifTitle = if (isDebt) "Utang/Kasbon Tercatat" else "Transaksi Berhasil"
        val notifBody = if (isDebt) {
            "Pelanggan $customerName mencatat utang Rp ${total.toInt()} via $cashierName"
        } else {
            "Total Rp ${total.toInt()} ($paymentMethod) - ${items.size} jenis menu"
        }
        NotificationHelper.showNotification(context, notifTitle, notifBody)

        return Result.success(transaction)
    }

    suspend fun voidTransaction(
        transaction: Transaction,
        menuList: List<MenuItem>,
        inventoryList: List<InventoryItem>,
        storeId: String = "default_store"
    ): Result<Unit> {
        val uid = currentUserId
        if (uid == null) {
            val restorations = mutableMapOf<String, Double>()
            transaction.items.forEach { cartItem ->
                val menu = menuList.find { it.id == cartItem.menuId || it.name == cartItem.name }
                menu?.recipe?.forEach { recipeItem ->
                    restorations[recipeItem.ingredientId] =
                        (restorations[recipeItem.ingredientId] ?: 0.0) + (recipeItem.quantity * cartItem.quantity)
                }
            }
            localInventory.value = localInventory.value.map { ing ->
                val addBack = restorations[ing.id] ?: 0.0
                if (addBack > 0.0) ing.copy(stock = ing.stock + addBack) else ing
            }
            localTransactions.value = localTransactions.value.filterNot { it.id == transaction.id }

            NotificationHelper.showNotification(
                context,
                "Pembatalan Transaksi",
                "Transaksi #${transaction.id.takeLast(6)} berhasil dibatalkan dan stok dikembalikan."
            )
            return Result.success(Unit)
        }

        // Return stock
        val invCol = db.collection("users").document(uid).collection("stores").document(storeId).collection("inventory")
        transaction.items.forEach { cartItem ->
            val menu = menuList.find { it.id == cartItem.menuId || it.name == cartItem.name }
            menu?.recipe?.forEach { recipeItem ->
                val ing = inventoryList.find { it.id == recipeItem.ingredientId }
                if (ing != null) {
                    val restored = ing.stock + (recipeItem.quantity * cartItem.quantity)
                    invCol.document(ing.id).update("stock", restored).await()
                }
            }
        }

        db.collection("users").document(uid).collection("stores").document(storeId)
            .collection("transactions").document(transaction.id).delete().await()

        NotificationHelper.showNotification(
            context,
            "Pembatalan Transaksi",
            "Transaksi #${transaction.id.takeLast(6)} berhasil dibatalkan dan stok dikembalikan."
        )

        return Result.success(Unit)
    }

    // --- BUKU KASBON & UTANG PELANGGAN ---
    fun observeDebts(storeId: String = "default_store"): Flow<List<DebtAccount>> {
        val uid = currentUserId ?: return localDebts
        return callbackFlow {
            val col = db.collection("users").document(uid).collection("stores").document(storeId).collection("debts")
            val registration = col.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WarkopRepo", "Error observing debts", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(DebtAccount::class.java) ?: emptyList()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    private suspend fun recordDebtEntry(
        uid: String,
        customerName: String,
        transactionId: String,
        amount: Double,
        note: String,
        storeId: String
    ) {
        val debtsCol = db.collection("users").document(uid).collection("stores").document(storeId).collection("debts")

        val query = debtsCol.whereEqualTo("customerName", customerName).get().await()
        val existing = query.documents.firstOrNull()?.toObject(DebtAccount::class.java)

        val newEntry = DebtRecord(
            transactionId = transactionId,
            timestamp = System.currentTimeMillis(),
            amount = amount,
            note = note
        )

        if (existing != null) {
            val updatedEntries = existing.entries + newEntry
            val newTotal = existing.totalDebt + amount
            val newRemaining = existing.remainingDebt + amount
            debtsCol.document(existing.id).update(
                mapOf(
                    "totalDebt" to newTotal,
                    "remainingDebt" to newRemaining,
                    "status" to "Belum Lunas",
                    "entries" to updatedEntries
                )
            ).await()
        } else {
            val newDoc = debtsCol.document()
            val account = DebtAccount(
                id = newDoc.id,
                userId = uid,
                customerName = customerName,
                totalDebt = amount,
                remainingDebt = amount,
                status = "Belum Lunas",
                entries = listOf(newEntry),
                payments = emptyList()
            )
            newDoc.set(account).await()
        }
    }

    suspend fun payDebt(
        debtId: String,
        amount: Double,
        cashierName: String,
        note: String,
        storeId: String = "default_store"
    ): Result<Unit> {
        val uid = currentUserId
        if (uid == null) {
            val existing = localDebts.value.find { it.id == debtId }
                ?: return Result.failure(IllegalArgumentException("Akun kasbon tidak ditemukan"))
            val newRemaining = (existing.remainingDebt - amount).coerceAtLeast(0.0)
            val newStatus = if (newRemaining <= 0.0) "Lunas" else "Cicilan"
            val payment = PaymentRecord(
                timestamp = System.currentTimeMillis(),
                amount = amount,
                cashierName = cashierName,
                note = note
            )
            localDebts.value = localDebts.value.map {
                if (it.id == debtId) {
                    it.copy(
                        remainingDebt = newRemaining,
                        status = newStatus,
                        payments = it.payments + payment
                    )
                } else it
            }
            NotificationHelper.showNotification(
                context,
                "Pembayaran Kasbon",
                "Kasbon ${existing.customerName} dicicil Rp ${amount.toInt()}. Sisa utang: Rp ${newRemaining.toInt()}"
            )
            return Result.success(Unit)
        }

        val doc = db.collection("users").document(uid).collection("stores").document(storeId)
            .collection("debts").document(debtId)

        val existing = doc.get().await().toObject(DebtAccount::class.java)
            ?: return Result.failure(IllegalArgumentException("Akun kasbon tidak ditemukan"))

        val newRemaining = (existing.remainingDebt - amount).coerceAtLeast(0.0)
        val newStatus = if (newRemaining <= 0.0) "Lunas" else "Cicilan"

        val payment = PaymentRecord(
            timestamp = System.currentTimeMillis(),
            amount = amount,
            cashierName = cashierName,
            note = note
        )
        val updatedPayments = existing.payments + payment

        doc.update(
            mapOf(
                "remainingDebt" to newRemaining,
                "status" to newStatus,
                "payments" to updatedPayments
            )
        ).await()

        NotificationHelper.showNotification(
            context,
            "Pembayaran Kasbon",
            "Kasbon ${existing.customerName} dicicil Rp ${amount.toInt()}. Sisa utang: Rp ${newRemaining.toInt()}"
        )

        return Result.success(Unit)
    }

    // --- KASIR SUB-AKUN ---
    fun observeCashiers(storeId: String = "default_store"): Flow<List<CashierSubAccount>> {
        val uid = currentUserId ?: return localCashiers
        return callbackFlow {
            val col = db.collection("users").document(uid).collection("stores").document(storeId).collection("cashiers")
            val registration = col.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WarkopRepo", "Error observing cashiers", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(CashierSubAccount::class.java) ?: emptyList()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    suspend fun addCashier(name: String, pin: String, shift: String = "Pagi (07:00 - 15:00)", storeId: String = "default_store") {
        val uid = currentUserId
        if (uid == null) {
            val id = "cashier_${UUID.randomUUID().toString().take(8)}"
            localCashiers.value = localCashiers.value + CashierSubAccount(
                id = id,
                userId = "local_preview",
                name = name,
                pin = pin,
                shift = shift,
                isActive = true
            )
            return
        }
        val col = db.collection("users").document(uid).collection("stores").document(storeId).collection("cashiers")
        val doc = col.document()
        val cashier = CashierSubAccount(
            id = doc.id,
            userId = uid,
            name = name,
            pin = pin,
            shift = shift,
            isActive = true
        )
        doc.set(cashier).await()
    }

    suspend fun deleteCashier(cashierId: String, storeId: String = "default_store") {
        val uid = currentUserId
        if (uid == null) {
            localCashiers.value = localCashiers.value.filterNot { it.id == cashierId }
            return
        }
        db.collection("users").document(uid).collection("stores").document(storeId)
            .collection("cashiers").document(cashierId).delete().await()
    }

    // --- SEED DATA DEFAULT DARI WARKOP EMAK JIKA BARU PERTAMA KALI ---
    suspend fun seedDefaultDataIfEmpty(storeId: String = "default_store") {
        val uid = currentUserId ?: return
        val invCol = db.collection("users").document(uid).collection("stores").document(storeId).collection("inventory")
        val existingInv = invCol.limit(1).get().await()

        if (existingInv.isEmpty) {
            val initialIngredients = listOf(
                InventoryItem(id = "ing_kopi", userId = uid, name = "Kopi", unit = "g", stock = 5000.0, costPerUnit = 200.0, minStock = 500.0),
                InventoryItem(id = "ing_susu", userId = uid, name = "Susu", unit = "ml", stock = 20000.0, costPerUnit = 18.0, minStock = 2000.0),
                InventoryItem(id = "ing_cup", userId = uid, name = "Cup", unit = "pcs", stock = 300.0, costPerUnit = 400.0, minStock = 30.0),
                InventoryItem(id = "ing_gula", userId = uid, name = "Gula Aren", unit = "ml", stock = 3000.0, costPerUnit = 30.0, minStock = 300.0),
                InventoryItem(id = "ing_teh", userId = uid, name = "Teh", unit = "g", stock = 1000.0, costPerUnit = 100.0, minStock = 100.0)
            )
            for (item in initialIngredients) {
                invCol.document(item.id).set(item).await()
            }

            val menuCol = db.collection("users").document(uid).collection("stores").document(storeId).collection("menu")
            val initialMenu = listOf(
                MenuItem(
                    id = "menu_kopisusu",
                    userId = uid,
                    name = "Kopi Susu",
                    price = 18000.0,
                    category = "Kopi",
                    recipe = listOf(
                        RecipeItem("ing_kopi", "Kopi", 18.0, "g"),
                        RecipeItem("ing_susu", "Susu", 150.0, "ml"),
                        RecipeItem("ing_cup", "Cup", 1.0, "pcs"),
                        RecipeItem("ing_gula", "Gula Aren", 15.0, "ml")
                    )
                ),
                MenuItem(
                    id = "menu_americano",
                    userId = uid,
                    name = "Americano",
                    price = 15000.0,
                    category = "Kopi",
                    recipe = listOf(
                        RecipeItem("ing_kopi", "Kopi", 18.0, "g"),
                        RecipeItem("ing_cup", "Cup", 1.0, "pcs")
                    )
                ),
                MenuItem(
                    id = "menu_esteh",
                    userId = uid,
                    name = "Es Teh",
                    price = 8000.0,
                    category = "Non-Kopi",
                    recipe = listOf(
                        RecipeItem("ing_teh", "Teh", 5.0, "g"),
                        RecipeItem("ing_cup", "Cup", 1.0, "pcs"),
                        RecipeItem("ing_gula", "Gula Aren", 15.0, "ml")
                    )
                ),
                MenuItem(
                    id = "menu_latte",
                    userId = uid,
                    name = "Latte",
                    price = 22000.0,
                    category = "Kopi",
                    recipe = listOf(
                        RecipeItem("ing_kopi", "Kopi", 18.0, "g"),
                        RecipeItem("ing_susu", "Susu", 200.0, "ml"),
                        RecipeItem("ing_cup", "Cup", 1.0, "pcs")
                    )
                )
            )
            for (m in initialMenu) {
                menuCol.document(m.id).set(m).await()
            }
        }
    }
}
