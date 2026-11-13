package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MutedGreyText
import com.example.ui.theme.WarningRed
import com.example.util.BiometricHelper

@Composable
fun SecurityPinBiometricDialog(
    title: String,
    subtitle: String,
    pinLabel: String = "Masukkan PIN Verifikasi",
    biometricTitle: String = "Verifikasi Sidik Jari",
    biometricSubtitle: String = "Tempelkan jari untuk mengonfirmasi tindakan",
    confirmButtonText: String = "Verifikasi PIN",
    onDismiss: () -> Unit,
    onConfirmPin: (String) -> Boolean,
    onBiometricSuccess: () -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val biometricAvailability = remember(context) {
        BiometricHelper.checkAvailability(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MutedGreyText,
                    lineHeight = 18.sp
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        errorMsg = null
                    },
                    label = { Text(pinLabel) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_security_pin_input")
                )

                OutlinedButton(
                    onClick = {
                        BiometricHelper.authenticate(
                            context = context,
                            title = biometricTitle,
                            subtitle = biometricSubtitle,
                            onSuccess = {
                                onBiometricSuccess()
                            },
                            onError = { err ->
                                errorMsg = err
                            }
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tactilePress()
                        .testTag("btn_dialog_fingerprint")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Sidik Jari",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (biometricAvailability.isAvailable) {
                            "Verifikasi dengan Sidik Jari"
                        } else {
                            "Sidik Jari (Cek Perangkat)"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                if (!biometricAvailability.isAvailable) {
                    Text(
                        text = biometricAvailability.message,
                        fontSize = 11.sp,
                        color = MutedGreyText
                    )
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        fontSize = 12.sp,
                        color = WarningRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinInput.isBlank()) {
                        errorMsg = "Masukkan PIN terlebih dahulu atau gunakan sidik jari"
                    } else {
                        val ok = onConfirmPin(pinInput)
                        if (!ok) {
                            errorMsg = "PIN tidak valid. Periksa kembali PIN Anda."
                        }
                    }
                },
                modifier = Modifier
                    .tactilePress()
                    .testTag("btn_confirm_security_pin")
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
