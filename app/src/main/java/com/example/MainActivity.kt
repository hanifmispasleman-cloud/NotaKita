package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.RoleLoginScreen
import com.example.ui.components.tactilePress
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SessionRole
import com.example.ui.viewmodel.WarkopViewModel
import com.example.util.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DrawerDestination(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard Manager", Icons.Default.BarChart),
    CASHIER_HOME("Beranda Kasir Piket", Icons.Default.Badge),
    REPORTS("Laporan PDF & Excel", Icons.Default.Assessment),
    POS("Kasir (POS)", Icons.Default.PointOfSale),
    INVENTORY("Stok & Resep", Icons.Default.Inventory2),
    DEBTS("Buku Kasbon / Bond", Icons.Default.ReceiptLong),
    HISTORY("Riwayat Penjualan", Icons.Default.History),
    PRICING("Rekomendasi Margin", Icons.Default.PriceCheck),
    OPERATIONAL("Biaya Operasional", Icons.Default.SettingsSuggest),
    CASHIERS("Kelola Staf Kasir", Icons.Default.People),
    PROFILE("Profil & Keamanan", Icons.Default.Storefront)
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.init(this)

        setContent {
            MyApplicationTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var showAuthScreen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (showAuthScreen && currentUser == null) {
        BackHandler { showAuthScreen = false }
        AuthScreen(
            onAuthSuccess = { _, _ ->
                currentUser = Firebase.auth.currentUser
                showAuthScreen = false
            },
            onDismiss = { showAuthScreen = false }
        )
    } else {
        val sessionKey = currentUser?.uid ?: "local_preview"
        key(sessionKey) {
            MainAppContainer(
                isSignedIn = currentUser != null,
                onRequestSignIn = { showAuthScreen = true },
                onSignOut = {
                    coroutineScope.launch {
                        withContext(NonCancellable) {
                            try {
                                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                            } catch (_: Exception) {}
                            Firebase.auth.signOut()
                            currentUser = null
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(
    isSignedIn: Boolean,
    onRequestSignIn: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: WarkopViewModel = viewModel()
) {
    val storeProfile by viewModel.storeProfile.collectAsState()
    val sessionRole by viewModel.sessionRole.collectAsState()
    val activeOperator by viewModel.activeOperator.collectAsState()
    val activeShift by viewModel.activeShift.collectAsState()
    val uiToast by viewModel.uiToast.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentScreen by remember { mutableStateOf(DrawerDestination.DASHBOARD) }

    // Show Snackbar when ViewModel emits toast
    LaunchedEffect(uiToast) {
        uiToast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    // Reset starting screen cleanly when switching between Manager & Kasir Lapangan
    LaunchedEffect(sessionRole) {
        when (sessionRole) {
            is SessionRole.Manager -> currentScreen = DrawerDestination.DASHBOARD
            is SessionRole.Cashier -> currentScreen = DrawerDestination.CASHIER_HOME
            SessionRole.Locked -> Unit
        }
    }

    if (sessionRole is SessionRole.Locked) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            RoleLoginScreen(
                viewModel = viewModel,
                isCloudSignedIn = isSignedIn,
                onRequestCloudAuth = onRequestSignIn,
                modifier = Modifier.padding(innerPadding)
            )
        }
        return
    }

    val isManager = sessionRole is SessionRole.Manager
    val homeDestination = if (isManager) DrawerDestination.DASHBOARD else DrawerDestination.CASHIER_HOME
    val allowedDestinations = remember(isManager) {
        if (isManager) {
            listOf(
                DrawerDestination.DASHBOARD,
                DrawerDestination.REPORTS,
                DrawerDestination.POS,
                DrawerDestination.INVENTORY,
                DrawerDestination.DEBTS,
                DrawerDestination.HISTORY,
                DrawerDestination.PRICING,
                DrawerDestination.OPERATIONAL,
                DrawerDestination.CASHIERS,
                DrawerDestination.PROFILE
            )
        } else {
            listOf(
                DrawerDestination.CASHIER_HOME,
                DrawerDestination.POS,
                DrawerDestination.DEBTS,
                DrawerDestination.INVENTORY,
                DrawerDestination.HISTORY
            )
        }
    }

    // Android Hardware Back Handler
    BackHandler {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (currentScreen != homeDestination) {
            currentScreen = homeDestination
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxHeight()
            ) {
                // DRAWER HEADER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isManager) MaterialTheme.colorScheme.primary else AmberCoffee)
                        .statusBarsPadding()
                        .padding(20.dp)
                ) {
                    Column {
                        Icon(
                            imageVector = if (isManager) Icons.Default.AdminPanelSettings else Icons.Default.Badge,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = storeProfile.storeName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = if (isManager) {
                                "Manager: $activeOperator"
                            } else {
                                "Kasir: $activeOperator · $activeShift"
                            },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = if (isManager) "AKSES MANAGER" else "KASIR LAPANGAN",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (storeProfile.isOpen) MintLight else WarningRedLight
                            ) {
                                Text(
                                    text = if (storeProfile.isOpen) "Toko Buka" else "Toko Tutup",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (storeProfile.isOpen) ForestGreenDark else Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // DRAWER ITEMS (Role-separated)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    allowedDestinations.forEach { dest ->
                        NavigationDrawerItem(
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = {
                                Text(
                                    dest.label,
                                    fontWeight = if (currentScreen == dest) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selected = currentScreen == dest,
                            onClick = {
                                currentScreen = dest
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .tactilePress()
                                .testTag("drawer_item_${dest.name}")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.SwapHoriz, null, tint = AmberCoffee) },
                        label = {
                            Text(
                                "Ganti Akun / Kunci Layar",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                viewModel.logoutSession()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.tactilePress().testTag("drawer_switch_role")
                    )

                    if (isManager) {
                        if (isSignedIn) {
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Logout, null, tint = WarningRed) },
                                label = { Text("Keluar Cloud Google", color = WarningRed, fontWeight = FontWeight.SemiBold) },
                                selected = false,
                                onClick = {
                                    coroutineScope.launch {
                                        drawerState.close()
                                        onSignOut()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.tactilePress().testTag("drawer_logout")
                            )
                        } else {
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.CloudSync, null, tint = MaterialTheme.colorScheme.primary) },
                                label = {
                                    Text(
                                        "Hubungkan Cloud Google",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                selected = false,
                                onClick = {
                                    coroutineScope.launch {
                                        drawerState.close()
                                        onRequestSignIn()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.tactilePress().testTag("drawer_login")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentScreen.label,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isManager) {
                                    "${storeProfile.storeName} · Manager (${if (storeProfile.isOpen) "Buka" else "Tutup"})"
                                } else {
                                    "${storeProfile.storeName} · Kasir: $activeOperator"
                                },
                                fontSize = 11.sp,
                                color = if (storeProfile.isOpen) ForestGreenLight else WarningRed
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch { drawerState.open() }
                            },
                            modifier = Modifier.testTag("btn_open_drawer")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu Laci Navigasi")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (isManager) {
                                    currentScreen = DrawerDestination.PROFILE
                                } else {
                                    viewModel.logoutSession()
                                }
                            },
                            modifier = Modifier.testTag("btn_top_action")
                        ) {
                            Icon(
                                imageVector = if (isManager) Icons.Default.Storefront else Icons.Default.Lock,
                                contentDescription = if (isManager) "Profil Toko" else "Kunci Layar",
                                tint = if (storeProfile.isOpen) ForestGreen else WarningRed
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    DrawerDestination.DASHBOARD -> if (isManager) {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToReports = { currentScreen = DrawerDestination.REPORTS }
                        )
                    } else {
                        CashierDashboardScreen(
                            viewModel = viewModel,
                            onNavigate = { currentScreen = it },
                            onLockSwitchRole = { viewModel.logoutSession() }
                        )
                    }
                    DrawerDestination.CASHIER_HOME -> CashierDashboardScreen(
                        viewModel = viewModel,
                        onNavigate = { currentScreen = it },
                        onLockSwitchRole = { viewModel.logoutSession() }
                    )
                    DrawerDestination.REPORTS -> if (isManager) {
                        ReportsScreen(viewModel = viewModel)
                    } else {
                        CashierDashboardScreen(
                            viewModel = viewModel,
                            onNavigate = { currentScreen = it },
                            onLockSwitchRole = { viewModel.logoutSession() }
                        )
                    }
                    DrawerDestination.POS -> PosScreen(viewModel = viewModel)
                    DrawerDestination.INVENTORY -> InventoryScreen(viewModel = viewModel)
                    DrawerDestination.DEBTS -> DebtsScreen(viewModel = viewModel)
                    DrawerDestination.HISTORY -> HistoryScreen(viewModel = viewModel)
                    DrawerDestination.PRICING -> if (isManager) PricingScreen(viewModel = viewModel) else CashierDashboardScreen(
                        viewModel = viewModel,
                        onNavigate = { currentScreen = it },
                        onLockSwitchRole = { viewModel.logoutSession() }
                    )
                    DrawerDestination.OPERATIONAL -> if (isManager) OperationalScreen(viewModel = viewModel) else CashierDashboardScreen(
                        viewModel = viewModel,
                        onNavigate = { currentScreen = it },
                        onLockSwitchRole = { viewModel.logoutSession() }
                    )
                    DrawerDestination.CASHIERS -> if (isManager) CashiersScreen(viewModel = viewModel) else CashierDashboardScreen(
                        viewModel = viewModel,
                        onNavigate = { currentScreen = it },
                        onLockSwitchRole = { viewModel.logoutSession() }
                    )
                    DrawerDestination.PROFILE -> if (isManager) ProfileScreen(
                        viewModel = viewModel,
                        isSignedIn = isSignedIn,
                        onRequestSignIn = onRequestSignIn,
                        onSignOut = onSignOut
                    ) else CashierDashboardScreen(
                        viewModel = viewModel,
                        onNavigate = { currentScreen = it },
                        onLockSwitchRole = { viewModel.logoutSession() }
                    )
                }
            }
        }
    }
}
