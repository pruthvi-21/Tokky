package com.boxy.authenticator.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import boxy_authenticator.composeapp.generated.resources.Res
import boxy_authenticator.composeapp.generated.resources.refresh
import com.boxy.authenticator.domain.models.TokenEntry
import com.boxy.authenticator.domain.models.enums.LabelVisibility
import com.boxy.authenticator.domain.models.enums.shouldShowLabels
import com.boxy.authenticator.domain.models.otp.HotpInfo
import com.boxy.authenticator.domain.models.otp.TotpInfo
import com.boxy.authenticator.ui.components.design.BoxyProgressBar
import com.boxy.authenticator.ui.components.OtpTextView
import com.boxy.authenticator.ui.components.TokenThumbnail
import com.boxy.authenticator.utils.getInitials
import com.boxy.authenticator.utils.moveRight
import com.boxy.authenticator.utils.name
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

private const val SLIDE_DURATION = 150

@Composable
fun TokensList(
    tokensList: List<TokenEntry>,
    viewedTokenIds: Set<String>,
    onTokenViewed: (String) -> Unit,
    onUpdateHotpCounter: (String, Long, (Boolean) -> Unit) -> Unit,
    selectedTokenIds: Set<String>,
    onTokenLongPressed: (String) -> Unit,
    onTokenSelectionToggle: (String) -> Unit,
    labelVisibility: LabelVisibility,
    headerContent: @Composable () -> Unit = {},
    emptyMessage: String = "",
    singleExpansion: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    val expansionScope = rememberCoroutineScope()
    var expansionAnimationJob by remember { mutableStateOf<Job?>(null) }
    var isCardExpansionAnimating by remember { mutableStateOf(false) }
    val groupedAccounts = remember(tokensList) {
        tokensList
            .sortedBy { it.name.lowercase() }
            .groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }
    }

    val cardShape = MaterialTheme.shapes.medium

    LazyColumn(modifier = modifier) {
        item(key = "label-filters") {
            headerContent()
        }
        groupedAccounts.forEach { (letter, tokens) ->
            stickyHeader(key = "letter-$letter") {
                Text(
                    text = letter.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(vertical = 3.dp, horizontal = 37.5.dp)
                        .animateItem(
                            fadeInSpec = tween(SLIDE_DURATION),
                            placementSpec = if (isCardExpansionAnimating) null
                            else tween(SLIDE_DURATION),
                            fadeOutSpec = tween(SLIDE_DURATION),
                        ),
                )
            }
            itemsIndexed(tokens, key = { _, token -> token.id }) { index, token ->
                val shape = when {
                    tokens.size == 1 -> cardShape
                    index == 0 -> cardShape.copy(
                        bottomStart = CornerSize(0.dp),
                        bottomEnd = CornerSize(0.dp),
                    )

                    index == tokens.lastIndex -> cardShape.copy(
                        topStart = CornerSize(0.dp),
                        topEnd = CornerSize(0.dp),
                    )

                    else -> RectangleShape
                }
                Column(
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(SLIDE_DURATION),
                        placementSpec = if (isCardExpansionAnimating) null
                        else tween(SLIDE_DURATION),
                        fadeOutSpec = tween(SLIDE_DURATION),
                    )
                ) {
                    TokenCard(
                        token = token,
                        labelVisibility = labelVisibility,
                        isExpanded = expandedStates[token.id] ?: false,
                        isSelectionMode = selectedTokenIds.isNotEmpty(),
                        isSelected = token.id in selectedTokenIds,
                        isNewItem = token.id !in viewedTokenIds,
                        onUpdateHotpCounter = onUpdateHotpCounter,
                        onToggleExpand = { isExpanded ->
                            expansionAnimationJob?.cancel()
                            isCardExpansionAnimating = true
                            onTokenViewed(token.id)
                            if (singleExpansion) {
                                expandedStates.entries
                                    .firstOrNull { (id, expanded) -> expanded && id != token.id }
                                    ?.key
                                    ?.let { expandedStates[it] = false }
                            }
                            expandedStates[token.id] = isExpanded
                            expansionAnimationJob = expansionScope.launch {
                                delay(SLIDE_DURATION.toLong())
                                isCardExpansionAnimating = false
                            }
                        },
                        onLongPress = {
                            expandedStates[token.id] = false
                            onTokenLongPressed(token.id)
                        },
                        onSelectionToggle = { onTokenSelectionToggle(token.id) },
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .clip(shape)
                    )
                    if (index != tokens.lastIndex) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(start = 90.dp, end = 24.dp),
                        )
                    }
                }
            }
        }

        if (tokensList.isEmpty() && emptyMessage.isNotEmpty()) {
            item(key = "empty-filter-result") {
                Text(
                    text = emptyMessage,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 72.dp),
                )
            }
        }

        item {
            Text(
                text = "Showing ${tokensList.size} entries",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 15.dp)
            )
            Spacer(Modifier.height(60.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenCard(
    token: TokenEntry,
    labelVisibility: LabelVisibility,
    isExpanded: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isNewItem: Boolean,
    onUpdateHotpCounter: (String, Long, (Boolean) -> Unit) -> Unit,
    onToggleExpand: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    onSelectionToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedLabels = remember(token.labels) {
        token.labels.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(SLIDE_DURATION),
        label = "TokenSelectionColor",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onSelectionToggle()
                    else onToggleExpand(!isExpanded)
                },
                onLongClick = onLongPress,
            )
            .padding(horizontal = 24.dp, vertical = 15.dp)

    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TokenThumbnail(
                thumbnail = token.thumbnail,
                text = token.issuer.getInitials(),
                width = 55.dp,
            )

            Spacer(modifier = Modifier.width(4.dp))

            LabelsView(
                issuer = token.issuer,
                label = token.label,
                isNewItem = isNewItem,
                modifier = Modifier.weight(1f)
            )
            Arrow(
                isExpanded = isExpanded,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
            )
        }

        AnimatedVisibility(
            visible = !token.isArchived && token.labels.isNotEmpty() &&
                    labelVisibility.shouldShowLabels(isExpanded),
            enter = expandVertically(animationSpec = tween(SLIDE_DURATION)),
            exit = shrinkVertically(animationSpec = tween(SLIDE_DURATION)),
        ) {
            FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 59.dp, top = 6.dp),
            ) {
                sortedLabels.forEach { label ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(SLIDE_DURATION)
            ),
            exit = shrinkVertically(
                animationSpec = tween(SLIDE_DURATION)
            )
        ) {
            when (token.otpInfo) {
                is HotpInfo -> HOTPFieldView(token.id, token.otpInfo, onUpdateHotpCounter)
                is TotpInfo -> TOTPFieldView(token.otpInfo)
            }
        }
    }
}

@Composable
private fun HOTPFieldView(
    tokenId: String,
    otpInfo: HotpInfo,
    onUpdateCounter: (String, Long, (Boolean) -> Unit) -> Unit,
) {
    var counter by remember(tokenId, otpInfo.counter) { mutableLongStateOf(otpInfo.counter) }
    var otp by remember(tokenId, otpInfo) { mutableStateOf(otpInfo.getOtp()) }
    val scope = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 55.dp)
            .padding(top = 10.dp)
    ) {
        Text(
            text = "#$counter",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(55.dp)
        )
        OtpTextView(
            otp = otp,
            modifier = Modifier.weight(1f).padding(15.dp)
        )

        var isUpdating by remember { mutableStateOf(false) }
        IconButton(
            onClick = {
                if (isUpdating || counter == Long.MAX_VALUE) return@IconButton

                scope.launch {
                    isUpdating = true
                    val nextCounter = counter + 1
                    onUpdateCounter(tokenId, nextCounter) { success ->
                        scope.launch {
                            if (success) {
                                counter = nextCounter
                                otp = HotpInfo(
                                    secretKey = otpInfo.secretKey,
                                    algorithm = otpInfo.algorithm,
                                    digits = otpInfo.digits,
                                    counter = nextCounter,
                                ).getOtp()
                                delay(1000)
                                isUpdating = false
                            } else {
                                delay(500)
                                isUpdating = false
                            }
                        }
                    }
                }
            },
            enabled = !isUpdating && counter < Long.MAX_VALUE,
            modifier = Modifier.moveRight(15.dp)
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(Res.string.refresh))
        }
    }
}

@Composable
private fun TOTPFieldView(
    otpInfo: TotpInfo,
) {
    data class OtpState(
        val value: String,
        val progress: Float,
        val duration: Long,
        val isAnimating: Boolean = true,
    )

    val coroutineScope = rememberCoroutineScope()
    val totalPeriodMillis = otpInfo.period * 1000L

    val otpState = remember {
        mutableStateOf(
            OtpState(
                value = otpInfo.getOtp(),
                progress = otpInfo.getMillisTillNextRotation().toFloat() / totalPeriodMillis,
                duration = otpInfo.getMillisTillNextRotation() % totalPeriodMillis
            )
        )
    }

    LaunchedEffect(otpInfo) {
        while (true) {
            delay(otpState.value.duration)
            otpState.value = OtpState(
                value = otpInfo.getOtp(),
                progress = 1f,
                duration = totalPeriodMillis
            )
        }
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            coroutineScope.launch {
                otpState.value = OtpState(
                    value = otpInfo.getOtp(),
                    progress = otpInfo.getMillisTillNextRotation().toFloat() / totalPeriodMillis,
                    duration = otpInfo.getMillisTillNextRotation() % totalPeriodMillis
                )
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "OTP Progress")
    val progressAnimationValue by infiniteTransition.animateFloat(
        initialValue = otpState.value.progress,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = otpState.value.duration.toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "OTP Progress Animation"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = 55.dp,
                    height = 37.5.dp,
                ), contentAlignment = Alignment.Center
        ) {
            BoxyProgressBar(
                progress = progressAnimationValue,
                width = 28.dp,
                height = 28.dp,
            )
        }

        OtpTextView(
            otp = otpState.value.value,
            modifier = Modifier
                .padding(horizontal = 15.dp)
        )
    }
}

@Composable
private fun Arrow(
    isExpanded: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(40.dp)
            .alpha(.5f)
    ) {
        val animationProgress by animateFloatAsState(
            targetValue = if (isExpanded) 1f else 0f,
            animationSpec = tween(SLIDE_DURATION),
            label = "ExpandCollapseAnimation"
        )

        Icon(
            imageVector = when {
                isSelectionMode && isSelected -> Icons.Outlined.CheckCircle
                isSelectionMode -> Icons.Outlined.RadioButtonUnchecked
                else -> Icons.Rounded.ArrowBackIosNew
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(20.dp)
                .fillMaxHeight()
                .graphicsLayer(
                    rotationZ = if (isSelectionMode) 0f
                    else -90f + animationProgress * 180f
                )
        )
    }
}

@Composable
private fun LabelsView(
    issuer: String,
    label: String,
    isNewItem: Boolean = false,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = issuer,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isNewItem) {
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (-7.5).dp)
                )
            }
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color(0xFFA6A6A6),
                    fontSize = 13.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
