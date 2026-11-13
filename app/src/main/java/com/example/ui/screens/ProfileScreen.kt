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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun ProfileScreen(
    viewModel: WarkopViewModel,
    isSignedIn: Boolean,
    onRequestSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storeProfile by viewModel.storeProfile.collectAsState()
    val authUser = remember(isSignedIn) { Firebase.auth.currentUser }

    var storeNameInput by remember(storeProfile) { mutableStateOf(storeProfile.storeName) }
    var managerNameInput by remember(storeProfile) { mutableStateOf(storeProfile.managerName) }
    var pinInput by remember(storeProfile) { mutableStateOf(storeProfile.managerPin) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Pengaturan Profil & Keamanan Warkop",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        // STATUS BUKA / TUTUP TOKO
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (storeProfile.isOpen) MintLight else Color(0xFFFDE8E4)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (storeProfile.isOpen) "Status: Toko Sedang Buka" else "Status: Toko Sedang Tutup",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (storeProfile.isOpen) ForestGreenDark else WarningRed
                        )
                        Text(
                            text = if (storeProfile.isOpen)
                                "Beban biaya operasional dialokasikan normal ke laporan laba rugi."
                            else
                                "Penundaan biaya operasional aktif. Biaya harian tidak dibebankan.",
                            fontSize = 12.sp,
                            color = MutedGreyText
                        )
                    }

                    Switch(
                        checked = storeProfile.isOpen,
                        onCheckedChange = { viewModel.toggleStoreOpen() },
                        modifier = Modifier.testTag("switch_store_open_profile")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PENGATURAN IDENTITAS TOKO & MANAGER
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Identitas Usaha & Pemilik (Manager)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = storeNameInput,
                    onValueChange = { storeNameInput = it },
                    label = { Text("Nama Warkop / Kedai") },
                    leadingIcon = { Icon(Icons.Default.Store, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_store_name")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = managerNameInput,
                    onValueChange = { managerNameInput = it },
                    label = { Text("Nama Manager / Pemilik Akun Utama") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_manager_name")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (storeNameInput.isNotBlank() && managerNameInput.isNotBlank()) {
                            viewModel.updateStoreNames(storeNameInput, managerNameInput)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).tactilePress().testTag("btn_save_store_names")
                ) {
                    Text("Simpan Nama Warkop & Manager")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // KEAMANAN: PIN MANAGER UNTUK OTORISASI STOK
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PIN Otorisasi Manager",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Text(
                    text = "PIN ini digunakan untuk memverifikasi penambahan atau pengubahan stok barang oleh petugas kasir yang piket.",
                    fontSize = 12.sp,
                    color = MutedGreyText,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it },
                    label = { Text("PIN Verifikasi (4-6 angka)") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_manager_pin")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (pinInput.length >= 4) {
                            viewModel.updateManagerPin(pinInput)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberCoffee),
                    modifier = Modifier.fillMaxWidth().height(48.dp).tactilePress().testTag("btn_save_manager_pin")
                ) {
                    Text("Perbarui PIN Manager")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // INFORMASI AKUN & KELUAR
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Informasi Akun",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isSignedIn) "Email: ${authUser?.email ?: "Akun Terotentikasi"}" else "Status: Mode Lokal (Tanpa Login)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isSignedIn) "Basis Data: Cloud Firestore Realtime" else "Basis Data: Penyimpanan Lokal (Siap Pratinjau)",
                    fontSize = 12.sp,
                    color = MutedGreyText
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isSignedIn) {
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                        modifier = Modifier.fillMaxWidth().height(48.dp).tactilePress().testTag("btn_logout")
                    ) {
                        Icon(Icons.Default.Logout, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Keluar dari Akun")
                    }
                } else {
                    Button(
                        onClick = onRequestSignIn,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).tactilePress().testTag("btn_connect_cloud")
                    ) {
                        Icon(Icons.Default.CloudSync, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Masuk / Hubungkan Cloud Firebase")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
