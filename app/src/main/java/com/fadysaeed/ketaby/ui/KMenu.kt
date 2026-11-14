package com.fadysaeed.ketaby.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * A DropdownMenu that keeps the app's text direction. A menu opens in its own popup window,
 * which takes the phone's layout direction instead of the one App() provides, so Arabic items
 * were laid out left-to-right (the English part landed on the wrong side).
 */
@Composable
fun KDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dir = LocalLayoutDirection.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = if (containerColor == Color.Unspecified) MenuDefaults.containerColor else containerColor,
    ) {
        val column = this
        CompositionLocalProvider(LocalLayoutDirection provides dir) { column.content() }
    }
}
