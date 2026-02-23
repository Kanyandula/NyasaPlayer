package com.example.nyasaplayer.data.api

import com.google.firebase.auth.FirebaseUser

// TODO: Phase 3 — replace FirebaseUser with domain type
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
