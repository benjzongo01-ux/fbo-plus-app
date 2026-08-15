package com.benjo.fboplus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benjo.fboplus.auth.SecureTokenStorage
import com.benjo.fboplus.data.network.ApiClient
import com.benjo.fboplus.data.network.AuthApi
import com.benjo.fboplus.data.network.PinRequest
import com.benjo.fboplus.data.network.RegisterRequest
import com.benjo.fboplus.data.network.ResendCodeRequest
import com.benjo.fboplus.data.network.VerifyEmailRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class CodeSent(val email: String) : AuthUiState()
    data class NeedsPinCreation(val token: String) : AuthUiState()
    data class NeedsPinEntry(val token: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiClient.retrofit.create(AuthApi::class.java)
    private val tokenStorage = SecureTokenStorage(application)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun isLoggedIn(): Boolean = tokenStorage.hasToken()

    /** Inscription : nouveau compte (email + téléphone, sans mot de passe) */
    fun register(email: String, phone: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = api.register(RegisterRequest(email, phone))
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    when {
                        body.token != null -> _uiState.value = AuthUiState.NeedsPinEntry(body.token)
                        body.message != null -> _uiState.value = AuthUiState.CodeSent(email)
                        else -> _uiState.value = AuthUiState.Error("Réponse inattendue du serveur.")
                    }
                } else {
                    _uiState.value = AuthUiState.Error("Inscription impossible. Vérifiez vos informations.")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erreur réseau : impossible de contacter le serveur.")
            }
        }
    }

    /** Connexion sur un nouvel appareil : demande d'un code pour un compte existant */
    fun requestLoginCode(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = api.resendCode(ResendCodeRequest(email))
                if (response.isSuccessful) {
                    _uiState.value = AuthUiState.CodeSent(email)
                } else if (response.code() == 404) {
                    _uiState.value = AuthUiState.Error("Aucun compte trouvé avec cet email.")
                } else {
                    _uiState.value = AuthUiState.Error("Échec de l'envoi du code.")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erreur réseau : impossible de contacter le serveur.")
            }
        }
    }

    /** Vérification du code reçu par email (inscription ET connexion nouvel appareil) */
    fun verifyCode(email: String, code: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = api.verifyEmail(VerifyEmailRequest(email, code))
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.value = if (body.hasPinSet) {
                        AuthUiState.NeedsPinEntry(body.token)
                    } else {
                        AuthUiState.NeedsPinCreation(body.token)
                    }
                } else {
                    _uiState.value = AuthUiState.Error("Code incorrect.")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erreur réseau : impossible de contacter le serveur.")
            }
        }
    }

    fun resendCode(email: String) {
        viewModelScope.launch {
            try {
                api.resendCode(ResendCodeRequest(email))
            } catch (e: Exception) {
                // Échec silencieux, l'utilisateur peut réessayer
            }
        }
    }

    /** Première définition du PIN (nouveau compte) */
    fun createPin(token: String, pin: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = api.setPin("Bearer $token", PinRequest(pin))
                if (response.isSuccessful) {
                    tokenStorage.saveToken(token)
                    tokenStorage.savePin(pin)
                    _uiState.value = AuthUiState.Success
                } else {
                    _uiState.value = AuthUiState.Error("Impossible d'enregistrer le PIN.")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erreur réseau : impossible de contacter le serveur.")
            }
        }
    }

    /** Saisie du PIN existant (compte déjà créé, nouvel appareil) */
    fun loginWithPin(token: String, pin: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = api.loginPin("Bearer $token", PinRequest(pin))
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    tokenStorage.saveToken(body.token)
                    tokenStorage.savePin(pin)
                    _uiState.value = AuthUiState.Success
                } else {
                    _uiState.value = AuthUiState.Error("PIN incorrect.")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erreur réseau : impossible de contacter le serveur.")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}