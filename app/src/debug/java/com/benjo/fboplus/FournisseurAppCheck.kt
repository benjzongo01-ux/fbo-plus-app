package com.benjo.fboplus

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal fun fournisseurAppCheck(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
