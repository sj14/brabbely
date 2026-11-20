package io.github.sj14.brabbely.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.sj14.brabbely.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }
