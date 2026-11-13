package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.tactilePress
import com.example.ui.theme.*
import com.example.ui.viewmodel.WarkopViewModel
import kotlin.math.ceil

@Composable
fun PricingScreen(
    viewModel: WarkopViewModel,
    modifier: Modifier = Modifier
) {
    val storeProfile by viewModel.storeProfile.collectAsState()
    val menuList by viewModel.menuList.collectAsState()
    val inventoryList by viewModel.inventoryList.collectAsState()

    val opPerUnit = remember(storeProfile) {
        if (storeProfile.isOpen) storeProfile.operatingCost.perUnitCost() else 0.0
    }

    fun roundTo500(value: Double): Double {
        return ceil(value / 500.0) * 500.0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Kalkulator HPP & Rekomendasi Harga",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Rumus: HPP = Biaya bahan resep + Alokasi operasional/unit. Harga Rekomendasi = HPP ÷ (1 − margin target), dibulatkan ke kelipatan Rp500 terdekat.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                if (!storeProfile.isOpen) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Catatan: Toko saat ini tutup, alokasi beban operasional ditunda (Rp 0/unit).",
                        fontSize = 11.sp,
                        color = WarningRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (menuList.isEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Belum ada menu yang dibuat. Tambahkan menu di menu Stok & Resep.",
                    fontSize = 13.sp,
                    color = MutedGreyText,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            menuList.forEach { menu ->
                val costBahan = menu.recipe.sumOf { r ->
                    val ing = inventoryList.find { it.id == r.ingredientId }
                    (ing?.costPerUnit ?: 0.0) * r.quantity
                }
                val totalHpp = costBahan + opPerUnit
                val currentMargin = if (menu.price > 0) ((menu.price - totalHpp) / menu.price) * 100.0 else 0.0

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = menu.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Rp ${viewModel.formatRupiah(menu.price)} (Margin ${String.format(java.util.Locale.US, "%.1f", currentMargin)}%)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "Rincian HPP: Rp ${viewModel.formatRupiah(totalHpp)} (Bahan: Rp ${viewModel.formatRupiah(costBahan)} + Ops: Rp ${viewModel.formatRupiah(opPerUnit)})",
                            fontSize = 12.sp,
                            color = MutedGreyText,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        )

                        // 3 OPSI MARGIN (Defensif, Normal, Agresif)
                        val tiers = listOf(
                            Triple("Defensif", "Margin 10-15%", 0.125),
                            Triple("Normal", "Margin 25-35%", 0.30),
                            Triple("Agresif", "Margin 50%+", 0.55)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tiers.forEach { (title, subtitle, marginRate) ->
                                val targetPrice = roundTo500(totalHpp / (1.0 - marginRate))
                                val profitPerPortion = targetPrice - totalHpp

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(subtitle, fontSize = 10.sp, color = MutedGreyText)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Rp ${viewModel.formatRupiah(targetPrice)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Laba: +Rp ${viewModel.formatRupiah(profitPerPortion)}",
                                            fontSize = 10.sp,
                                            color = SuccessGreen
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Button(
                                            onClick = {
                                                viewModel.saveMenuItem(menu.copy(price = targetPrice))
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.fillMaxWidth().height(28.dp).tactilePress().testTag("apply_price_${menu.id}_$title")
                                        ) {
                                            Text("Terapkan", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
