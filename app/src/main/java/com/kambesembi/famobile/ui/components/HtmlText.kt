package com.kambesembi.famobile.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.kambesembi.famobile.data.parse.FaHtml

/** FA's description or comment HTML as native text, with tappable links. */
@Composable
fun HtmlText(
    html: String,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = LocalContentColor.current,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val open by rememberUpdatedState(onLink)
    val (text, icons) = remember(html, linkColor) {
        val listener = LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.let { open(it.url) } }
        val prepared = FaHtml.prepareWithIcons(html)
        val rendered = AnnotatedString.fromHtml(
            prepared.html,
            linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            linkInteractionListener = listener,
        )
        aligned(withIcons(rendered)) to prepared.icons
    }
    val inline = remember(icons) {
        icons.withIndex().associate { (i, url) ->
            "icon$i" to InlineTextContent(Placeholder(1.5.em, 1.5.em, PlaceholderVerticalAlign.TextCenter)) {
                AsyncImage(url, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)))
            }
        }
    }
    Text(text, modifier = modifier, style = style, color = color, inlineContent = inline)
}

/** Swaps FaHtml's icon markers for inline content "icon<n>", keeping the links around them. */
private fun withIcons(text: AnnotatedString): AnnotatedString {
    if (text.text.indexOf(FaHtml.ICON_START) < 0) return text
    return buildAnnotatedString {
        var at = 0
        while (true) {
            val start = text.text.indexOf(FaHtml.ICON_START, at)
            val end = if (start < 0) -1 else text.text.indexOf(FaHtml.ICON_END, start)
            if (end < 0) break
            append(text.subSequence(at, start))
            val link = text.getLinkAnnotations(start, end + 1).firstOrNull()?.item
            if (link != null) pushLink(link)
            appendInlineContent("icon" + text.text.substring(start + 1, end), "@")
            if (link != null) pop()
            at = end + 1
        }
        append(text.subSequence(at, text.length))
    }
}

/** FA's [center] and [right] blocks: FaHtml's markers become paragraphs aligned that way. */
private fun aligned(text: AnnotatedString): AnnotatedString {
    val marks = mapOf(FaHtml.CENTER_START to (FaHtml.CENTER_END to TextAlign.Center), FaHtml.RIGHT_START to (FaHtml.RIGHT_END to TextAlign.End))
    val ends = setOf(FaHtml.CENTER_END, FaHtml.RIGHT_END)
    if (text.text.none { it in marks || it in ends }) return text
    return buildAnnotatedString {
        var at = 0
        while (at < text.length) {
            val start = text.text.indexOfFirst(at) { it in marks || it in ends }
            if (start < 0) break
            append(text.subSequence(at, start))
            val (endMark, align) = marks[text.text[start]] ?: run {
                // An end without its start: dropped.
                at = start + 1
                null
            } ?: continue
            val end = text.text.indexOf(endMark, start + 1).let { if (it < 0) text.length else it }
            // Line breaks at the block's edges would only add empty lines around it.
            val inner = text.subSequence(start + 1, end).let { trimBreaks(it) }
            if (inner.isNotEmpty()) {
                // A paragraph of its own, aligned.
                withStyle(ParagraphStyle(textAlign = align)) { append(inner) }
            }
            at = (end + 1).coerceAtMost(text.length)
            // The break after the block likewise.
            if (at < text.length && text.text[at] == '\n') at++
        }
        if (at < text.length) append(text.subSequence(at, text.length))
    }
}

private fun String.indexOfFirst(from: Int, test: (Char) -> Boolean): Int {
    for (i in from until length) if (test(this[i])) return i
    return -1
}

private fun trimBreaks(text: AnnotatedString): AnnotatedString {
    var start = 0
    var end = text.length
    while (start < end && text.text[start] == '\n') start++
    while (end > start && text.text[end - 1] == '\n') end--
    return text.subSequence(start, end)
}
