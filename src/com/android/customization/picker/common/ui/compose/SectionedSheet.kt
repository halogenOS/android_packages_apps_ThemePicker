// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.common.ui.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SelectionGap = 3.dp
private val SelectionRingWidth = 3.dp
private val RingInset = SelectionGap + SelectionRingWidth

// Design choice: the grid gets at most half of the screen height, so the previews above the sheet
// remain visible. The previews take the space the sheet leaves, so their size cannot set it.
private const val GRID_SCREEN_HEIGHT_FRACTION = 0.5f

/**
 * A floating sheet with a grid of options in titled sections. The [header] above the grid shows the
 * previewed option and the [footer] below it, a [SectionedSheetFooter], one more control of the
 * sheet. Each row holds as many [LabeledOption]s of [optionSize] as fit the width.
 */
@Composable
internal fun SectionedSheet(
    optionSize: Dp,
    header: (@Composable () -> Unit)?,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    sections: LazyGridScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val gridMaxHeight = LocalConfiguration.current.screenHeightDp.dp * GRID_SCREEN_HEIGHT_FRACTION
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .drawBehind { drawRect(colorScheme.surfaceBright) }
    ) {
        if (header != null) {
            header()
            HorizontalDivider(color = colorScheme.outlineVariant)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = ringSizeOf(optionSize)),
            modifier = Modifier.fillMaxWidth().heightIn(max = gridMaxHeight),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = sections,
        )
        HorizontalDivider(color = colorScheme.outlineVariant)
        footer()
    }
}

@Composable
internal fun SectionedSheetFooter(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Header of a [SectionedSheet]: a sample of the previewed option, its name and its details. */
@Composable
internal fun SectionedSheetHeader(
    name: String,
    details: String?,
    sample: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        sample()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (details != null) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

/** Adds the title of a section of a [SectionedSheet], spanning the whole row of the grid. */
internal fun LazyGridScope.sectionTitle(key: Any, title: @Composable () -> String) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title(),
            modifier = Modifier.padding(start = 6.dp, top = 14.dp).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * An option of [size] with its label below. The selected one gets a ring around it, so it stands
 * out whatever it shows. The option is clipped to corners of [cornerRadius], and the ring follows
 * them.
 */
@Composable
internal fun LabeledOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val ringSize = ringSizeOf(size)
    Column(
        modifier =
            modifier.selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier =
                Modifier.size(ringSize)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = SelectionRingWidth,
                                color = colorScheme.onSurface,
                                shape = RoundedCornerShape(cornerRadius + RingInset),
                            )
                        } else {
                            Modifier
                        }
                    ),
            contentAlignment = Alignment.Center,
        ) {
            content(Modifier.size(size).clip(RoundedCornerShape(cornerRadius)))
        }
        Text(
            text = label,
            modifier = Modifier.widthIn(max = ringSize),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Width of a [LabeledOption] of [size], which its ring sets. */
private fun ringSizeOf(size: Dp): Dp = size + RingInset * 2
