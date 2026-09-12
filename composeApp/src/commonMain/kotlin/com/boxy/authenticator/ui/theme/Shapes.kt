package com.boxy.authenticator.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

private val radius = 8.dp
val TokkyShapes = Shapes().copy(
    extraSmall = RoundedCornerShape(radius),
    small = RoundedCornerShape(radius),
    medium = RoundedCornerShape(radius),
    large = RoundedCornerShape(radius),
    extraLarge = RoundedCornerShape(radius),
)
