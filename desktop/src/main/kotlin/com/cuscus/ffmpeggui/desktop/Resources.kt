package com.cuscus.ffmpeggui.desktop

import androidx.compose.runtime.Composable
import java.util.Locale

@Composable
fun stringResource(id: String, vararg args: Any): String =
    if (args.isEmpty()) id else String.format(Locale.US, id, *args)
