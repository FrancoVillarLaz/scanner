package com.inncome.scanner.config

// TokenAuthenticator.kt
import com.inncome.scanner.data.request.RefreshRequest
import com.inncome.scanner.service.AuthApi
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import kotlinx.coroutines.runBlocking

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code == 401) {
            val refreshToken = tokenManager.getRefreshToken()

            if (refreshToken == null) {
                tokenManager.clearTokens()
                return null
            }

            val newTokenResponse = runBlocking {
                try {
                    authApi.refreshToken(RefreshRequest(refreshToken))
                } catch (e: Exception) {
                    tokenManager.clearTokens()
                    null
                }
            }

            // 3. Procesar la respuesta del refresh
            if (newTokenResponse?.isSuccessful == true) {
                val tokenBody = newTokenResponse.body()
                if (tokenBody != null) {
                    // Guardar los nuevos tokens
                    tokenManager.saveToken(
                        tokenBody.accessToken,
                        tokenBody.refreshToken,
                        // Asume que el servidor envía el tiempo de expiración
                        System.currentTimeMillis() + (tokenBody.expiresAt* 1000)
                    )

                    return response.request.newBuilder()
                        .header("Authorization", "Bearer ${tokenBody.accessToken}")
                        .build()
                }
            }

            // Si el refresh falló o no dio un cuerpo válido
            tokenManager.clearTokens()
            return null
        }

        return null
    }
}