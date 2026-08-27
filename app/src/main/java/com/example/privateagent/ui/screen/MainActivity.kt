package com.example.privateagent.ui.screen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.privateagent.data.auth.AuthState
import com.example.privateagent.data.auth.SessionManager
import com.example.privateagent.ui.theme.PrivateAgentTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrivateAgentTheme {
                val sessionState by SessionManager.authState.collectAsState()

                LaunchedEffect(sessionState) {
                    if (sessionState == AuthState.LoggedOut) {
                        moveToLogin()
                    }
                }

                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                            composable("main") {
                            MainScreen(
                                onReviewClick = {
                                    navController.navigate("review")
                                },
                                onPlanClick = {
                                    navController.navigate("plan")
                                }
                            )
                        }
                        composable("review") {
                            ReviewScreen()
                        }

                        composable ("plan") {
                            PlanScreen()
                        }
                    }

                }
            }
        }
    }

    private fun moveToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
