package com.example.privateagent.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.example.privateagent.ui.component.AiWorkspaceSelector
import com.example.privateagent.ui.theme.PrivateAgentTheme
import com.example.privateagent.ui.viewmodel.ReviewViewModel

@Composable
fun ReviewScreen (
    modifier: Modifier = Modifier,
    reviewViewModel: ReviewViewModel = viewModel()
) {
    val uiState = reviewViewModel.uiState

    var selectedWorkspace by remember { mutableStateOf("b") }
    var contents by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AiRequestHeader("Code Review", "프로젝트 코드의 문제점과 개선 사항을 검토합니다.")

        AiWorkspaceSelector (
            selectedWorkspace = selectedWorkspace,
            onWorkspaceSelected = {
                selectedWorkspace = it
            }
        )

        AiInputField (
            contents = contents,
            label = "리뷰 요청",
            placeholder = "예: 에러 처리와 중복 코드를 중점적으로 검토해줘.",
            onContentsChange = {
                contents = it
            })


        Button (
            onClick = {
                reviewViewModel.requestReview(
                    workspace = selectedWorkspace,
                    contents = contents.trim()
                )
            },
            enabled = !uiState.isLoading && contents.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (uiState.isLoading) {
                    "리뷰 요청 중..."
                } else {
                    "리뷰 시작"
                }
            )
        }

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
fun ReviewScreenPrev() {
    PrivateAgentTheme {
        ReviewScreen()
    }
}