package com.srisu.srisu.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun StyledAnnotatedText(
    modifier: Modifier = Modifier,
    title: String,
    subTitle: String,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(color = Color.Black),
    subTitleStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black)
) {
    Text(
        text = buildAnnotatedString {
            withStyle(style = titleStyle.toSpanStyle()) {
                append(title)
            }
            append(" ") // Adds spacing
            withStyle(style = subTitleStyle.toSpanStyle()) {
                append(subTitle)
            }
        },
        modifier = modifier
    )
}

@Composable
fun HighlightedTextComponent(
    fullText: String,
    highlightedText: String,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
    highlightedTextStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary)
) {

    Text(
        text = buildAnnotatedString {
            val startIndex = fullText.indexOf(highlightedText)
            val endIndex = startIndex + highlightedText.length

            withStyle(style = textStyle.toSpanStyle()) {
                append(fullText.substring(0, startIndex))
            }

            withStyle(style = highlightedTextStyle.toSpanStyle()) {
                append(highlightedText)
            }

            withStyle(style = textStyle.toSpanStyle()) {
                append(fullText.substring(endIndex))
            }
        },
    )
}


@Composable
fun ErrorText(
    modifier: Modifier,
    text: String,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error)
) {
    Text(
        modifier = modifier,
        text = text,
        style = textStyle
    )
}

@Composable
fun ReadMoreText(
    modifier: Modifier,
    minLines: Int = 5,
    text: String,
    style: TextStyle,
    expandableTextStyle: TextStyle
) {

    var expandedState by remember { mutableStateOf(false) }
    var showReadMoreButtonState by remember { mutableStateOf(false) }
    val maxLines = if (expandedState) 200 else minLines

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
            onTextLayout = { textLayoutResult: TextLayoutResult ->
                if (textLayoutResult.lineCount > minLines - 1) {
                    if (textLayoutResult.isLineEllipsized(minLines - 1)) showReadMoreButtonState =
                        true
                }
            }
        )
        if (showReadMoreButtonState) {

            Text(
                text = if (expandedState) "Read Less" else "Read More...",
                modifier = Modifier.padding(top = 4.dp).wrapContentWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    expandedState = !expandedState
                },
                style = expandableTextStyle

            )


        }

    }
}