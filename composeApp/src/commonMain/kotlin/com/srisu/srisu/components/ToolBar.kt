package com.srisu.srisu.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.filter_icon

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun PrimaryToolBar(
    title: String,
    onNavigate: () -> Unit
) {

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
        navigationIcon = {
            IconButton(
                onClick = {
                    onNavigate()
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "navigate back icon",
                    tint = Color.White
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransparentToolBar(
    title: String,
    onClickAction: () -> Unit = {}
) {

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

        },
        actions = {
            IconButton(
                onClick = {
                    onClickAction()
                }
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.filter_icon),
                    contentDescription = "Filter Icon",
                )
            }
        }
    )

}