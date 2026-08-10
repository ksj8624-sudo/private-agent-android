package com.example.privateagent.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.privateagent.ui.component.AiInputField
import com.example.privateagent.ui.component.AiRequestHeader
import com.example.privateagent.ui.component.AiResultSection
import com.example.privateagent.ui.theme.PrivateAgentTheme
import com.example.privateagent.ui.viewmodel.PlanViewModel

@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    planViewModel: PlanViewModel = viewModel()
) {
    val uiState = planViewModel.uiState

    var contents by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AiRequestHeader("프로젝트 계획", "프로젝트의 계획을 작성합니다.")

        AiInputField (
            contents = contents,
            label = "프로젝트 계획 요청",
            placeholder = "중점적으로 검토할 내용을 입력하세요.",
            onContentsChange = {
                contents = it
            })

        Button (
            onClick = {
                planViewModel.requestPlan(
                    contents.trim()
                )
            },
            enabled = !uiState.isLoading && contents.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (uiState.isLoading) {
                    "계획 요청 중..."
                } else {
                    "계획 요청 시작"
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AiResultSection(
            isLoading = uiState.isLoading,
            result = uiState.result,
            errorMessage = uiState.errorMessage,
            title = "리뷰 결과",
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PlanScreenPrev() {
    PrivateAgentTheme {
        PlanScreen()
    }
}