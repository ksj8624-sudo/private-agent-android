package com.example.privateagent.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AiRequestHeader(title: String, description: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun AiWorkspaceSelector(
    selectedWorkspace: String,
    onWorkspaceSelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "프로젝트",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "backend" to "Backend",
                "server" to "Server",
                "front" to "Front"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = selectedWorkspace == value,
                    onClick = {
                        onWorkspaceSelected(value)
                    },
                    label = {
                        Text(label)
                    }
                )
            }
        }
    }
}

@Composable
fun AiInputField(
    contents: String,
    label: String,
    placeholder: String,
    onContentsChange: (String) -> Unit
) {
    OutlinedTextField(
        value = contents,
        onValueChange = onContentsChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(label)
        },
        placeholder = {
            Text(placeholder)
        },
        minLines = 6
    )
}

@Composable
fun AiResultSection(
    isLoading: Boolean,
    result: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    title: String = "결과"
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        }

        errorMessage?.let { message ->
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "오류",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (result.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            val scrollState = rememberScrollState()

            Text(
                text = result,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}