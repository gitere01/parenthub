package com.joseph.cbchubpro.ui.kidscontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun KidsControlParentScreen(
    parentId: String,
    onManageClick: (String) -> Unit,
    viewModel: KidsControlViewModel = viewModel()
) {
    val childrenWithCodes by viewModel.childrenWithCodes.collectAsState()
    val pairingInputState by viewModel.pairingInputState.collectAsState()

    LaunchedEffect(parentId) {
        if (parentId.isNotEmpty()) {
            viewModel.listenForChildrenAndCodes(parentId)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            AddChildCard(
                inputState = pairingInputState,
                onChildNameChange = { viewModel.onChildNameChange(it) },
                onGenerateCode = { viewModel.generateNewChildAndCode(parentId) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text("Paired Children", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(childrenWithCodes, key = { it.id }) { child ->
            ChildListItem(
                child = child,
                onManageClick = onManageClick,
                onDeleteClick = { viewModel.deleteChild(parentId, child) }
            )
        }
    }
}

@Composable
private fun AddChildCard(
    inputState: PairingInputState,
    onChildNameChange: (String) -> Unit,
    onGenerateCode: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Add New Child")
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = inputState.childName,
                onValueChange = onChildNameChange,
                label = { Text("Child's Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !inputState.isGeneratingCode
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onGenerateCode,
                enabled = inputState.childName.isNotBlank() && !inputState.isGeneratingCode
            ) {
                if (inputState.isGeneratingCode) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Create Child & Pairing Code")
                }
            }
        }
    }
}

@Composable
private fun ChildListItem(
    child: ChildWithCode,
    onManageClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(child.name, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    OnlineStatusIndicator(isOnline = child.online)
                }
                child.code?.takeIf { it.isNotBlank() }?.let { code ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Code: $code")
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Pairing Code", code))
                        }) {
                            Text("Copy")
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onManageClick(child.id) }) {
                    Text("Manage")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Child")
                }
            }
        }
    }
}

@Composable
private fun OnlineStatusIndicator(isOnline: Boolean) {
    val color = if (isOnline) Color.Green else Color.Gray
    val text = if (isOnline) "Online" else "Offline"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.size(8.dp)) { 
            drawCircle(color = color)
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
