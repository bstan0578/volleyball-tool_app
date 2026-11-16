package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.VolleyDarkSurface
import com.example.ui.theme.VolleyDarkSurfaceVariant
import com.example.ui.theme.VolleyGrayBorder
import com.example.ui.theme.VolleyYellow

/**
 * High-contrast stepper button with zero contentPadding so '+' and '-' icons are ALWAYS centered and visible.
 * Guarantees >= 48dp accessible touch target by default.
 */
@Composable
fun VolleyStepperButton(
    isIncrement: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sizeDp: Dp = 48.dp,
    accentColor: Color = VolleyYellow,
    testTagName: String? = null
) {
    val finalModifier = modifier
        .size(sizeDp)
        .let { if (testTagName != null) it.testTag(testTagName) else it }

    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) VolleyGrayBorder else VolleyGrayBorder.copy(alpha = 0.35f)
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = VolleyDarkSurfaceVariant,
            contentColor = accentColor,
            disabledContainerColor = VolleyDarkSurfaceVariant.copy(alpha = 0.4f),
            disabledContentColor = VolleyGrayBorder
        ),
        modifier = finalModifier
    ) {
        Icon(
            imageVector = if (isIncrement) Icons.Default.Add else Icons.Default.Remove,
            contentDescription = if (isIncrement) "Erhöhen (+)" else "Verringern (-)",
            tint = if (enabled) accentColor else VolleyGrayBorder,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Viewport-safe responsive form dialog:
 * - Uses full safe width (up to 560.dp) instead of cramped M3 AlertDialog margins.
 * - Scrollable content body with weight(1f, fill = false) so footer action buttons
 *   (Speichern / Übernehmen / Abbrechen) stay pinned and NEVER get pushed off-screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VolleyFormDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxDialogHeight = (maxHeight * 0.94f).coerceAtLeast(240.dp)
            Surface(
                color = VolleyDarkSurface,
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = maxDialogHeight)
                    .border(1.dp, VolleyGrayBorder, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        title()
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
