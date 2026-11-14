package io.github.ebziboi.delta.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Temporary: the old card name, until every screen moves to [GlassCard]. */
@Composable
fun DeltaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    GlassCard(modifier = modifier, content = content)
