package com.example.privateagent.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.privateagent.ui.viewmodel.HealthViewModel

@Composable
fun MainScreen (
    onReviewClick:() -> Unit,
    onPlanClick:() -> Unit,
    viewModel: HealthViewModel = viewModel<HealthViewModel>(),
    modifier: Modifier = Modifier
){
    Column(
        modifier = modifier
            .fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.checkServer()
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "서버 상태: $serverStatus"
            )

            Text(
                text = "Private AI Agent",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "개인 AI 에이전트 관리 서비스",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Card (
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MainMenuCard(
                    title = "Code Review",
                    description = "프로젝트 코드를 검토합니다.",
                    icon = Icons.Default.Description,
                    onClick = onReviewClick
                )

                MainMenuCard(
                    title = "Dev Plan",
                    description = "개발 계획 생성합니다.",
                    icon = Icons.Default.EditNote,
                    onClick = onPlanClick,
                )
            }
        }
    }
}

@Composable
fun MainMenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
