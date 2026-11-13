package com.example.ui.auth

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    onAuthSuccess: (storeName: String, managerName: String) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    // 0 = Masuk (Login), 1 = Daftar (Register / Buat Akun Baru)
    var selectedAuthTab by remember { mutableStateOf(1) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun handleGoogleAuth() {
        isLoading = true
        errorMessage = null

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            errorMessage = "Konfigurasi Google Sign-In tidak ditemukan (default_web_client_id)."
            isLoading = false
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        coroutineScope.launch {
            try {
                val result = credentialManager.getCredential(context as Activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    val name = user?.displayName ?: "Manager"
                    onAuthSuccess("Warkop Emak", name)
                } else {
                    errorMessage = "Tipe kredensial tidak dikenali"
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In dibatalkan atau ditutup: ${e.message}")
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In gagal", e)
                errorMessage = e.localizedMessage ?: "Gagal memproses autentikasi"
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
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
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Coffee,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Kasir Warkop Emak",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Sistem Kasir Digital, Manajemen Stok & Pembukuan Kasbon Real-time",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                // TAB PILIHAN MASUK / DAFTAR
                TabRow(
                    selectedTabIndex = selectedAuthTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedAuthTab == 0,
                        onClick = {
                            selectedAuthTab = 0
                            errorMessage = null
                        },
                        text = {
                            Text(
                                "Masuk",
                                fontWeight = if (selectedAuthTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("tab_login")
                    )
                    Tab(
                        selected = selectedAuthTab == 1,
                        onClick = {
                            selectedAuthTab = 1
                            errorMessage = null
                        },
                        text = {
                            Text(
                                "Daftar Akun Baru",
                                fontWeight = if (selectedAuthTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("tab_register")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (selectedAuthTab == 1) {
                    // MODE DAFTAR AKUN BARU
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pendaftaran Akun Manager",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Daftarkan akun utama warkop Anda dengan satu ketukan. Detail seperti nama toko, alamat, dan PIN otorisasi dapat diisi langsung setelah masuk ke dashboard warkop.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { handleGoogleAuth() },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .tactilePress()
                            .testTag("btn_register_account")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Daftar & Buat Akun Manager Baru",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    TextButton(
                        onClick = { selectedAuthTab = 0 },
                        modifier = Modifier.testTag("switch_to_login")
                    ) {
                        Text(
                            text = "Sudah memiliki akun? Masuk di sini",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // MODE MASUK (LOGIN)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Masuk ke Akun Terdaftar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Masuk kembali untuk mengakses pencatatan kasir, sinkronisasi stok, dan buku kasbon warkop Anda.",
                                fontSize = 12.sp,
                                color = MutedGreyText,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { handleGoogleAuth() },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .tactilePress()
                            .testTag("btn_login_account")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Masuk dengan Akun Google",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    TextButton(
                        onClick = { selectedAuthTab = 1 },
                        modifier = Modifier.testTag("switch_to_register")
                    ) {
                        Text(
                            text = "Belum memiliki akun? Daftar akun baru di sini",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Akun Google akan terdaftar sebagai Akun Utama (Manager). Sub-akun kasir lapangan dapat diatur dan dibuat di dalam dashboard.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )

                if (onDismiss != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().tactilePress().testTag("btn_dismiss_auth")
                    ) {
                        Text("Kembali ke Aplikasi (Mode Lokal)")
                    }
                }
            }
        }
    }
}
