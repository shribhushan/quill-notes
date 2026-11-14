package com.quillnotes.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatIndentDecrease
import androidx.compose.material.icons.rounded.FormatIndentIncrease
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quillnotes.R
import com.quillnotes.text.Checklist
import com.quillnotes.text.LineKind
import com.quillnotes.text.Markdown
import com.quillnotes.text.MarkdownEditing
import com.quillnotes.text.MdSpan
import com.quillnotes.text.SpanType
import com.quillnotes.text.TextState
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * Holds the note body's editing state: the text with its selection, which the formatting toolbar
 * and the voice/scan inserts act on. The note's canonical text still lives in the view-model;
 * this mirrors it and reports every change back through the `emit` callbacks.
 */
@Stable
class MarkdownFieldState(initialText: String = "") {
    var value by mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
        private set
    var hasFocus by mutableStateOf(false)
    internal var layout: TextLayoutResult? = null

    val text: String get() = value.text

    /** Adopts [text] when it differs from what the field shows (undo, redo, loading a note). */
    fun syncFrom(text: String) {
        if (text == value.text) return
        val sel = value.selection
        value = TextFieldValue(text, TextRange(sel.start.coerceAtMost(text.length), sel.end.coerceAtMost(text.length)))
    }

    /** A change typed by the user; lists continue on Enter and markers delete whole. */
    fun onUserChange(new: TextFieldValue, emit: (String) -> Unit) {
        val old = value
        var result = new
        if (new.text != old.text) {
            val adjusted = MarkdownEditing.afterEdit(old.toState(), new.toState())
            if (adjusted.text != new.text || adjusted.selStart != new.selection.start || adjusted.selEnd != new.selection.end) {
                result = adjusted.toValue()
            }
        }
        value = result
        if (result.text != old.text) emit(result.text)
    }

    /** Applies a toolbar action. */
    fun edit(emit: (String) -> Unit, op: (TextState) -> TextState) {
        val before = value.text
        val result = op(value.toState())
        value = result.toValue()
        if (result.text != before) emit(result.text)
    }

    /** Inserts text at the caret (dictation, scanned text), separated from what is before it. */
    fun insertAtCaret(text: String, emit: (String) -> Unit, newLine: Boolean = false) {
        val s = value.toState()
        val before = s.text.substring(0, s.start)
        val separator = when {
            before.isEmpty() -> ""
            newLine && !before.endsWith("\n") -> "\n"
            !newLine && !before.last().isWhitespace() -> " "
            else -> ""
        }
        val r = MarkdownEditing.replaceRange(s, s.start, s.end, separator + text)
        value = r.toValue()
        emit(r.text)
    }

    /** The link the caret is inside, if any (for editing it from the toolbar). */
    fun linkAtCaret(): MdSpan? = Markdown.spans(value.text)
        .firstOrNull { it.type == SpanType.LINK && value.selection.start in it.start..it.end }

    fun selectedText(): String = value.text.substring(value.selection.min.coerceAtLeast(0), value.selection.max.coerceAtMost(value.text.length))

    /** Ticks a checklist line and reorders the list. */
    fun toggleChecklist(lineIndex: Int, emit: (String) -> Unit) {
        val updated = Checklist.toggle(value.text, lineIndex)
        if (updated == value.text) return
        val sel = value.selection
        value = TextFieldValue(updated, TextRange(sel.start.coerceAtMost(updated.length), sel.end.coerceAtMost(updated.length)))
        emit(updated)
    }

    /** The checklist line whose checkbox was tapped at [position], or null if the tap was elsewhere. */
    internal fun checklistLineAt(position: Offset, density: Density): Int? {
        val layout = layout ?: return null
        val text = value.text
        val offset = layout.getOffsetForPosition(position).coerceIn(0, text.length)
        val line = Markdown.lineAt(Markdown.lines(text), offset)
        if (line.kind != LineKind.CHECK) return null
        val marker = line.markerStart
        val row = layout.getLineForOffset(marker)
        if (position.y < layout.getLineTop(row) || position.y > layout.getLineBottom(row)) return null
        val glyph = layout.getBoundingBox(marker)
        val left = glyph.left - with(density) { 14.dp.toPx() }
        val right = layout.getHorizontalPosition(marker + 1, true) + with(density) { 22.dp.toPx() }
        return if (position.x in left..right) line.index else null
    }

    private fun TextFieldValue.toState() = TextState(text, selection.start, selection.end)

    private fun TextState.toValue() =
        TextFieldValue(text, TextRange(selStart.coerceIn(0, text.length), selEnd.coerceIn(0, text.length)))
}

/** The note body: Markdown shown formatted as it is typed, with tappable checkboxes. */
@Composable
fun MarkdownField(
    state: MarkdownFieldState,
    text: String,
    onTextChange: (String) -> Unit,
    ink: Color,
    accent: Color,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 360.dp
) {
    LaunchedEffect(text) { state.syncFrom(text) }
    val transformation = remember(ink, accent) { MarkdownTransformation(ink, accent) }
    BasicTextField(
        value = state.value,
        onValueChange = { state.onUserChange(it, onTextChange) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink.copy(alpha = 0.88f), lineHeight = 27.sp),
        cursorBrush = SolidColor(accent),
        visualTransformation = transformation,
        onTextLayout = { state.layout = it },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .onFocusChanged { state.hasFocus = it.isFocused }
            .checklistTaps(state) { line -> state.toggleChecklist(line, onTextChange) },
        decorationBox = { inner ->
            Box {
                if (state.value.text.isEmpty()) {
                    Text(text = placeholder, style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = 0.38f))
                }
                inner()
            }
        }
    )
}

/**
 * Watches taps on the text field without taking part in them, and claims one only when it lands
 * on a checkbox, so a tap anywhere else still places the cursor as usual.
 */
private fun Modifier.checklistTaps(state: MarkdownFieldState, onToggle: (lineIndex: Int) -> Unit): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
            if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
            val line = state.checklistLineAt(down.position, this) ?: return@awaitEachGesture
            down.consume()
            up.consume()
            onToggle(line)
        }
    }

/**
 * Styles Markdown in place. The text is never changed, only drawn differently, one character for
 * one character, so the cursor and the IME behave exactly as with plain text: markers are dimmed,
 * `- [ ]` is drawn as a box, `- ` as a bullet.
 */
class MarkdownTransformation(private val ink: Color, private val accent: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val chars = raw.toCharArray()
        val styles = ArrayList<AnnotatedString.Range<SpanStyle>>()
        fun add(start: Int, end: Int, style: SpanStyle) {
            if (end > start) styles += AnnotatedString.Range(style, start, end)
        }
        val dim = SpanStyle(color = ink.copy(alpha = 0.32f))

        for (line in Markdown.lines(raw)) {
            when (line.kind) {
                LineKind.CHECK -> {
                    val m = line.markerStart
                    chars[m] = if (line.checked) '☑' else '☐'
                    // The rest of "- [ ] " becomes zero-width so the box sits right before the text.
                    for (i in 1..4) chars[m + i] = '​'
                    add(m, m + 1, SpanStyle(color = if (line.checked) accent else ink.copy(alpha = 0.72f), fontSize = 22.sp))
                    if (line.checked) {
                        add(line.contentStart, line.end, SpanStyle(textDecoration = TextDecoration.LineThrough, color = ink.copy(alpha = 0.45f)))
                    }
                }
                LineKind.BULLET -> {
                    chars[line.markerStart] = '•'
                    add(line.markerStart, line.markerStart + 1, SpanStyle(color = accent, fontWeight = FontWeight.Bold))
                }
                LineKind.NUMBERED -> add(line.markerStart, line.contentStart, SpanStyle(color = accent, fontWeight = FontWeight.SemiBold))
                LineKind.HEADING -> {
                    val size = when (line.level) { 1 -> 24.sp; 2 -> 21.sp; else -> 19.sp }
                    add(line.start, line.end, SpanStyle(fontSize = size, fontWeight = FontWeight.SemiBold))
                    add(line.start, line.contentStart, dim)
                }
                LineKind.QUOTE -> {
                    add(line.start, line.end, SpanStyle(fontStyle = FontStyle.Italic, color = ink.copy(alpha = 0.7f)))
                    add(line.start, line.contentStart, dim)
                }
                LineKind.PLAIN -> Unit
            }
        }

        for (s in Markdown.spans(raw)) {
            val content = s.contentStart to s.contentEnd
            when (s.type) {
                SpanType.BOLD -> add(content.first, content.second, SpanStyle(fontWeight = FontWeight.Bold))
                SpanType.ITALIC -> add(content.first, content.second, SpanStyle(fontStyle = FontStyle.Italic))
                SpanType.STRIKE -> add(content.first, content.second, SpanStyle(textDecoration = TextDecoration.LineThrough))
                SpanType.CODE -> add(content.first, content.second, SpanStyle(fontFamily = FontFamily.Monospace, background = ink.copy(alpha = 0.08f)))
                SpanType.LINK, SpanType.WIKILINK ->
                    add(content.first, content.second, SpanStyle(color = accent, textDecoration = TextDecoration.Underline))
                SpanType.URL -> add(s.start, s.end, SpanStyle(color = accent, textDecoration = TextDecoration.Underline))
            }
            if (s.type != SpanType.URL) {
                add(s.start, s.contentStart, dim)
                add(s.contentEnd, s.end, dim)
            }
        }
        return TransformedText(AnnotatedString(String(chars), styles), OffsetMapping.Identity)
    }
}

// ── Toolbar ───────────────────────────────────────────────────

class FormatActions(
    val onBold: () -> Unit = {},
    val onItalic: () -> Unit = {},
    val onHeading: () -> Unit = {},
    val onBullets: () -> Unit = {},
    val onNumbered: () -> Unit = {},
    val onChecklist: () -> Unit = {},
    val onIndent: () -> Unit = {},
    val onOutdent: () -> Unit = {},
    val onLink: () -> Unit = {},
    val onGallery: () -> Unit = {},
    val onCamera: () -> Unit = {},
    val onVoice: () -> Unit = {},
    val onScan: () -> Unit = {}
)

/** One row of formatting and insert tools above the keyboard; scrolls sideways on narrow screens. */
@Composable
fun FormatBar(actions: FormatActions, ink: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolButton(Icons.Rounded.FormatBold, R.string.format_bold, ink, actions.onBold)
        ToolButton(Icons.Rounded.FormatItalic, R.string.format_italic, ink, actions.onItalic)
        ToolButton(Icons.Rounded.Title, R.string.format_heading, ink, actions.onHeading)
        ToolDivider(ink)
        ToolButton(Icons.Rounded.Checklist, R.string.format_checklist, ink, actions.onChecklist)
        ToolButton(Icons.Rounded.FormatListBulleted, R.string.format_bullets, ink, actions.onBullets)
        ToolButton(Icons.Rounded.FormatListNumbered, R.string.format_numbered, ink, actions.onNumbered)
        ToolButton(Icons.Rounded.FormatIndentDecrease, R.string.format_outdent, ink, actions.onOutdent)
        ToolButton(Icons.Rounded.FormatIndentIncrease, R.string.format_indent, ink, actions.onIndent)
        ToolDivider(ink)
        ToolButton(Icons.Rounded.Link, R.string.format_link, ink, actions.onLink)
        ToolButton(Icons.Rounded.Image, R.string.format_gallery, ink, actions.onGallery)
        ToolButton(Icons.Rounded.PhotoCamera, R.string.format_camera, ink, actions.onCamera)
        ToolButton(Icons.Rounded.Mic, R.string.format_voice, ink, actions.onVoice)
        ToolButton(Icons.Rounded.DocumentScanner, R.string.format_scan, ink, actions.onScan)
    }
}

@Composable
private fun ToolButton(icon: ImageVector, description: Int, ink: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = stringResource(description), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = stringResource(description), tint = ink.copy(alpha = 0.82f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ToolDivider(ink: Color) {
    Spacer(Modifier.width(4.dp))
    Box(Modifier.width(1.dp).height(22.dp).background(ink.copy(alpha = 0.14f)))
    Spacer(Modifier.width(4.dp))
}

/** Add or edit a link: its text and address, with "Open" when the caret is in an existing one. */
@Composable
fun LinkDialog(
    initialLabel: String,
    initialUrl: String,
    onConfirm: (label: String, url: String) -> Unit,
    onDismiss: () -> Unit,
    onOpen: ((url: String) -> Unit)? = null
) {
    var label by remember { mutableStateOf(initialLabel) }
    var url by remember { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.link_text)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.link_address)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label.trim(), url.trim()) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.link_apply))
            }
        },
        dismissButton = {
            Row {
                if (onOpen != null && initialUrl.isNotBlank()) {
                    TextButton(onClick = { onOpen(url.trim()) }) { Text(stringResource(R.string.link_open)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
