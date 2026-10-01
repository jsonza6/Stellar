package roro.stellar.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import roro.stellar.manager.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Miuix 风格的确认对话框。
 *
 * 使用 [WindowDialog]（独立窗口层）而不是 Overlay 变体，这样在任何页面层级都能弹出，
 * 不要求调用方处于 `Scaffold` 的 popup host 之下。
 */
@Composable
fun StellarDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String = stringResource(R.string.cancel),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = onDismissRequest,
    confirmEnabled: Boolean = true,
    showDismissButton: Boolean = true,
    leadingAction: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingAction?.invoke(this)
                Spacer(Modifier.weight(1f))
                if (showDismissButton) {
                    TextButton(
                        text = dismissText,
                        onClick = onDismiss
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}

@Composable
fun StellarInfoDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmText: String = stringResource(R.string.confirm),
    onConfirm: () -> Unit = onDismissRequest
) {
    StellarDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        showDismissButton = false
    ) {
        Text(message)
    }
}
