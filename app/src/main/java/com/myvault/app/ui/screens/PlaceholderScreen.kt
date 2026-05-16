package com.myvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun PlaceholderScreen(title: String) {
    val colors = VaultThemeTokens.colors

    Scaffold(
        containerColor = colors.bg,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                .padding(innerPadding)
                .padding(VaultSpacing.screen),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            ) {
                Text(
                    text = title,
                    color = colors.text,
                )
                Text(
                    text = "Pass 1 placeholder",
                    color = colors.textMuted,
                )
            }
        }
    }
}
