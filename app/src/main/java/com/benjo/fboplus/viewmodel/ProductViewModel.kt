package com.benjo.fboplus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benjo.fboplus.data.Product
import com.benjo.fboplus.data.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProductViewModel : ViewModel() {

    private val _products = MutableStateFlow<List<Product>>(ProductRepository.allProducts)
    val products: StateFlow<List<Product>> = _products

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategorie = MutableStateFlow("Tous")
    val selectedCategorie: StateFlow<String> = _selectedCategorie

    init {
        viewModelScope.launch {
            ProductRepository.catalogue.collect {
                filterProducts(_searchQuery.value, _selectedCategorie.value)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        filterProducts(query, _selectedCategorie.value)
    }

    fun setCategorie(categorie: String) {
        _selectedCategorie.value = categorie
        filterProducts(_searchQuery.value, categorie)
    }

    private fun filterProducts(query: String, categorie: String) {
        var result = ProductRepository.allProducts
        if (categorie != "Tous") {
            result = result.filter { it.categorie == categorie }
        }
        if (query.isNotEmpty()) {
            result = result.filter {
                it.nameFr.contains(query, ignoreCase = true) ||
                        it.ref.contains(query, ignoreCase = true) ||
                        it.pathologies.any { p -> p.contains(query, ignoreCase = true) }
            }
        }
        _products.value = result
    }

    fun getCategories(): List<String> {
        return listOf("Tous") + ProductRepository.allProducts.map { it.categorie }.distinct()
    }
}