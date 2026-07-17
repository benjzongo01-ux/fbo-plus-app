package com.benjo.fboplus.data

data class Product(
    val id: Int,
    val ref: String,
    val nameFr: String,
    val categorie: String,
    val prixPublic: Int,
    val cc: Double,
    val pathologies: List<String> = emptyList(),
    val imageRes: Int = 0,
    val description: String = ""
)