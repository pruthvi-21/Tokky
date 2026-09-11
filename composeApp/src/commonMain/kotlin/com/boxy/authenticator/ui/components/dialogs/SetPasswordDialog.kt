package com.boxy.authenticator.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.confirm_password
import boxy_authenticator.composeapp.generated.resources.enter_a_password
import boxy_authenticator.composeapp.generated.resources.ok
import boxy_authenticator.composeapp.generated.resources.password
import boxy_authenticator.composeapp.generated.resources.password_cant_be_empty
import boxy_authenticator.composeapp.generated.resources.password_didnt_match
import boxy_authenticator.composeapp.generated.resources.password_must_be_numeric
import boxy_authenticator.composeapp.generated.resources.password_too_short
import boxy_authenticator.composeapp.generated.resources.set_password
import boxy_authenticator.composeapp.generated.resources.show_password
import com.boxy.authenticator.ui.components.design.BoxyTextField
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SetPasswordDialog(
    dialogBody: String? = null,
    confirmText: String = stringResource(Res.string.ok),
    numericOnly: Boolean = false,
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    var showPassword by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    BoxyDialog(
        dialogTitle = stringResource(Res.string.set_password),
        dialogBody = dialogBody,
        confirmText = confirmText,
        onDismissRequest = {
            onDismissRequest()
        },
        onConfirmation = {
            scope.launch {
                passwordError = when {
                    password.isEmpty() -> getString(Res.string.password_cant_be_empty)
                    password.length < 6 -> getString(Res.string.password_too_short, 6)
                    numericOnly && password.any { !it.isDigit() } ->
                        getString(Res.string.password_must_be_numeric)

                    else -> null
                }

                confirmPasswordError = when {
                    confirmPassword != password -> getString(Res.string.password_didnt_match)
                    else -> null
                }

                if (passwordError == null && confirmPasswordError == null) {
                    onConfirmation(password)
                }
            }
        }
    ) {
        Column {
            val focusManager = LocalFocusManager.current
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            BoxyTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = null
                },
                placeholder = stringResource(Res.string.enter_a_password),
                label = stringResource(Res.string.password),
                isPasswordField = !showPassword,
                hidePasswordVisibilityEye = true,
                errorMessage = passwordError,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Next,
                    keyboardType = if (numericOnly) KeyboardType.NumberPassword else KeyboardType.Text,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.focusRequester(focusRequester)
            )
            Spacer(Modifier.height(10.dp))
            BoxyTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    confirmPasswordError = null
                },
                placeholder = stringResource(Res.string.confirm_password),
                label = stringResource(Res.string.confirm_password),
                isPasswordField = !showPassword,
                hidePasswordVisibilityEye = true,
                errorMessage = confirmPasswordError,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                    keyboardType = if (numericOnly) KeyboardType.NumberPassword else KeyboardType.Text,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.clearFocus() }
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clickable {
                        showPassword = !showPassword
                    }
                    .padding(end = 15.dp)
                    .align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showPassword,
                    onCheckedChange = { showPassword = !showPassword },
                )

                Text(stringResource(Res.string.show_password))
            }
        }
    }
}
