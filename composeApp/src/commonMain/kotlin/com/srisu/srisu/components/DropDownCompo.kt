package com.srisu.srisu.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.utils.getCountryFlagFromAssets
import org.jetbrains.compose.resources.painterResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.country_flag


@Composable
fun CountryCodeDropDown(
    modifier: Modifier = Modifier,
    selectedCountryPrefix: String,
    selectedCountryCode: String,
    onClick: () -> Unit
) {

    val flag = getCountryFlagFromAssets(
        countryCode = selectedCountryCode
    )

    Card(
        modifier = modifier
            .wrapContentWidth()
            .height(54.dp),
        onClick = {
            onClick()

        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = CenterVertically
        ) {

            if (flag == null) {
                Image(
                    painter = painterResource(Res.drawable.country_flag),
                    contentDescription = "country_flag",
                    modifier = Modifier
                        .size(20.dp)
                )
            } else {
                Image(
                    bitmap = flag,
                    contentDescription = "flag",
                    modifier = Modifier
                        .size(20.dp)
                )
            }


            Text(
                text = selectedCountryPrefix,
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium
            )


            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
            )
        }
    }


}
