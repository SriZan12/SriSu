package com.srisu.srisu.features.auth.presentation.screen.profilesetup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srisu.srisu.components.RoundedPrimaryButtonCompo
import com.srisu.srisu.utils.ZodiacUtils
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.zodiac_bg

@Composable
fun ZodiacRevealScreen(
    zodiacSign: ZodiacUtils.ZodiacSign,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            RoundedPrimaryButtonCompo(
                modifier = modifier,
                title = "Next",
                enabled = true,
                onClick = onContinueClick
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = painterResource(Res.drawable.zodiac_bg),
                contentDescription = "Zodiac background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues = innerPadding)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {


                Text(
                    text = "Y O U R  Z O D I A C  S I G N",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )

                Image(
                    painter = painterResource(zodiacSign.logo),
                    contentDescription = "zodiac sign",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(120.dp)
                )

                Text(
                    text = zodiacSign.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )


                HorizontalDivider(
                    modifier = Modifier.width(300.dp),
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.24f)
                )


                Text(
                    text = zodiacSign.description,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 34.sp,
                    textAlign = TextAlign.Center
                )


                ZodiacTraitRow(
                    traits = zodiacSign.traits
                )

            }
        }
    }
}


@Composable
private fun ZodiacTraitChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.25f)
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Visible,
                maxLines = 1
            )
        }
    }
}


@Composable
private fun ZodiacTraitRow(
    traits: List<String>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 12.dp,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        traits.forEach { trait ->
            ZodiacTraitChip(
                text = trait,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
