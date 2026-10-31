package com.droid.googlebookfeaturechecker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.googlebookfeaturechecker.model.DependencyCheckItem
import com.droid.googlebookfeaturechecker.model.DependencyId
import com.droid.googlebookfeaturechecker.model.FeatureCheckerUiState
import com.droid.googlebookfeaturechecker.model.FeatureItem
import com.droid.googlebookfeaturechecker.model.PhoneReportState
import com.droid.googlebookfeaturechecker.model.ThemeMode
import com.droid.googlebookfeaturechecker.ui.theme.LocalStudioColors

/**
 * Android 16 / googlebook.studio grouped card corner radius shape:
 * outer edges are 22.dp, internal edges between stacked rows are 6.dp with 2.dp gap.
 */
fun groupShape(i: Int, n: Int, big: Dp = 22.dp, small: Dp = 6.dp) = when {
  n <= 1 -> RoundedCornerShape(big)
  i == 0 -> RoundedCornerShape(topStart = big, topEnd = big, bottomEnd = small, bottomStart = small)
  i == n - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomEnd = big, bottomStart = big)
  else -> RoundedCornerShape(small)
}

@Composable
private fun CaptionSpacer(color: Color) {
  val top = WindowInsets.captionBar.getTop(LocalDensity.current)
  if (top > 0) {
    Box(
      Modifier
        .fillMaxWidth()
        .height(with(LocalDensity.current) { top.toDp() })
        .background(color)
    )
  }
}

@Composable
private fun HeaderRow(
  background: Color,
  height: Dp = 56.dp,
  content: @Composable RowScope.() -> Unit
) {
  Row(
    Modifier
      .fillMaxWidth()
      .height(height)
      .background(background)
      .padding(horizontal = 24.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content
  )
}

@Composable
private fun StatusDot(text: String, ok: Boolean) {
  val dotColor = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Box(
      Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(dotColor)
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
fun FeatureCheckerScreen(
  uiState: FeatureCheckerUiState,
  onSelectFeature: (String) -> Unit,
  onToggleCheckMark: (DependencyId) -> Unit,
  onFocusDependency: (Int) -> Unit,
  onMoveFocusNext: () -> Unit,
  onMoveFocusPrevious: () -> Unit,
  onToggleFocusedDependency: () -> Unit,
  onExecuteAction: (DependencyCheckItem) -> Unit,
  onRefreshChecks: () -> Unit,
  onSetThemeMode: (ThemeMode) -> Unit,
  onCycleThemeMode: () -> Unit,
  onSendFeedbackReport: () -> Unit,
  onOpenReferenceThread: () -> Unit,
  modifier: Modifier = Modifier
) {
  val studio = LocalStudioColors.current
  val chrome = studio.chrome
  val metCount = uiState.dependencies.count { it.isMet }
  val totalCount = uiState.dependencies.size
  val allMet = metCount == totalCount && totalCount > 0

  Surface(
    modifier = modifier
      .fillMaxSize()
      .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
          when (event.key) {
            Key.DirectionDown -> {
              onMoveFocusNext()
              true
            }
            Key.DirectionUp -> {
              onMoveFocusPrevious()
              true
            }
            Key.Spacebar, Key.Enter -> {
              onToggleFocusedDependency()
              true
            }
            Key.R -> {
              onRefreshChecks()
              true
            }
            Key.T -> {
              onCycleThemeMode()
              true
            }
            Key.F -> {
              onSendFeedbackReport()
              true
            }
            else -> false
          }
        } else {
          false
        }
      },
    color = MaterialTheme.colorScheme.surface
  ) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val isCompactPhone = maxWidth < 600.dp
      val wide = maxWidth >= 800.dp
      val horizontalPad = when {
        isCompactPhone -> 16.dp
        wide -> 32.dp
        else -> 24.dp
      }

      Column(Modifier.fillMaxSize()) {
        CaptionSpacer(chrome)
        Box(Modifier.fillMaxWidth().background(chrome)) {
          Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        }

        // Adaptive googlebook.studio Header (2 compact rows on phone < 600dp, 1 wide row on tablet/desktop)
        if (isCompactPhone) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(chrome)
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Googlebook Feature Checker",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              Spacer(Modifier.width(8.dp))
              RecheckPillButton(onRefreshChecks = onRefreshChecks)
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Box(modifier = Modifier.weight(1f)) {
                StatusDot(
                  text = if (allMet) {
                    "All $totalCount met · ${uiState.localDeviceName}"
                  } else {
                    "$metCount/$totalCount met · ${uiState.localDeviceName}"
                  },
                  ok = allMet
                )
              }
              Spacer(Modifier.width(8.dp))
              ConnectedThemeSwitcher(
                currentMode = uiState.themeMode,
                compact = true,
                onSetThemeMode = onSetThemeMode
              )
            }
          }
        } else {
          HeaderRow(background = chrome, height = 56.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
              Text(
                text = "Googlebook Feature Checker",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
              StatusDot(
                text = if (allMet) {
                  "All $totalCount dependencies met · ${uiState.localDeviceName}"
                } else {
                  "$metCount of $totalCount dependencies met · ${uiState.localDeviceName}"
                },
                ok = allMet
              )
            }

            Spacer(Modifier.weight(1f))

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              ConnectedThemeSwitcher(
                currentMode = uiState.themeMode,
                compact = false,
                onSetThemeMode = onSetThemeMode
              )
              RecheckPillButton(onRefreshChecks = onRefreshChecks)
            }
          }
        }

        // Optional quiet toast/banner row when an action is triggered
        uiState.statusBannerMessage?.let { bannerMsg ->
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(chrome)
              .padding(horizontal = horizontalPad, vertical = 6.dp)
              .testTag("status_banner")
          ) {
            Text(
              text = bannerMsg,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        if (wide) {
          Row(Modifier.fillMaxSize()) {
            // Left Pane (chrome surface, 420.dp wide, matching HearOn Link's DevicePane)
            Column(
              modifier = Modifier
                .width(420.dp)
                .fillMaxHeight()
                .background(chrome)
                .verticalScroll(rememberScrollState())
                .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 28.dp),
              verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
              StudioDeviceOverviewPane(
                uiState = uiState,
                isCompactPhone = false,
                onSelectFeature = onSelectFeature,
                onOpenReferenceThread = onOpenReferenceThread
              )
            }

            // Right Pane (surface canvas with grouped borderless M3 Expressive cards)
            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
            ) {
              StudioDependenciesColumn(
                uiState = uiState,
                includeOverviewPane = false,
                isCompactPhone = false,
                horizontalPad = 32.dp,
                onSelectFeature = onSelectFeature,
                onToggleCheckMark = onToggleCheckMark,
                onFocusDependency = onFocusDependency,
                onExecuteAction = onExecuteAction,
                onRefreshChecks = onRefreshChecks,
                onSendFeedbackReport = onSendFeedbackReport,
                onOpenReferenceThread = onOpenReferenceThread
              )
            }
          }
        } else {
          StudioDependenciesColumn(
            uiState = uiState,
            includeOverviewPane = true,
            isCompactPhone = isCompactPhone,
            horizontalPad = horizontalPad,
            onSelectFeature = onSelectFeature,
            onToggleCheckMark = onToggleCheckMark,
            onFocusDependency = onFocusDependency,
            onExecuteAction = onExecuteAction,
            onRefreshChecks = onRefreshChecks,
            onSendFeedbackReport = onSendFeedbackReport,
            onOpenReferenceThread = onOpenReferenceThread
          )
        }
      }
    }
  }
}

@Composable
private fun ConnectedThemeSwitcher(
  currentMode: ThemeMode,
  compact: Boolean,
  onSetThemeMode: (ThemeMode) -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    ThemeMode.entries.forEachIndexed { idx, mode ->
      val selected = currentMode == mode
      val shape = when (idx) {
        0 -> RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 6.dp, bottomEnd = 6.dp)
        ThemeMode.entries.lastIndex -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp)
        else -> RoundedCornerShape(6.dp)
      }
      Box(
        modifier = Modifier
          .height(if (compact) 30.dp else 34.dp)
          .clip(shape)
          .background(
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer
          )
          .clickable { onSetThemeMode(mode) }
          .padding(horizontal = if (compact) 9.dp else 12.dp)
          .testTag("theme_chip_${mode.name}"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = mode.displayName,
          style = MaterialTheme.typography.labelMedium,
          color = if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSecondaryContainer
        )
      }
    }
  }
}

@Composable
private fun RecheckPillButton(onRefreshChecks: () -> Unit) {
  Box(
    modifier = Modifier
      .height(34.dp)
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.secondaryContainer)
      .clickable(onClick = onRefreshChecks)
      .padding(horizontal = 12.dp)
      .testTag("recheck_all_button"),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = "Re-check",
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = "Re-check",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer
      )
    }
  }
}

/**
 * Overview pane in googlebook.studio / HearOn Link visual style:
 * 1. Minimalist line-art illustration of a Googlebook laptop & Pixel phone tapping from the side.
 * 2. Readiness metric cells with thin progress bars (Met, Click-marked, Phone status).
 * 3. Feature selector tile ("Googlebook Better Together") + grouped capability rows.
 */
@Composable
private fun StudioDeviceOverviewPane(
  uiState: FeatureCheckerUiState,
  isCompactPhone: Boolean,
  onSelectFeature: (String) -> Unit,
  onOpenReferenceThread: () -> Unit
) {
  val studio = LocalStudioColors.current
  val metCount = uiState.dependencies.count { it.isMet }
  val checkedCount = uiState.dependencies.count { it.isChecked }
  val total = uiState.dependencies.size.coerceAtLeast(1)

  // 1. Minimalist Line-Art Illustration (compact height on phone so checklist stays above the fold)
  GooglebookAndPixelLineArt(
    allMet = metCount == total,
    modifier = Modifier
      .fillMaxWidth()
      .height(if (isCompactPhone) 124.dp else 158.dp)
  )

  // 2. Three Metric Cells with thin rounded progress bars
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(if (isCompactPhone) 10.dp else 18.dp)
  ) {
    MetricBarCell(
      label = "Dependencies",
      headline = "$metCount/$total",
      fraction = metCount.toFloat() / total.toFloat(),
      sub = if (metCount == total) "All met" else "${total - metCount} need fix",
      compact = isCompactPhone,
      modifier = Modifier.weight(1f)
    )
    MetricBarCell(
      label = "Click-marked",
      headline = "$checkedCount/$total",
      fraction = checkedCount.toFloat() / total.toFloat(),
      sub = "Click to mark",
      compact = isCompactPhone,
      modifier = Modifier.weight(1f)
    )
    val phoneConnected = uiState.phoneReportState is PhoneReportState.Connected
    MetricBarCell(
      label = "Phone check",
      headline = if (phoneConnected) "Linked" else "Tip",
      fraction = if (phoneConnected) 1f else 0.35f,
      sub = if (phoneConnected) "Verified" else "Run on phone",
      compact = isCompactPhone,
      modifier = Modifier.weight(1f)
    )
  }

  // 3. Connected Feature Selector ("Feature")
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Feature",
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      uiState.features.forEach { feature ->
        val selected = feature.id == uiState.selectedFeature.id
        Box(
          modifier = Modifier
            .weight(1f)
            .height(68.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
              if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.secondaryContainer
            )
            .clickable { onSelectFeature(feature.id) }
            .padding(horizontal = 16.dp)
            .testTag("feature_card_${feature.id}"),
          contentAlignment = Alignment.CenterStart
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Devices,
              contentDescription = null,
              tint = if (selected) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(22.dp)
            )
            Column {
              Text(
                text = feature.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer
              )
              Text(
                text = feature.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = (if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer).copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
  }

  // 4. Grouped White Cards on Left Pane (like "Conversation awareness" / "Personalized volume" in HearOn Link)
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    val subFeatures = uiState.selectedFeature.subFeatures
    val totalRows = subFeatures.size + 1
    subFeatures.forEachIndexed { idx, line ->
      val parts = line.split(" · ", limit = 2)
      val title = parts.firstOrNull().orEmpty()
      val sub = parts.getOrNull(1)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(groupShape(idx, totalRows))
          .background(studio.card)
          .padding(horizontal = 18.dp, vertical = 12.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight(580)),
            color = MaterialTheme.colorScheme.onSurface
          )
          if (sub != null) {
            Text(
              text = sub,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    // Bottom row in group: Reference Support Thread #472325070
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(groupShape(totalRows - 1, totalRows))
        .background(studio.card)
        .clickable(onClick = onOpenReferenceThread)
        .padding(horizontal = 18.dp, vertical = 13.dp)
        .testTag("reference_thread_button"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Rollout & setup guide",
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight(580)),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "Googlebook Community thread #472325070",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}

@Composable
private fun MetricBarCell(
  label: String,
  headline: String,
  fraction: Float,
  sub: String,
  modifier: Modifier = Modifier,
  compact: Boolean = false
) {
  val studio = LocalStudioColors.current
  val primary = MaterialTheme.colorScheme.primary
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = headline,
      style = MaterialTheme.typography.headlineSmall.copy(fontSize = if (compact) 19.sp else 23.sp),
      color = MaterialTheme.colorScheme.onSurface
    )
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(7.dp)
        .clip(CircleShape)
        .background(studio.track)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(fraction.coerceIn(0.08f, 1f))
          .fillMaxHeight()
          .clip(CircleShape)
          .background(primary)
      )
    }
    Text(
      text = sub,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun GooglebookAndPixelLineArt(
  allMet: Boolean,
  modifier: Modifier = Modifier
) {
  val cardFill = LocalStudioColors.current.card
  val strokeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
  val accentColor = MaterialTheme.colorScheme.primary
  val warnColor = MaterialTheme.colorScheme.tertiary

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val cx = w * 0.45f
    val cy = h * 0.54f
    val strokeW = 2.dp.toPx()

    // Googlebook screen frame (clean white fill + subtle outline like HearOn Link's case illustration)
    val lidW = 132.dp.toPx()
    val lidH = 84.dp.toPx()
    val lidLeft = cx - lidW / 2f
    val lidTop = cy - lidH / 2f - 6.dp.toPx()

    drawRoundRect(
      color = cardFill,
      topLeft = Offset(lidLeft, lidTop),
      size = Size(lidW, lidH),
      cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
    )
    drawRoundRect(
      color = strokeColor,
      topLeft = Offset(lidLeft, lidTop),
      size = Size(lidW, lidH),
      cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
      style = Stroke(width = strokeW)
    )

    // Googlebook base lip
    val baseW = 152.dp.toPx()
    val baseH = 10.dp.toPx()
    val baseLeft = cx - baseW / 2f
    val baseTop = lidTop + lidH
    drawRoundRect(
      color = cardFill,
      topLeft = Offset(baseLeft, baseTop),
      size = Size(baseW, baseH),
      cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
    )
    drawRoundRect(
      color = strokeColor,
      topLeft = Offset(baseLeft, baseTop),
      size = Size(baseW, baseH),
      cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
      style = Stroke(width = strokeW)
    )

    // Center status LED dot inside Googlebook (just like the teal dot on the AirPods case)
    drawCircle(
      color = if (allMet) accentColor else warnColor,
      radius = 3.5.dp.toPx(),
      center = Offset(cx, lidTop + lidH * 0.52f)
    )

    // Tilted Pixel phone tapping from the right side
    val phonePivotX = lidLeft + lidW + 18.dp.toPx()
    val phonePivotY = cy - 4.dp.toPx()
    val phoneW = 46.dp.toPx()
    val phoneH = 88.dp.toPx()

    withTransform({
      rotate(degrees = -16f, pivot = Offset(phonePivotX, phonePivotY))
    }) {
      val pLeft = phonePivotX - phoneW / 2f
      val pTop = phonePivotY - phoneH / 2f
      drawRoundRect(
        color = cardFill,
        topLeft = Offset(pLeft, pTop),
        size = Size(phoneW, phoneH),
        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
      )
      drawRoundRect(
        color = strokeColor,
        topLeft = Offset(pLeft, pTop),
        size = Size(phoneW, phoneH),
        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
        style = Stroke(width = strokeW)
      )
      // Pixel camera bar visor line
      val visorY = pTop + 18.dp.toPx()
      val visorH = 10.dp.toPx()
      drawLine(
        color = strokeColor,
        start = Offset(pLeft, visorY),
        end = Offset(pLeft + phoneW, visorY),
        strokeWidth = strokeW
      )
      drawLine(
        color = strokeColor,
        start = Offset(pLeft, visorY + visorH),
        end = Offset(pLeft + phoneW, visorY + visorH),
        strokeWidth = strokeW
      )
      // Camera pill inside visor
      drawRoundRect(
        color = strokeColor,
        topLeft = Offset(pLeft + 8.dp.toPx(), visorY + 2.5.dp.toPx()),
        size = Size(18.dp.toPx(), 5.dp.toPx()),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        style = Stroke(width = strokeW * 0.85f)
      )
      // Teal sync dot on Pixel
      drawCircle(
        color = accentColor,
        radius = 3.dp.toPx(),
        center = Offset(phonePivotX, pTop + phoneH * 0.62f)
      )
    }

    // Subtle tap ripple arc between Pixel and Googlebook
    val ripplePath = Path().apply {
      moveTo(lidLeft + lidW - 3.dp.toPx(), cy - 14.dp.toPx())
      quadraticTo(
        lidLeft + lidW - 12.dp.toPx(),
        cy - 2.dp.toPx(),
        lidLeft + lidW - 3.dp.toPx(),
        cy + 10.dp.toPx()
      )
    }
    drawPath(
      path = ripplePath,
      color = accentColor.copy(alpha = 0.75f),
      style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
  }
}

@Composable
private fun StudioDependenciesColumn(
  uiState: FeatureCheckerUiState,
  includeOverviewPane: Boolean,
  isCompactPhone: Boolean,
  horizontalPad: Dp,
  onSelectFeature: (String) -> Unit,
  onToggleCheckMark: (DependencyId) -> Unit,
  onFocusDependency: (Int) -> Unit,
  onExecuteAction: (DependencyCheckItem) -> Unit,
  onRefreshChecks: () -> Unit,
  onSendFeedbackReport: () -> Unit,
  onOpenReferenceThread: () -> Unit
) {
  val studio = LocalStudioColors.current

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = horizontalPad)
      .testTag("dependencies_lazy_column"),
    contentPadding = PaddingValues(vertical = if (isCompactPhone) 16.dp else 24.dp),
    verticalArrangement = Arrangement.spacedBy(if (isCompactPhone) 18.dp else 24.dp)
  ) {
    if (includeOverviewPane) {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(studio.chrome)
            .padding(if (isCompactPhone) 16.dp else 20.dp),
          verticalArrangement = Arrangement.spacedBy(if (isCompactPhone) 14.dp else 18.dp)
        ) {
          StudioDeviceOverviewPane(
            uiState = uiState,
            isCompactPhone = isCompactPhone,
            onSelectFeature = onSelectFeature,
            onOpenReferenceThread = onOpenReferenceThread
          )
        }
      }
    }

    // Group 1: Dependencies Checklist (7 items in Android 16 / googlebook.studio grouped rows)
    item {
      Text(
        text = "Dependencies",
        modifier = Modifier.padding(horizontal = if (isCompactPhone) 12.dp else 20.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
      )
    }

    items(
      count = uiState.dependencies.size,
      key = { idx -> uiState.dependencies[idx].id.name }
    ) { index ->
      val item = uiState.dependencies[index]
      val shape = groupShape(index, uiState.dependencies.size)
      DependencySettingRow(
        item = item,
        shape = shape,
        isFocused = index == uiState.focusedDependencyIndex,
        isCompactPhone = isCompactPhone,
        onToggleCheckMark = {
          onFocusDependency(index)
          onToggleCheckMark(item.id)
        },
        onFocus = { onFocusDependency(index) },
        onExecuteAction = { onExecuteAction(item) }
      )
    }

    // Group 2: Companion Phone Dependency Check / Tip
    item {
      CompanionPhoneGroup(
        phoneReportState = uiState.phoneReportState,
        onRefreshCompanionScan = onRefreshChecks
      )
    }

    // Group 3: Troubleshooting & Feedback Report to Google
    item {
      TroubleshootingFeedbackGroup(
        onSendFeedbackReport = onSendFeedbackReport,
        onOpenReferenceThread = onOpenReferenceThread
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DependencySettingRow(
  item: DependencyCheckItem,
  shape: RoundedCornerShape,
  isFocused: Boolean,
  isCompactPhone: Boolean,
  onToggleCheckMark: () -> Unit,
  onFocus: () -> Unit,
  onExecuteAction: () -> Unit
) {
  val studio = LocalStudioColors.current
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()

  val bgColor = when {
    isFocused || isHovered -> MaterialTheme.colorScheme.surfaceContainerLow
    else -> studio.card
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(bgColor)
      .hoverable(interactionSource)
      .focusable()
      .clickable { onToggleCheckMark() }
      .padding(horizontal = if (isCompactPhone) 14.dp else 20.dp, vertical = 14.dp)
      .testTag("dependency_card_${item.id.name}"),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(if (isCompactPhone) 10.dp else 14.dp)
    ) {
      // Click-mark Checkbox on each dependency (Green Met checkmark box)
      Checkbox(
        checked = item.isChecked,
        onCheckedChange = { onToggleCheckMark() },
        colors = CheckboxDefaults.colors(
          checkedColor = studio.metGreen,
          checkmarkColor = studio.onMetGreen,
          uncheckedColor = MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
          .testTag("click_mark_${item.id.name}")
          .semantics {
            contentDescription = "Click-mark for ${item.title}"
          }
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = "${item.stepNumber}. ${item.title}",
          style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(580)),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = if (item.requirementSummary.isNotBlank()) {
            "${item.detectedStatus} · ${item.requirementSummary}"
          } else {
            item.detectedStatus
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Status & Action Column:
      // - When !item.isMet: show primary action button ("Update Apk", "Fix Wi-Fi", etc.)
      // - When item.isMet: show green "✓ Met" box, AND if item.hasUpdateAvailable is true,
      //   show an "Update Available" button directly underneath "Met".
      if (!item.isMet) {
        if (!isCompactPhone) {
          Button(
            onClick = {
              onFocus()
              onExecuteAction()
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.testTag("action_button_${item.id.name}")
          ) {
            Text(
              text = item.actionLabel,
              style = MaterialTheme.typography.labelMedium
            )
          }
        }
      } else {
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Green Met checkmark box
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(studio.metGreenContainer)
              .padding(horizontal = 12.dp, vertical = 6.dp)
              .testTag("met_badge_${item.id.name}")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = studio.onMetGreenContainer,
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = "Met",
                style = MaterialTheme.typography.labelMedium,
                color = studio.onMetGreenContainer
              )
            }
          }

          // Requirement 2: If an update is available, add a button under Met saying "Update Available"
          // even if the condition is met. If no update is available, leave Met as is.
          if (item.hasUpdateAvailable) {
            Button(
              onClick = {
                onFocus()
                onExecuteAction()
              },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
              ),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier.testTag("update_available_button_${item.id.name}")
            ) {
              Text(
                text = "Update Available",
                style = MaterialTheme.typography.labelMedium
              )
            }
          }
        }
      }
    }

    // Explanation when not met, plus callout for phone requirement (and full-width/aligned action button on compact phone)
    Column(
      modifier = Modifier.padding(start = if (isCompactPhone) 40.dp else 48.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      if (!item.isMet) {
        Text(
          text = item.whyNotOrFixInstruction,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.tertiary
        )
        if (isCompactPhone) {
          Button(
            onClick = {
              onFocus()
              onExecuteAction()
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.testTag("action_button_${item.id.name}")
          ) {
            Text(
              text = item.actionLabel,
              style = MaterialTheme.typography.labelMedium
            )
          }
        }
      }
      Text(
        text = item.phoneRequirementNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun CompanionPhoneGroup(
  phoneReportState: PhoneReportState,
  onRefreshCompanionScan: () -> Unit
) {
  val studio = LocalStudioColors.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("companion_phone_section"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = "Companion phone",
      modifier = Modifier.padding(horizontal = 20.dp),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary
    )

    when (phoneReportState) {
      is PhoneReportState.Connected -> {
        val rowsCount = phoneReportState.items.size + 1
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(groupShape(0, rowsCount))
              .background(studio.card)
              .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
              Column {
                Text(
                  text = phoneReportState.snapshot.phoneModel,
                  style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(580)),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Companion phone reported live status",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onRefreshCompanionScan)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("rescan_phone_button")
            ) {
              Text(
                text = "Refresh phone",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
              )
            }
          }

          phoneReportState.items.forEachIndexed { idx, phoneItem ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(groupShape(idx + 1, rowsCount))
                .background(studio.card)
                .padding(horizontal = 20.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = phoneItem.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight(560)),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "${phoneItem.statusText} · ${if (phoneItem.isMet) "Met" else "Not met"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (phoneItem.isMet) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.tertiary
                }
              )
            }
          }
        }
      }

      is PhoneReportState.Unreachable -> {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          // Row 1: Tip to run the same app on the phone
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(groupShape(0, 2))
              .background(studio.card)
              .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Icon(
              imageVector = Icons.Default.PhoneAndroid,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = phoneReportState.tipTitle,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(580)),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = phoneReportState.tipMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onRefreshCompanionScan)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("rescan_phone_button")
            ) {
              Text(
                text = "Check phone",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
              )
            }
          }

          // Row 2: Compact checklist of what the phone needs
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(groupShape(1, 2))
              .background(studio.card)
              .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = "What your phone also needs",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight(580)),
              color = MaterialTheme.colorScheme.onSurface
            )
            phoneReportState.phoneRequirementsChecklist.forEach { req ->
              Text(
                text = "• $req",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TroubleshootingFeedbackGroup(
  onSendFeedbackReport: () -> Unit,
  onOpenReferenceThread: () -> Unit
) {
  val studio = LocalStudioColors.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("google_feedback_card"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = "Still not working?",
      modifier = Modifier.padding(horizontal = 20.dp),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      // Row 1: Send bug / feedback report to Google
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 64.dp)
          .clip(groupShape(0, 2))
          .background(studio.card)
          .clickable(onClick = onSendFeedbackReport)
          .padding(horizontal = 20.dp, vertical = 14.dp)
          .testTag("send_feedback_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Text(
            text = "Send a bug to Google using feedback report",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(580)),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Copies your device & dependency diagnostics and opens Google Feedback Report",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
          Text(
            text = "Send Feedback Report",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary
          )
        }
      }

      // Row 2: Open community thread
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 58.dp)
          .clip(groupShape(1, 2))
          .background(studio.card)
          .clickable(onClick = onOpenReferenceThread)
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .testTag("bottom_support_thread_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Text(
            text = "Update on Better Together setup & Samsung Galaxy rollout",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight(560)),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "support.google.com/googlebook/thread/472325070",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
