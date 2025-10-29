package com.inncome.scanner

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.inncome.scanner.config.RetrofitClient
import com.inncome.scanner.data.request.LoginRequest
import com.inncome.scanner.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        RetrofitClient.init(applicationContext)

        val tokenManager = RetrofitClient.getTokenManager()
        if (tokenManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val identifier = binding.etIdentifier.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInputs(identifier, password)) {
                login(identifier, password)
            }
        }
    }

    private fun validateInputs(identifier: String, password: String): Boolean {
        if (identifier.isEmpty()) {
            binding.etIdentifier.error = "Ingrese email o DNI"
            return false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Ingrese contraseña"
            return false
        }

        return true
    }

    private fun login(identifier: String, password: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getAuthApi().login(
                    LoginRequest(identifier, password)
                )

                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!

                    val tokenManager = RetrofitClient.getTokenManager()

                    tokenManager.saveToken(
                        loginResponse.accessToken,
                        loginResponse.refreshToken,
                        loginResponse.expiresAt
                    )

                    Toast.makeText(
                        this@LoginActivity,
                        "✓ Login exitoso",
                        Toast.LENGTH_SHORT
                    ).show()

                    navigateToMain()
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "Credenciales incorrectas"
                        404 -> "Usuario no encontrado"
                        else -> "Error al iniciar sesión: ${response.code()}"
                    }

                    Toast.makeText(
                        this@LoginActivity,
                        errorMsg,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en login", e)
                Toast.makeText(
                    this@LoginActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true
            }
        }
    }
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}