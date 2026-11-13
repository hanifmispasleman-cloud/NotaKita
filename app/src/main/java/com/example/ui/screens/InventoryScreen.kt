package com.example.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InventoryItem
import com.example.data.model.MenuItem
import com.example.data.model.RecipeItem
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel
import com.example.util.BiometricHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: WarkopViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storeProfile by viewModel.storeProfile.collectAsState()
    val inventoryList by viewModel.inventoryList.collectAsState()
    val menuList by viewModel.menuList.collectAsState()
    val activeOperator by viewModel.activeOperator.collectAsState()

    // Dialog state for Manager Authorization
    var showAuthDialogForStock by remember { mutableStateOf<InventoryItem?>(null) }
    var newStockInput by remember { mutableStateOf("") }
    var authPinInput by remember { mutableStateOf("") }
    var authReasonInput by remember { mutableStateOf("Penyesuaian stok fisik") }

    // Dialog state for adding new ingredient
    var showAddIngredientDialog by remember { mutableStateOf(false) }
    var newIngName by remember { mutableStateOf("") }
    var newIngUnit by remember { mutableStateOf("g") }
    var newIngStock by remember { mutableStateOf("") }
    var newIngCost by remember { mutableStateOf("") }
    var newIngMin by remember { mutableStateOf("") }
    var addIngPin by remember { mutableStateOf("") }

    // Dialog state for adding new menu
    var showAddMenuDialog by remember { mutableStateOf(false) }
    var newMenuName by remember { mutableStateOf("") }
    var newMenuPrice by remember { mutableStateOf("") }
    var newMenuCategory by remember { mutableStateOf("Kopi") }
    val selectedRecipes = remember { mutableStateListOf<RecipeItem>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Keamanan Otorisasi Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Sistem Keamanan Otorisasi Stok",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Penambahan atau koreksi stok memerlukan verifikasi PIN Manager (${storeProfile.managerName}). Petugas piket: $activeOperator",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // TABEL BAHAN BAKU MASTER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bahan Baku (Inventaris Master)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { showAddIngredientDialog = true },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.tactilePress().testTag("btn_add_ingredient")
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tambah Bahan", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (inventoryList.isEmpty()) {
                    Text(
                        text = "Belum ada bahan baku terdaftar.",
                        fontSize = 13.sp,
                        color = MutedGreyText,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    inventoryList.forEach { item ->
                        val isCritical = item.minStock > 0 && item.stock <= item.minStock
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (isCritical) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "KRITIS",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningRed,
                                            modifier = Modifier
                                                .background(Color(0xFFFDE8E4), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Stok: ${item.stock.toInt()} ${item.unit} · Biaya: Rp ${viewModel.formatRupiah(item.costPerUnit)}/${item.unit} · Min: ${item.minStock.toInt()}",
                                    fontSize = 12.sp,
                                    color = if (isCritical) WarningRed else MutedGreyText
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        showAuthDialogForStock = item
                                        newStockInput = item.stock.toInt().toString()
                                        authPinInput = ""
                                        authReasonInput = "Koreksi stok fisik"
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.tactilePress()
                                ) {
                                    Text("Ubah", fontSize = 11.sp)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // TABEL MENU & RESEP (BILL OF MATERIALS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Menu & Formula Resep (BOM)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    showAddMenuDialog = true
                    newMenuName = ""
                    newMenuPrice = ""
                    newMenuCategory = "Kopi"
                    selectedRecipes.clear()
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.tactilePress().testTag("btn_add_menu")
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tambah Menu", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (menuList.isEmpty()) {
                    Text(
                        text = "Belum ada menu yang dibuat.",
                        fontSize = 13.sp,
                        color = MutedGreyText,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    menuList.forEach { menu ->
                        val hppBahan = menu.recipe.sumOf { r ->
                            val ing = inventoryList.find { it.id == r.ingredientId }
                            (ing?.costPerUnit ?: 0.0) * r.quantity
                        }
                        val opPerUnit = storeProfile.operatingCost.perUnitCost()
                        val hppTotal = hppBahan + (if (storeProfile.isOpen) opPerUnit else 0.0)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = menu.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Harga: Rp ${viewModel.formatRupiah(menu.price)} · HPP Bahan: Rp ${viewModel.formatRupiah(hppBahan)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Resep: ${menu.recipe.joinToString { "${it.quantity.toInt()} ${it.unit} ${it.ingredientName}" }}",
                                    fontSize = 11.sp,
                                    color = MutedGreyText
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteMenuItem(menu.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Hapus Menu",
                                    tint = WarningRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // DIALOG OTORISASI PERUBAHAN STOK OLEH MANAGER
    if (showAuthDialogForStock != null) {
        val target = showAuthDialogForStock!!
        AlertDialog(
            onDismissRequest = { showAuthDialogForStock = null },
            title = {
                Text(
                    text = "Otorisasi Perubahan Stok: ${target.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Stok saat ini: ${target.stock.toInt()} ${target.unit}",
                        fontSize = 13.sp,
                        color = MutedGreyText
                    )
                    OutlinedTextField(
                        value = newStockInput,
                        onValueChange = { newStockInput = it },
                        label = { Text("Jumlah Stok Baru (${target.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = authReasonInput,
                        onValueChange = { authReasonInput = it },
                        label = { Text("Alasan Perubahan (Restock / Rusak / Opname)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = authPinInput,
                        onValueChange = { authPinInput = it },
                        label = { Text("PIN Verifikasi Manager") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_auth_pin")
                    )

                    OutlinedButton(
                        onClick = {
                            val stockVal = newStockInput.toDoubleOrNull() ?: target.stock
                            BiometricHelper.authenticate(
                                context = context,
                                title = "Otorisasi Stok Manager",
                                subtitle = "Verifikasi sidik jari untuk mengubah stok ${target.name}",
                                onSuccess = {
                                    viewModel.setInventoryStockWithAuth(
                                        itemId = target.id,
                                        newStock = stockVal,
                                        managerPin = "",
                                        reason = authReasonInput,
                                        useBiometric = true
                                    )
                                    showAuthDialogForStock = null
                                },
                                onError = { err -> viewModel.showToast(err) }
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp).tactilePress().testTag("btn_stock_fingerprint")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Otorisasi Sidik Jari", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val stockVal = newStockInput.toDoubleOrNull() ?: target.stock
                        val ok = viewModel.setInventoryStockWithAuth(
                            itemId = target.id,
                            newStock = stockVal,
                            managerPin = authPinInput,
                            reason = authReasonInput,
                            useBiometric = false
                        )
                        if (ok) showAuthDialogForStock = null
                    },
                    modifier = Modifier.tactilePress()
                ) {
                    Text("Verifikasi PIN & Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthDialogForStock = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG TAMBAH BAHAN BAKU
    if (showAddIngredientDialog) {
        AlertDialog(
            onDismissRequest = { showAddIngredientDialog = false },
            title = { Text("Tambah Bahan Baku Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newIngName,
                        onValueChange = { newIngName = it },
                        label = { Text("Nama Bahan (mis. Kopi, Susu, Gula)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newIngUnit,
                            onValueChange = { newIngUnit = it },
                            label = { Text("Satuan (g/ml/pcs)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newIngStock,
                            onValueChange = { newIngStock = it },
                            label = { Text("Stok Awal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newIngCost,
                            onValueChange = { newIngCost = it },
                            label = { Text("Harga/Satuan (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newIngMin,
                            onValueChange = { newIngMin = it },
                            label = { Text("Batas Kritis") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = addIngPin,
                        onValueChange = { addIngPin = it },
                        label = { Text("PIN Manager untuk Tambah") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            val stock = newIngStock.toDoubleOrNull() ?: 0.0
                            val cost = newIngCost.toDoubleOrNull() ?: 0.0
                            val min = newIngMin.toDoubleOrNull() ?: 0.0
                            if (newIngName.isNotBlank()) {
                                BiometricHelper.authenticate(
                                    context = context,
                                    title = "Otorisasi Tambah Bahan",
                                    subtitle = "Verifikasi sidik jari untuk menambah ${newIngName.trim()}",
                                    onSuccess = {
                                        viewModel.addInventoryItem(
                                            InventoryItem(
                                                name = newIngName.trim(),
                                                unit = newIngUnit.trim(),
                                                stock = stock,
                                                costPerUnit = cost,
                                                minStock = min
                                            ),
                                            managerPin = "",
                                            useBiometric = true
                                        )
                                        showAddIngredientDialog = false
                                        newIngName = ""
                                        newIngStock = ""
                                        newIngCost = ""
                                        addIngPin = ""
                                    },
                                    onError = { err -> viewModel.showToast(err) }
                                )
                            } else {
                                viewModel.showToast("Isi nama bahan terlebih dahulu")
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp).tactilePress()
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Otorisasi Sidik Jari", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val stock = newIngStock.toDoubleOrNull() ?: 0.0
                        val cost = newIngCost.toDoubleOrNull() ?: 0.0
                        val min = newIngMin.toDoubleOrNull() ?: 0.0
                        if (newIngName.isNotBlank()) {
                            val ok = viewModel.addInventoryItem(
                                InventoryItem(
                                    name = newIngName.trim(),
                                    unit = newIngUnit.trim(),
                                    stock = stock,
                                    costPerUnit = cost,
                                    minStock = min
                                ),
                                managerPin = addIngPin,
                                useBiometric = false
                            )
                            if (ok) {
                                showAddIngredientDialog = false
                                newIngName = ""
                                newIngStock = ""
                                newIngCost = ""
                                addIngPin = ""
                            }
                        }
                    },
                    modifier = Modifier.tactilePress()
                ) {
                    Text("Tambah")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddIngredientDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG TAMBAH MENU DENGAN FORMULA RESEP
    if (showAddMenuDialog) {
        var selectedIngId by remember { mutableStateOf(inventoryList.firstOrNull()?.id ?: "") }
        var selectedIngQty by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddMenuDialog = false },
            title = { Text("Tambah Menu & Resep (BOM)", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newMenuName,
                        onValueChange = { newMenuName = it },
                        label = { Text("Nama Menu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newMenuPrice,
                            onValueChange = { newMenuPrice = it },
                            label = { Text("Harga Jual (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newMenuCategory,
                            onValueChange = { newMenuCategory = it },
                            label = { Text("Kategori") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Bahan Resep per Porsi:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))

                    selectedRecipes.forEach { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${r.ingredientName}: ${r.quantity} ${r.unit}", fontSize = 12.sp)
                            IconButton(onClick = { selectedRecipes.remove(r) }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Input Tambah Resep
                    if (inventoryList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Dropdown bahan
                            var expanded by remember { mutableStateOf(false) }
                            val activeIng = inventoryList.find { it.id == selectedIngId } ?: inventoryList.first()

                            Box(modifier = Modifier.weight(1.5f)) {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(activeIng.name, fontSize = 11.sp, maxLines = 1)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    inventoryList.forEach { ing ->
                                        DropdownMenuItem(
                                            text = { Text("${ing.name} (${ing.unit})") },
                                            onClick = {
                                                selectedIngId = ing.id
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = selectedIngQty,
                                onValueChange = { selectedIngQty = it },
                                label = { Text("Jml") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    val q = selectedIngQty.toDoubleOrNull() ?: 0.0
                                    val targetIng = inventoryList.find { it.id == selectedIngId }
                                    if (targetIng != null && q > 0) {
                                        selectedRecipes.removeAll { it.ingredientId == targetIng.id }
                                        selectedRecipes.add(
                                            RecipeItem(
                                                ingredientId = targetIng.id,
                                                ingredientName = targetIng.name,
                                                quantity = q,
                                                unit = targetIng.unit
                                            )
                                        )
                                        selectedIngQty = ""
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.tactilePress()
                            ) {
                                Text("+", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = newMenuPrice.toDoubleOrNull() ?: 0.0
                        if (newMenuName.isNotBlank() && p > 0 && selectedRecipes.isNotEmpty()) {
                            viewModel.saveMenuItem(
                                MenuItem(
                                    name = newMenuName.trim(),
                                    price = p,
                                    category = newMenuCategory.trim(),
                                    recipe = selectedRecipes.toList()
                                )
                            )
                            showAddMenuDialog = false
                        }
                    },
                    modifier = Modifier.tactilePress()
                ) {
                    Text("Simpan Menu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMenuDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
