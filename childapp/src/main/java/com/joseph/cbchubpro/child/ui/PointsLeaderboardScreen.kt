package com.joseph.cbchubpro.child.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LeaderboardViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _leaderboard = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val leaderboard = _leaderboard.asStateFlow()

    private val _userRank = MutableStateFlow<Int?>(null)
    val userRank = _userRank.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        fetchLeaderboard()
    }

    private fun fetchLeaderboard() {
        val uid = auth.currentUser?.uid
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = db.collection("users")
                    .whereEqualTo("role", "child")
                    .orderBy("pointsBalance", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().await()

                val entries = snapshot.documents.mapIndexed { index, doc ->
                    val entry = LeaderboardEntry(
                        uid = doc.id,
                        name = doc.getString("name") ?: "Student",
                        points = doc.getLong("pointsBalance") ?: 0
                    )
                    if (doc.id == uid) {
                        _userRank.value = index + 1
                    }
                    entry
                }
                _leaderboard.value = entries
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

data class LeaderboardEntry(val uid: String, val name: String, val points: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsLeaderboardScreen(
    onBack: () -> Unit,
    viewModel: LeaderboardViewModel = viewModel()
) {
    val leaderboard by viewModel.leaderboard.collectAsState()
    val userRank by viewModel.userRank.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hall of Fame", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
        ) {
            // Summary Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        ),
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("My Current Rank", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        Text(
                            text = if (userRank != null) "#$userRank" else "---",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Top Learners",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    itemsIndexed(leaderboard) { index, entry ->
                        LeaderboardRow(
                            rank = index + 1,
                            name = entry.name,
                            points = entry.points,
                            isCurrentUser = entry.uid == currentUserId
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardRow(rank: Int, name: String, points: Long, isCurrentUser: Boolean) {
    val bgColor = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer else Color.White
    val borderStroke = if (isCurrentUser) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = borderStroke
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = when (rank) {
                            1 -> Color(0xFFFFB300) // Gold
                            2 -> Color(0xFFB0BEC5) // Silver
                            3 -> Color(0xFF8D6E63) // Bronze
                            else -> Color(0xFFE0E0E0)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) Color.White else Color.Black
                )
            }

            Spacer(Modifier.width(16.dp))

            // Name
            Text(
                text = if (isCurrentUser) "$name (You)" else name,
                modifier = Modifier.weight(1f),
                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )

            // Points
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Stars,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = points.toString(),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
