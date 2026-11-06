package com.benjo.fboplus

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal fun fournisseurAppCheck(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
