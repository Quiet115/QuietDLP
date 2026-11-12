package com.quietdlp.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.quietdlp.app.data.NoteBlock
import com.quietdlp.app.data.NoteSpan
import com.quietdlp.app.data.NoteStyle
import com.quietdlp.app.data.ReleaseNotes

/**
 * Las notas de una versión, sin Markdown crudo: títulos, negrita, cursiva, código, listas y enlaces.
 * La interpretación está en `data/ReleaseNotes.kt`; aquí solo se dibuja. Las demás apps copian los dos archivos.
 */
@Composable
fun ReleaseNotesText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { ReleaseNotes.parse(markdown) }
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant

    fun annotated(spans: List<NoteSpan>): AnnotatedString = buildAnnotatedString {
        for (span in spans) {
            val format = when (span.style) {
                NoteStyle.PLAIN -> SpanStyle()
                NoteStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                NoteStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                NoteStyle.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                NoteStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
            }
            val url = span.url
            if (url == null) {
                withStyle(format) { append(span.text) }
            } else {
                val open = LinkInteractionListener { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)), open)) {
                    withStyle(format) { append(span.text) }
                }
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is NoteBlock.Heading -> Text(
                    annotated(block.spans),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.labelLarge
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is NoteBlock.Paragraph -> Text(annotated(block.spans), style = MaterialTheme.typography.bodyMedium)
                is NoteBlock.Item -> Row(Modifier.padding(start = (12 * block.depth).dp)) {
                    Text(block.marker, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(22.dp))
                    Text(annotated(block.spans), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                is NoteBlock.Code -> Surface(shape = RoundedCornerShape(8.dp), color = codeBackground, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                NoteBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
