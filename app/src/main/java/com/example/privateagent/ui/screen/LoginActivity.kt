package com.example.privateagent.ui.screen

import android.content.Intent
import android.os.Bundle
import androidx.compose.material3.Button
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.privateagent.ui.theme.PrivateAgentTheme

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrivateAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LoginScreen(
                        onLoginClick = { moveToMain() },
                        modifier = Modifier.padding(innerPadding)
                    )
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
fun LoginScreen(onLoginClick: () -> Unit, modifier: Modifier = Modifier) {
    var userId by remember { mutableStateOf("") }
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
                    value = userId,
                    onValueChange = { userId = it },
                    placeholder = {Text("ID")},
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(value = password,
                    onValueChange = {password = it},
                    placeholder = {Text("Password")},
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(onClick = onLoginClick) {
                    Text("로그인")
                }
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    PrivateAgentTheme {
        LoginScreen(
            onLoginClick = {}
        )
    }
}