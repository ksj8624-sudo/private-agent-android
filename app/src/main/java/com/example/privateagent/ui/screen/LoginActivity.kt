package com.example.privateagent.ui.screen

import android.content.Intent
import android.os.Bundle
import androidx.compose.material3.Button
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.privateagent.data.auth.AuthState
import com.example.privateagent.data.auth.SessionManager
import com.example.privateagent.ui.theme.PrivateAgentTheme
import com.example.privateagent.ui.viewmodel.AuthViewModel

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authViewModel: AuthViewModel by viewModels()

        enableEdgeToEdge()
        setContent {
            PrivateAgentTheme {
                val uiState = authViewModel.uiState
                val sessionState by SessionManager.authState.collectAsState()

                when (sessionState) {
                    AuthState.Initializing -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    AuthState.LoggedIn -> {
                        LaunchedEffect(Unit) {
                            moveToMain()
                        }
                    }

                    AuthState.LoggedOut -> {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            LoginScreen(
                                onLoginClick = { email, password ->
                                    authViewModel.login(email, password)
                                },
                                onErrorDismiss = {
                                    authViewModel.clearError()
                                },
                                isLoading = uiState.isLoading,
                                errorMessage = uiState.errorMessage,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                    }
                }
            }
        }

    }

    fun moveToMain() {
        val intent = Intent(this, MainActivity::class.java)
        this.startActivity(intent)
        finish()
    }
}

@Composable
fun LoginScreen(onLoginClick: (
        email: String,
        password: String
        ) -> Unit,
                onErrorDismiss: () -> Unit,
                isLoading: Boolean,
                errorMessage: String?,
                modifier: Modifier = Modifier
) {
    if (!errorMessage.isNullOrEmpty()) {
        AlertDialog(
            onDismissRequest = onErrorDismiss,
            title = {
                Text("로그인 오류")
                    },
            text = {
                Text(errorMessage)
                 },
            confirmButton = {
                TextButton(
                    onClick = onErrorDismiss
                ) {
                    Text("확인")
                }
            }
        )
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF15151B)),
        contentAlignment = Alignment.Center) {

        Card(
            modifier = Modifier.width(360.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Private AI Agent")

                Text("개인 AI 에이전트 관리 서비스")

                TextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = {Text("ID")},
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(value = password,
                    onValueChange = {password = it},
                    placeholder = {Text("Password")},
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(onClick = {
                    onLoginClick(email, password)
                },
                    enabled = !isLoading,
                    modifier = modifier.fillMaxWidth()
                ) {
                    Text("로그인")
                }
            }

        }
    }
}
