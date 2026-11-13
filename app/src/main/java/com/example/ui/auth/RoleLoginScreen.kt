package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CashierSubAccount
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel
import com.example.util.BiometricHelper

@Composable
fun RoleLoginScreen(
    viewModel: WarkopViewModel,
    isCloudSignedIn: Boolean,
    onRequestCloudAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storeProfile by viewModel.storeProfile.collectAsState()
    val cashierList by viewModel.cashierList.collectAsState()

    // 0 = Manager (Akun Utama), 1 = Kasir Lapangan (Sub-Akun)
    var selectedRoleTab by remember { mutableStateOf(0) }
    var managerPinInput by remember { mutableStateOf("") }
    var cashierPinInput by remember { mutableStateOf("") }
    var selectedCashier by remember(cashierList) {
        mutableStateOf(cashierList.firstOrNull { it.isActive })
    }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cashierList) {
        if (selectedCashier == null || cashierList.none { it.id == selectedCashier?.id }) {
            selectedCashier = cashierList.firstOrNull { it.isActive }
        }
    }

    val biometricAvailability = remember(context) {
        BiometricHelper.checkAvailability(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Coffee,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = storeProfile.storeName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Gerbang Keamanan Akses · Manager & Kasir Lapangan",
                    fontSize = 12.sp,
                    color = MutedGreyText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )

                // Role Switcher Tabs
                TabRow(
                    selectedTabIndex = selectedRoleTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedRoleTab == 0,
                        onClick = {
                            selectedRoleTab = 0
                            localError = null
                        },
                        text = {
                            Text(
                                "Manager",
                                fontWeight = if (selectedRoleTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.testTag("tab_role_manager")
                    )
                    Tab(
                        selected = selectedRoleTab == 1,
                        onClick = {
                            selectedRoleTab = 1
                            localError = null
                        },
                        text = {
                            Text(
                                "Kasir Lapangan",
                                fontWeight = if (selectedRoleTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.testTag("tab_role_cashier")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (selectedRoleTab == 0) {
                    // --- LOGIN MANAGER ---
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Akun Utama: ${storeProfile.managerName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Akses penuh Dashboard Finansial, Laporan PDF & Excel Akuntansi, Margin Harga, Biaya Operasional, dan pembuatan akun Kasir Lapangan.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = managerPinInput,
                        onValueChange = {
                            managerPinInput = it
                            localError = null
                        },
                        label = { Text("PIN Keamanan Manager") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_login_manager_pin")
                    )

                    if (storeProfile.managerPin == "1234") {
                        Text(
                            text = "PIN default awal Manager: 1234 (dapat diubah di Profil & Keamanan)",
                            fontSize = 11.sp,
                            color = MutedGreyText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (managerPinInput.isBlank()) {
                                localError = "Masukkan PIN Manager atau gunakan sidik jari"
                            } else if (!viewModel.loginManagerWithPin(managerPinInput)) {
                                localError = "PIN Manager salah. Silakan coba lagi."
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .tactilePress()
                            .testTag("btn_login_manager_pin")
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Masuk Dashboard Manager", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            BiometricHelper.authenticate(
                                context = context,
                                title = "Login Manager - ${storeProfile.storeName}",
                                subtitle = "Verifikasi sidik jari untuk membuka Dashboard Manager",
                                onSuccess = { viewModel.loginManagerWithBiometric() },
                                onError = { msg -> localError = msg }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .tactilePress()
                            .testTag("btn_login_manager_biometric")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (biometricAvailability.isAvailable) {
                                "Masuk dengan Sidik Jari"
                            } else {
                                "Sidik Jari (Cek Perangkat)"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    // --- LOGIN KASIR LAPANGAN ---
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = AmberCoffeeContainer.copy(alpha = 0.55f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = Color(0xFF7A4400),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sub-Akun Kasir Lapangan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF5E3500)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Dashboard khusus operasional shift piket: Mesin Kasir (POS), Buku Kasbon, dan Cek Stok. Akun kasir dibuat oleh Manager.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF5E3500),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val activeCashiers = cashierList.filter { it.isActive }
                    if (activeCashiers.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Belum ada akun kasir lapangan terdaftar. Masuk sebagai Manager untuk membuat akun kasir baru.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Pilih Akun Kasir & Waktu Piket:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            activeCashiers.forEach { cashier: CashierSubAccount ->
                                val isChosen = selectedCashier?.id == cashier.id
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChosen) MintLight else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (isChosen) 1.5.dp else 0.dp,
                                            color = if (isChosen) ForestGreen else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .tactilePress {
                                            selectedCashier = cashier
                                            localError = null
                                        }
                                        .testTag("select_cashier_${cashier.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = isChosen,
                                                onClick = {
                                                    selectedCashier = cashier
                                                    localError = null
                                                }
                                            )
                                            Column {
                                                Text(
                                                    text = cashier.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = "Shift: ${cashier.shift}",
                                                    fontSize = 11.sp,
                                                    color = MutedGreyText
                                                )
                                            }
                                        }
                                        if (isChosen) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = ForestGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = cashierPinInput,
                            onValueChange = {
                                cashierPinInput = it
                                localError = null
                            },
                            label = { Text("PIN Kasir (${selectedCashier?.name ?: ""})") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_login_cashier_pin")
                        )

                        if (activeCashiers.any { it.pin == "1111" || it.pin == "2222" }) {
                            Text(
                                text = "PIN bawaan demo: Kasir Pagi (1111) · Kasir Sore (2222)",
                                fontSize = 11.sp,
                                color = MutedGreyText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val target = selectedCashier
                                if (target == null) {
                                    localError = "Pilih akun kasir terlebih dahulu"
                                } else if (cashierPinInput.isBlank()) {
                                    localError = "Masukkan PIN kasir atau gunakan sidik jari"
                                } else if (!viewModel.loginCashierWithPin(target, cashierPinInput)) {
                                    localError = "PIN Kasir untuk ${target.name} salah."
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberCoffee),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .tactilePress()
                                .testTag("btn_login_cashier_pin")
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Masuk Dashboard Kasir", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                val target = selectedCashier
                                if (target == null) {
                                    localError = "Pilih akun kasir terlebih dahulu"
                                } else {
                                    BiometricHelper.authenticate(
                                        context = context,
                                        title = "Login Kasir - ${target.name}",
                                        subtitle = "Verifikasi sidik jari untuk memulai shift ${target.shift}",
                                        onSuccess = { viewModel.loginCashierWithBiometric(target) },
                                        onError = { msg -> localError = msg }
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .tactilePress()
                                .testTag("btn_login_cashier_biometric")
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (biometricAvailability.isAvailable) {
                                    "Masuk dengan Sidik Jari"
                                } else {
                                    "Sidik Jari (Cek Perangkat)"
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (localError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = localError!!,
                        color = WarningRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onRequestCloudAuth,
                    modifier = Modifier.testTag("btn_cloud_sync_gate")
                ) {
                    Icon(
                        imageVector = if (isCloudSignedIn) Icons.Default.CloudDone else Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCloudSignedIn) {
                            "Sinkronisasi Cloud Firebase Aktif"
                        } else {
                            "Hubungkan Akun Google (Sinkronisasi Cloud)"
                        },
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
