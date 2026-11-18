package io.github.sj14.lauscher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.sj14.lauscher.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }
