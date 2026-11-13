package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OperatingCost
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel

@Composable
fun OperationalScreen(
    viewModel: WarkopViewModel,
    modifier: Modifier = Modifier
) {
    val storeProfile by viewModel.storeProfile.collectAsState()
    val cost = storeProfile.operatingCost

    var waterUsage by remember(cost) { mutableStateOf(cost.waterUsage.toString()) }
    var waterTariff by remember(cost) { mutableStateOf(cost.waterTariff.toLong().toString()) }

    var elecUsage by remember(cost) { mutableStateOf(cost.electricityUsage.toString()) }
    var elecTariff by remember(cost) { mutableStateOf(cost.electricityTariff.toLong().toString()) }

    var gasUsage by remember(cost) { mutableStateOf(cost.gasUsage.toString()) }
    var gasTariff by remember(cost) { mutableStateOf(cost.gasTariff.toLong().toString()) }

    var laborHours by remember(cost) { mutableStateOf(cost.laborHours.toString()) }
    var laborTariff by remember(cost) { mutableStateOf(cost.laborTariff.toLong().toString()) }

    var targetUnits by remember(cost) { mutableStateOf(cost.targetUnitsPerDay.toInt().toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Estimator Biaya Operasional Harian",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Biaya dialokasikan merata berdasarkan target porsi per hari untuk penetapan HPP akurat.",
            fontSize = 12.sp,
            color = MutedGreyText
        )

        Spacer(modifier = Modifier.height(12.dp))

        // STATUS TOKO BANNER
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (storeProfile.isOpen) MintLight else Color(0xFFFDE8E4)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (storeProfile.isOpen) "Toko Buka (Biaya Operasional Aktif)" else "Toko Tutup (Biaya Operasional Ditunda)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (storeProfile.isOpen) ForestGreenDark else WarningRed
                    )
                    Text(
                        text = if (storeProfile.isOpen) "Biaya harian dihitung dalam laba rugi" else "Biaya operasional ditangguhkan sementara",
                        fontSize = 11.sp,
                        color = MutedGreyText
                    )
                }

                Switch(
                    checked = storeProfile.isOpen,
                    onCheckedChange = { viewModel.toggleStoreOpen() }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // FORMULIR KOMPONEN OPERASIONAL
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Air
                CostRow(
                    label = "Air Bersih",
                    unit = "m³",
                    usageValue = waterUsage,
                    onUsageChange = { waterUsage = it },
                    tariffValue = waterTariff,
                    onTariffChange = { waterTariff = it }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

                // Listrik
                CostRow(
                    label = "Listrik",
                    unit = "kWh",
                    usageValue = elecUsage,
                    onUsageChange = { elecUsage = it },
                    tariffValue = elecTariff,
                    onTariffChange = { elecTariff = it }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

                // Gas LPG
                CostRow(
                    label = "Gas LPG",
                    unit = "kg",
                    usageValue = gasUsage,
                    onUsageChange = { gasUsage = it },
                    tariffValue = gasTariff,
                    onTariffChange = { gasTariff = it }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

                // Tenaga Kerja
                CostRow(
                    label = "Tenaga Kerja / Barista",
                    unit = "jam",
                    usageValue = laborHours,
                    onUsageChange = { laborHours = it },
                    tariffValue = laborTariff,
                    onTariffChange = { laborTariff = it }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

                // Target Porsi Penjualan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Target Penjualan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Estimasi porsi per hari", fontSize = 11.sp, color = MutedGreyText)
                    }

                    OutlinedTextField(
                        value = targetUnits,
                        onValueChange = { targetUnits = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(110.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val newCost = OperatingCost(
                            waterUsage = waterUsage.toDoubleOrNull() ?: cost.waterUsage,
                            waterTariff = waterTariff.toDoubleOrNull() ?: cost.waterTariff,
                            electricityUsage = elecUsage.toDoubleOrNull() ?: cost.electricityUsage,
                            electricityTariff = elecTariff.toDoubleOrNull() ?: cost.electricityTariff,
                            gasUsage = gasUsage.toDoubleOrNull() ?: cost.gasUsage,
                            gasTariff = gasTariff.toDoubleOrNull() ?: cost.gasTariff,
                            laborHours = laborHours.toDoubleOrNull() ?: cost.laborHours,
                            laborTariff = laborTariff.toDoubleOrNull() ?: cost.laborTariff,
                            targetUnitsPerDay = targetUnits.toDoubleOrNull() ?: cost.targetUnitsPerDay
                        )
                        viewModel.updateOperatingCost(newCost)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).tactilePress()
                ) {
                    Text("Simpan Perubahan Biaya Operasional")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // RINGKASAN BIAYA HARIAN & PER UNIT
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Total Biaya Ops / Hari", fontSize = 11.sp, color = MutedGreyText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rp ${viewModel.formatRupiah(cost.dailyTotal())}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Alokasi Ops / Unit", fontSize = 11.sp, color = MutedGreyText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rp ${viewModel.formatRupiah(cost.perUnitCost())}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberCoffee
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CostRow(
    label: String,
    unit: String,
    usageValue: String,
    onUsageChange: (String) -> Unit,
    tariffValue: String,
    onTariffChange: (String) -> Unit
) {
    Column {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = usageValue,
                onValueChange = onUsageChange,
                label = { Text("Pemakaian ($unit)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = tariffValue,
                onValueChange = onTariffChange,
                label = { Text("Tarif/Unit (Rp)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1.2f)
            )
        }
    }
}
