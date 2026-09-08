package com.jw.preferences

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

@Composable
fun SwitchPreference(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    customSwitch: @Composable ((checked: Boolean, enabled: Boolean) -> Unit)? = null,
    showDivider: Boolean? = null,
) {
    Preference(
        title = title,
        modifier = modifier.toggleable(
            value = value,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onValueChange,
        ),
        enabled = enabled,
        icon = icon,
        summary = summary,
        widgetContainer = {
            val theme = LocalPreferenceTheme.current
            Box {
                when {
                    customSwitch != null -> customSwitch(value, enabled)
                    theme.customSwitch != null -> theme.customSwitch.invoke(value, enabled)
                    else -> Switch(
                        checked = value,
                        onCheckedChange = null,
                        enabled = enabled,
                    )
                }
            }
        },
        showDivider = showDivider,
    )
}
