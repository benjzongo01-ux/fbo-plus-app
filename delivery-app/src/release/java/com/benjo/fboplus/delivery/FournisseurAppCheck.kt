package com.benjo.fboplus.delivery

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal fun fournisseurAppCheck(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
