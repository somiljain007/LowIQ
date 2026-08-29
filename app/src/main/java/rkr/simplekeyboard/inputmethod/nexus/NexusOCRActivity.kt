package rkr.simplekeyboard.inputmethod.nexus

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


// ============================================================
// NEXUS PIXEL THEME
// ============================================================

private val NexusBackground = ComposeColor(0xFF090A0F)
private val NexusSurface = ComposeColor(0xFF12131A)
private val NexusSurfaceRaised = ComposeColor(0xFF171820)
private val NexusSurfaceSoft = ComposeColor(0xFF20212A)

private val NexusText = ComposeColor(0xFFF7F5FC)
private val NexusSecondary = ComposeColor(0xFFA8A5B1)
private val NexusMuted = ComposeColor(0xFF73717B)

private val NexusAccent = ComposeColor(0xFF8B78FF)
private val NexusAccentBright = ComposeColor(0xFFA99AFF)

private val NexusGreen = ComposeColor(0xFF66D39A)
private val NexusGreenBackground = ComposeColor(0xFF10271C)

private val NexusSelected = ComposeColor(0x665F83FF)
private val NexusSelectedBorder = ComposeColor(0xFF8DAEFF)

private val NexusActive = ComposeColor(0xAAFFD166)
private val NexusActiveBorder = ComposeColor(0xFFFFD166)


// ============================================================
// OCR WORD
// ============================================================

data class OcrWord(
    val id: Int,
    val text: String,
    val bounds: Rect,
    val lineNumber: Int,
    val order: Int
)


// ============================================================
// ACTIVITY
// ============================================================

class NexusOCRActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionBar?.hide()

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NexusAccent,
                    onPrimary = ComposeColor(0xFF100D18),
                    background = NexusBackground,
                    onBackground = NexusText,
                    surface = NexusSurface,
                    onSurface = NexusText,
                    secondary = NexusAccentBright
                )
            ) {
                NexusOCRApp()
            }
        }
    }
}


// ============================================================
// MAIN APP
// ============================================================

@Composable
fun NexusOCRApp() {

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var bitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var words by remember {
        mutableStateOf<List<OcrWord>>(emptyList())
    }

    var selectedIds by remember {
        mutableStateOf<Set<Int>>(emptySet())
    }

    var isProcessing by remember {
        mutableStateOf(false)
    }

    var statusText by remember {
        mutableStateOf("Choose an image or document to begin")
    }


    // ========================================================
    // ML KIT
    // ========================================================

    val recognizer = remember {
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.close()
        }
    }


    // ========================================================
    // OCR
    // ========================================================

    fun runOCR(inputBitmap: Bitmap) {

        bitmap = inputBitmap
        words = emptyList()
        selectedIds = emptySet()

        isProcessing = true
        statusText = "Scanning image locally…"

        val image = InputImage.fromBitmap(
            inputBitmap,
            0
        )

        recognizer
            .process(image)

            .addOnSuccessListener { result ->

                val detected = mutableListOf<OcrWord>()

                var nextId = 0
                var order = 0
                var lineNumber = 0

                for (block in result.textBlocks) {

                    for (line in block.lines) {

                        for (element in line.elements) {

                            val box = element.boundingBox

                            if (
                                box != null &&
                                element.text.isNotBlank()
                            ) {

                                detected.add(
                                    OcrWord(
                                        id = nextId++,
                                        text = element.text.trim(),
                                        bounds = Rect(box),
                                        lineNumber = lineNumber,
                                        order = order++
                                    )
                                )
                            }
                        }

                        lineNumber++
                    }
                }

                words = detected
                isProcessing = false

                statusText =
                    if (detected.isEmpty()) {
                        "No readable text found"
                    } else {
                        "${detected.size} words detected • Tap or drag to select"
                    }
            }

            .addOnFailureListener { error ->

                isProcessing = false
                statusText = "OCR could not read this image"

                Toast.makeText(
                    context,
                    error.message ?: "OCR failed",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // ========================================================
    // CAMERA
    // ========================================================

    var cameraImageUri by remember {
        mutableStateOf<Uri?>(null)
    }

    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->

            val uri = cameraImageUri

            if (uri == null) {

                Toast.makeText(
                    context,
                    "Camera image URI was not created",
                    Toast.LENGTH_LONG
                ).show()

            } else if (!success) {

                context.contentResolver.delete(
                    uri,
                    null,
                    null
                )

                Toast.makeText(
                    context,
                    "Camera capture cancelled",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                scope.launch {

                    val capturedBitmap =
                        withContext(Dispatchers.IO) {

                            try {

                                context
                                    .contentResolver
                                    .openFileDescriptor(
                                        uri,
                                        "r"
                                    )
                                    ?.use { descriptor ->

                                        android.graphics.BitmapFactory
                                            .decodeFileDescriptor(
                                                descriptor.fileDescriptor
                                            )
                                    }

                            } catch (_: Exception) {

                                null
                            }
                        }

                    if (capturedBitmap != null) {

                        val corrected =
                            rotateBitmapFromExif(
                                context,
                                uri,
                                capturedBitmap
                            )

                        runOCR(corrected)

                    } else {

                        Toast.makeText(
                            context,
                            "Unable to read captured image",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    context.contentResolver.delete(
                        uri,
                        null,
                        null
                    )
                }
            }

            cameraImageUri = null
        }


    fun openCamera() {

        try {

            val values =
                ContentValues().apply {

                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        "nexus_pixel_${System.currentTimeMillis()}.jpg"
                    )

                    put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/jpeg"
                    )
                }

            val uri =
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )

            if (uri == null) {

                Toast.makeText(
                    context,
                    "Unable to create camera image",
                    Toast.LENGTH_LONG
                ).show()

                return
            }

            cameraImageUri = uri

            try {

                cameraLauncher.launch(uri)

            } catch (_: Exception) {

                context.contentResolver.delete(
                    uri,
                    null,
                    null
                )

                cameraImageUri = null

                Toast.makeText(
                    context,
                    "No camera app is available",
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (error: Exception) {

            cameraImageUri?.let { uri ->

                context.contentResolver.delete(
                    uri,
                    null,
                    null
                )
            }

            cameraImageUri = null

            Toast.makeText(
                context,
                "Unable to open camera: ${error.message ?: "unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                openCamera()

            } else {

                Toast.makeText(
                    context,
                    "Camera permission is required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    // ========================================================
    // GALLERY
    // ========================================================

    val galleryLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            scope.launch {

                val loaded =
                    withContext(Dispatchers.IO) {

                        try {

                            context
                                .contentResolver
                                .openInputStream(uri)
                                ?.use { stream ->

                                    android.graphics.BitmapFactory
                                        .decodeStream(stream)
                                }

                        } catch (_: Exception) {

                            null
                        }
                    }

                if (loaded != null) {

                    runOCR(loaded)

                } else {

                    Toast.makeText(
                        context,
                        "Unable to open image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }


    // ========================================================
    // DOCUMENT / PDF
    // ========================================================

    val documentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            scope.launch {

                try {

                    val mime =
                        context
                            .contentResolver
                            .getType(uri)
                            ?.lowercase()

                    if (mime == "application/pdf") {

                        val page =
                            withContext(Dispatchers.IO) {

                                renderFirstPdfPage(
                                    context,
                                    uri
                                )
                            }

                        if (page != null) {

                            runOCR(page)

                        } else {

                            Toast.makeText(
                                context,
                                "Could not read PDF",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } else {

                        val image =
                            withContext(Dispatchers.IO) {

                                context
                                    .contentResolver
                                    .openInputStream(uri)
                                    ?.use { stream ->

                                        android.graphics.BitmapFactory
                                            .decodeStream(stream)
                                    }
                            }

                        if (image != null) {

                            runOCR(image)

                        } else {

                            Toast.makeText(
                                context,
                                "Could not read document",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                } catch (_: Exception) {

                    Toast.makeText(
                        context,
                        "Unable to open document",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }


    // ========================================================
    // SELECTED TEXT
    // ========================================================

    val selectedWords =
        words
            .filter { selectedIds.contains(it.id) }
            .sortedBy { it.order }

    val selectedText =
        buildSelectedText(selectedWords)


    // ========================================================
    // UI
    // ========================================================

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NexusBackground
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 20.dp,
                        vertical = 20.dp
                    ),

            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            // ------------------------------------------------
            // HEADER
            // ------------------------------------------------

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 2.dp,
                            bottom = 4.dp
                        )
            ) {

                Text(
                    text = "NEXUS PIXEL",
                    color = NexusAccentBright,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Text(
                    text = "Pixel OCR",
                    color = NexusText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.9).sp
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )

                Text(
                    text = "Offline on-device text extraction",
                    color = NexusSecondary,
                    fontSize = 15.sp
                )
            }


            // ------------------------------------------------
            // SOURCE
            // ------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = NexusSurface
                )
            ) {

                Column(
                    modifier = Modifier.padding(15.dp)
                ) {

                    Text(
                        text = "Choose source",
                        color = NexusText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(11.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {

                        SourceButton(
                            modifier = Modifier.weight(1f),
                            title = "📷\nCamera",
                            onClick = {

                                if (
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) {

                                    openCamera()

                                } else {

                                    cameraPermissionLauncher.launch(
                                        Manifest.permission.CAMERA
                                    )
                                }
                            }
                        )

                        SourceButton(
                            modifier = Modifier.weight(1f),
                            title = "🖼\nGallery",
                            onClick = {
                                galleryLauncher.launch("image/*")
                            }
                        )

                        SourceButton(
                            modifier = Modifier.weight(1f),
                            title = "📄\nDocument",
                            onClick = {
                                documentLauncher.launch(
                                    arrayOf(
                                        "image/*",
                                        "application/pdf"
                                    )
                                )
                            }
                        )
                    }
                }
            }


            // ------------------------------------------------
            // STATUS
            // ------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = NexusSurfaceRaised
                )
            ) {

                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = 17.dp,
                            vertical = 16.dp
                        ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    if (isProcessing) {

                        CircularProgressIndicator(
                            modifier = Modifier.size(23.dp),
                            color = NexusAccentBright,
                            strokeWidth = 2.5.dp
                        )

                        Spacer(
                            modifier = Modifier.width(12.dp)
                        )
                    }

                    Column {

                        Text(
                            text =
                                if (isProcessing) {
                                    "PROCESSING"
                                } else {
                                    "STATUS"
                                },

                            color = NexusSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        Text(
                            text = statusText,
                            color =
                                if (isProcessing) {
                                    NexusAccentBright
                                } else {
                                    NexusText
                                },

                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }


            // ------------------------------------------------
            // RESULTS
            // ------------------------------------------------

            bitmap?.let { currentBitmap ->

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NexusSurface
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(15.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {

                                Text(
                                    text = "Smart Select",
                                    color = NexusText,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(
                                    modifier = Modifier.height(3.dp)
                                )

                                Text(
                                    text = "Choose individual words",
                                    color = NexusSecondary,
                                    fontSize = 14.sp
                                )
                            }

                            Card(
                                shape =
                                    RoundedCornerShape(15.dp),

                                colors =
                                    CardDefaults.cardColors(
                                        containerColor =
                                            NexusSurfaceSoft
                                    )
                            ) {

                                Text(
                                    text =
                                        "${selectedIds.size} selected",

                                    color =
                                        NexusAccentBright,

                                    fontSize = 12.sp,
                                    fontWeight =
                                        FontWeight.Bold,

                                    modifier =
                                        Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 9.dp
                                        )
                                )
                            }
                        }

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Text(
                            text =
                                "Tap a word or drag across words • Pinch or use +/− to zoom",

                            color = NexusSecondary,
                            fontSize = 13.sp
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        SmartOcrViewer(
                            bitmap = currentBitmap,
                            words = words,
                            selectedIds = selectedIds,
                            onSelectionChanged = {
                                selectedIds = it
                            }
                        )
                    }
                }


                // ------------------------------------------------
                // SELECTION BAR
                // ------------------------------------------------

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text =
                            "${selectedIds.size} words selected",

                        color = NexusText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = {
                            selectedIds =
                                words.map { it.id }.toSet()
                        }
                    ) {

                        Text(
                            text = "Select all",
                            color = NexusAccentBright,
                            fontSize = 14.sp,
                            fontWeight =
                                FontWeight.SemiBold
                        )
                    }

                    TextButton(
                        onClick = {
                            selectedIds = emptySet()
                        }
                    ) {

                        Text(
                            text = "Clear",
                            color = NexusSecondary,
                            fontSize = 14.sp
                        )
                    }
                }


                // ------------------------------------------------
                // SELECTED TEXT
                // ------------------------------------------------

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NexusSurface
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(17.dp)
                    ) {

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                text = "Selected text",
                                color = NexusText,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (selectedText.isNotBlank()) {

                                Spacer(
                                    modifier = Modifier.width(10.dp)
                                )

                                Card(
                                    shape =
                                        RoundedCornerShape(10.dp),

                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor =
                                                NexusGreenBackground
                                        )
                                ) {

                                    Text(
                                        text = "READY",
                                        color = NexusGreen,
                                        fontSize = 10.sp,
                                        fontWeight =
                                            FontWeight.Bold,

                                        modifier =
                                            Modifier.padding(
                                                horizontal = 9.dp,
                                                vertical = 6.dp
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(17.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = NexusBackground
                            )
                        ) {

                            Text(
                                text =
                                    if (
                                        selectedText.isBlank()
                                    ) {
                                        "Tap or drag across words in the image. Your selection will appear here."
                                    } else {
                                        selectedText
                                    },

                                color =
                                    if (
                                        selectedText.isBlank()
                                    ) {
                                        NexusMuted
                                    } else {
                                        NexusText
                                    },

                                fontSize = 17.sp,
                                lineHeight = 25.sp,

                                modifier =
                                    Modifier.padding(16.dp)
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(14.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {

                            OutlinedButton(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(54.dp),

                                enabled =
                                    selectedText.isNotBlank(),

                                shape =
                                    RoundedCornerShape(16.dp),

                                colors =
                                    ButtonDefaults
                                        .outlinedButtonColors(
                                            contentColor =
                                                NexusText,

                                            disabledContentColor =
                                                NexusMuted
                                        ),

                                onClick = {

                                    clipboard.setText(
                                        AnnotatedString(
                                            selectedText.trim()
                                        )
                                    )

                                    Toast.makeText(
                                        context,
                                        "Text copied",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {

                                Text(
                                    text = "📋  Copy",
                                    fontSize = 15.sp,
                                    fontWeight =
                                        FontWeight.SemiBold
                                )
                            }

                            Button(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(54.dp),

                                enabled =
                                    selectedText.isNotBlank(),

                                shape =
                                    RoundedCornerShape(16.dp),

                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                            NexusAccentBright,

                                        contentColor =
                                            ComposeColor(
                                                0xFF120F1D
                                            ),

                                        disabledContainerColor =
                                            NexusSurfaceSoft,

                                        disabledContentColor =
                                            NexusMuted
                                    ),

                                onClick = {

                                    val textToInsert =
                                        selectedText.trim()

                                    if (
                                        textToInsert.isBlank()
                                    ) {

                                        Toast.makeText(
                                            context,
                                            "No text selected",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                    } else {

                                        NexusImeBridge
                                            .setPendingText(
                                                textToInsert
                                            )

                                        Toast.makeText(
                                            context,
                                            "Text ready — returning to NexusIME",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        (
                                                context as? Activity
                                                )?.finish()
                                    }
                                }
                            ) {

                                Text(
                                    text = "🚀  Insert",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }


                // ------------------------------------------------
                // SCAN AGAIN
                // ------------------------------------------------

                OutlinedButton(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),

                    shape =
                        RoundedCornerShape(16.dp),

                    colors =
                        ButtonDefaults
                            .outlinedButtonColors(
                                contentColor =
                                    NexusSecondary
                            ),

                    onClick = {

                        bitmap = null
                        words = emptyList()
                        selectedIds = emptySet()
                        statusText =
                            "Choose an image or document to begin"
                    }
                ) {

                    Text(
                        text = "↻  Scan another image",
                        fontSize = 14.sp,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }


            // ------------------------------------------------
            // FOOTER
            // ------------------------------------------------

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = "🔒",
                    fontSize = 13.sp
                )

                Spacer(
                    modifier = Modifier.width(7.dp)
                )

                Text(
                    text =
                        "OCR is processed locally on this device.",

                    color = NexusMuted,
                    fontSize = 12.sp
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )
        }
    }
}


// ============================================================
// SOURCE BUTTON
// ============================================================

@Composable
fun SourceButton(
    modifier: Modifier,
    title: String,
    onClick: () -> Unit
) {

    Button(
        modifier = modifier.height(76.dp),
        onClick = onClick,

        shape =
            RoundedCornerShape(17.dp),

        colors =
            ButtonDefaults.buttonColors(
                containerColor = NexusSurfaceRaised,
                contentColor = NexusText
            ),

        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 2.dp
            )
    ) {

        Text(
            text = title,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


// ============================================================
// OCR VIEWER
// ============================================================

@Composable
fun SmartOcrViewer(
    bitmap: Bitmap,
    words: List<OcrWord>,
    selectedIds: Set<Int>,
    onSelectionChanged: (Set<Int>) -> Unit
) {

    var zoom by remember(bitmap) {
        mutableStateOf(1f)
    }

    var panX by remember(bitmap) {
        mutableStateOf(0f)
    }

    var panY by remember(bitmap) {
        mutableStateOf(0f)
    }

    var activeWordId by remember {
        mutableStateOf<Int?>(null)
    }

    var dragStartWordId by remember {
        mutableStateOf<Int?>(null)
    }


    fun resetView() {
        zoom = 1f
        panX = 0f
        panY = 0f
        activeWordId = null
        dragStartWordId = null
    }


    fun zoomIn() {
        zoom = min(5f, zoom + 0.5f)
    }


    fun zoomOut() {

        zoom = max(1f, zoom - 0.5f)

        if (zoom <= 1f) {

            zoom = 1f
            panX = 0f
            panY = 0f
        }
    }


    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                ComposeColor(0xFF030407)
        )
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        bitmap.width.toFloat() /
                                bitmap.height.toFloat()
                    )
        ) {

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {

                            scaleX = zoom
                            scaleY = zoom
                            translationX = panX
                            translationY = panY
                        }
            ) {

                Image(
                    bitmap =
                        bitmap.asImageBitmap(),

                    contentDescription =
                        "OCR source image",

                    modifier =
                        Modifier.fillMaxSize(),

                    contentScale =
                        ContentScale.FillBounds
                )


                Canvas(
                    modifier =
                        Modifier.fillMaxSize()
                ) {

                    val scaleX =
                        size.width /
                                bitmap.width.toFloat()

                    val scaleY =
                        size.height /
                                bitmap.height.toFloat()


                    for (word in words) {

                        val left =
                            word.bounds.left * scaleX

                        val top =
                            word.bounds.top * scaleY

                        val right =
                            word.bounds.right * scaleX

                        val bottom =
                            word.bounds.bottom * scaleY

                        val selected =
                            selectedIds.contains(
                                word.id
                            )

                        val active =
                            activeWordId == word.id


                        if (selected || active) {

                            val boxSize =
                                androidx.compose.ui.geometry
                                    .Size(
                                        max(
                                            1f,
                                            right - left
                                        ),
                                        max(
                                            1f,
                                            bottom - top
                                        )
                                    )

                            drawRoundRect(
                                color =
                                    if (active) {
                                        NexusActive
                                    } else {
                                        NexusSelected
                                    },

                                topLeft =
                                    Offset(
                                        left,
                                        top
                                    ),

                                size =
                                    boxSize,

                                cornerRadius =
                                    androidx.compose.ui.geometry
                                        .CornerRadius(
                                            5f,
                                            5f
                                        )
                            )

                            drawRoundRect(
                                color =
                                    if (active) {
                                        NexusActiveBorder
                                    } else {
                                        NexusSelectedBorder
                                    },

                                topLeft =
                                    Offset(
                                        left,
                                        top
                                    ),

                                size =
                                    boxSize,

                                cornerRadius =
                                    androidx.compose.ui.geometry
                                        .CornerRadius(
                                            5f,
                                            5f
                                        ),

                                style =
                                    Stroke(
                                        width = 2f
                                    )
                            )
                        }
                    }
                }
            }


            // ------------------------------------------------
            // TAP
            // ------------------------------------------------

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(
                            bitmap,
                            words,
                            selectedIds,
                            zoom,
                            panX,
                            panY
                        ) {

                            detectTapGestures { position ->

                                val point =
                                    screenToBitmapPoint(
                                        position,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        bitmap,
                                        zoom,
                                        panX,
                                        panY
                                    )

                                val word =
                                    findBestWord(
                                        point,
                                        words
                                    )

                                if (word != null) {

                                    val newSelection =
                                        selectedIds.toMutableSet()

                                    if (
                                        newSelection.contains(
                                            word.id
                                        )
                                    ) {

                                        newSelection.remove(
                                            word.id
                                        )

                                    } else {

                                        newSelection.add(
                                            word.id
                                        )
                                    }

                                    onSelectionChanged(
                                        newSelection
                                    )
                                }
                            }
                        }


                        // ------------------------------------------------
                        // PINCH / PAN
                        // ------------------------------------------------

                        .pointerInput(
                            bitmap
                        ) {

                            detectTransformGestures(
                                panZoomLock = false
                            ) {

                                    _,
                                    pan,
                                    gestureZoom,
                                    _ ->

                                if (
                                    gestureZoom != 1f
                                ) {

                                    zoom =
                                        min(
                                            5f,
                                            max(
                                                1f,
                                                zoom *
                                                        gestureZoom
                                            )
                                        )
                                }

                                if (zoom > 1f) {

                                    panX += pan.x
                                    panY += pan.y

                                } else {

                                    panX = 0f
                                    panY = 0f
                                }
                            }
                        }


                        // ------------------------------------------------
                        // DRAG SELECTION
                        // ------------------------------------------------

                        .pointerInput(
                            bitmap,
                            words,
                            selectedIds,
                            zoom,
                            panX,
                            panY
                        ) {

                            awaitPointerEventScope {

                                var lastPosition: Offset? =
                                    null

                                while (true) {

                                    val event =
                                        awaitPointerEvent()

                                    val pointer =
                                        event.changes
                                            .firstOrNull()

                                    if (pointer == null) {
                                        continue
                                    }

                                    if (
                                        event.changes.count {
                                            it.pressed
                                        } >= 2
                                    ) {

                                        lastPosition = null
                                        dragStartWordId = null
                                        activeWordId = null
                                        continue
                                    }

                                    if (
                                        pointer.pressed
                                    ) {

                                        val position =
                                            pointer.position

                                        val previous =
                                            lastPosition
                                                ?: position

                                        val dx =
                                            position.x -
                                                    previous.x

                                        val dy =
                                            position.y -
                                                    previous.y

                                        val distance =
                                            sqrt(
                                                dx * dx +
                                                        dy * dy
                                            )

                                        if (
                                            distance >= 3f ||
                                            lastPosition == null
                                        ) {

                                            val point =
                                                screenToBitmapPoint(
                                                    position,
                                                    size.width.toFloat(),
                                                    size.height.toFloat(),
                                                    bitmap,
                                                    zoom,
                                                    panX,
                                                    panY
                                                )

                                            val word =
                                                findBestWord(
                                                    point,
                                                    words
                                                )

                                            if (
                                                word != null
                                            ) {

                                                if (
                                                    dragStartWordId ==
                                                    null
                                                ) {

                                                    dragStartWordId =
                                                        word.id
                                                }

                                                activeWordId =
                                                    word.id


                                                val startWord =
                                                    words.firstOrNull {
                                                        it.id ==
                                                                dragStartWordId
                                                    }


                                                if (
                                                    startWord !=
                                                    null
                                                ) {

                                                    val start =
                                                        min(
                                                            startWord.order,
                                                            word.order
                                                        )

                                                    val end =
                                                        max(
                                                            startWord.order,
                                                            word.order
                                                        )

                                                    val rangeIds = words
                                                        .filter { it.order in start..end }
                                                        .map { it.id }
                                                        .toSet()

                                                    onSelectionChanged(
                                                        selectedIds +
                                                                rangeIds
                                                    )
                                                }
                                            }
                                        }

                                        lastPosition =
                                            position

                                        pointer.consume()

                                    } else {

                                        lastPosition = null
                                        dragStartWordId = null
                                        activeWordId = null
                                    }
                                }
                            }
                        }
            )


            // ------------------------------------------------
            // ACTIVE WORD
            // ------------------------------------------------

            activeWordId?.let { id ->

                val activeWord =
                    words.firstOrNull {
                        it.id == id
                    }

                if (activeWord != null) {

                    Card(
                        modifier =
                            Modifier
                                .align(
                                    Alignment.TopCenter
                                )
                                .padding(
                                    top = 10.dp
                                ),

                        shape =
                            RoundedCornerShape(15.dp),

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    ComposeColor(
                                        0xEE11131A
                                    )
                            )
                    ) {

                        Text(
                            text = activeWord.text,
                            color = NexusActiveBorder,

                            modifier =
                                Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 9.dp
                                ),

                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }


            // ------------------------------------------------
            // ZOOM
            // ------------------------------------------------

            Column(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomEnd
                        )
                        .padding(10.dp),

                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                ZoomButton(
                    text = "+",
                    onClick = {
                        zoomIn()
                    }
                )

                ZoomButton(
                    text = "−",
                    onClick = {
                        zoomOut()
                    }
                )

                ZoomButton(
                    text = "↺",
                    onClick = {
                        resetView()
                    }
                )
            }


            Card(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomStart
                        )
                        .padding(10.dp),

                shape =
                    RoundedCornerShape(13.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            ComposeColor(
                                0xDD11131A
                            )
                    )
            ) {

                Text(
                    text =
                        "${(zoom * 100).toInt()}%",

                    color = NexusText,

                    modifier =
                        Modifier.padding(
                            horizontal = 11.dp,
                            vertical = 8.dp
                        ),

                    fontSize = 12.sp,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }
    }
}


// ============================================================
// ZOOM BUTTON
// ============================================================

@Composable
fun ZoomButton(
    text: String,
    onClick: () -> Unit
) {

    IconButton(
        onClick = onClick,

        modifier =
            Modifier
                .size(45.dp)
                .clip(CircleShape)
                .background(
                    ComposeColor(
                        0xE611131A
                    )
                )
    ) {

        Text(
            text = text,
            color = NexusText,

            fontSize =
                if (text == "↺") {
                    18.sp
                } else {
                    25.sp
                },

            fontWeight = FontWeight.Medium
        )
    }
}


// ============================================================
// FIND WORD
// ============================================================

fun findBestWord(
    point: Offset,
    words: List<OcrWord>
): OcrWord? {

    for (word in words) {

        if (
            word.bounds.contains(
                point.x.toInt(),
                point.y.toInt()
            )
        ) {

            return word
        }
    }

    val maxDistance = 45f

    return words
        .minByOrNull { word ->

            distanceSquared(
                point.x,
                point.y,
                word.bounds.centerX().toFloat(),
                word.bounds.centerY().toFloat()
            )
        }
        ?.takeIf { word ->

            distanceSquared(
                point.x,
                point.y,
                word.bounds.centerX().toFloat(),
                word.bounds.centerY().toFloat()
            ) <=
                    maxDistance * maxDistance
        }
}


// ============================================================
// SCREEN → BITMAP
// ============================================================

fun screenToBitmapPoint(
    position: Offset,
    viewWidth: Float,
    viewHeight: Float,
    bitmap: Bitmap,
    zoom: Float,
    panX: Float,
    panY: Float
): Offset {

    val transformedX =
        (position.x - panX) / zoom

    val transformedY =
        (position.y - panY) / zoom

    val scaleX =
        bitmap.width.toFloat() /
                viewWidth

    val scaleY =
        bitmap.height.toFloat() /
                viewHeight

    return Offset(
        transformedX * scaleX,
        transformedY * scaleY
    )
}


// ============================================================
// SELECTED TEXT
// ============================================================

fun buildSelectedText(
    selectedWords: List<OcrWord>
): String {

    if (selectedWords.isEmpty()) {
        return ""
    }

    val builder = StringBuilder()

    var previousLine = -1

    for (word in selectedWords) {

        if (builder.isNotEmpty()) {

            if (
                word.lineNumber !=
                previousLine
            ) {

                builder.append("\n")

            } else {

                builder.append(" ")
            }
        }

        builder.append(word.text)

        previousLine =
            word.lineNumber
    }

    return builder
        .toString()
        .replace(" ,", ",")
        .replace(" .", ".")
        .replace(" !", "!")
        .replace(" ?", "?")
        .replace(" :", ":")
        .replace(" ;", ";")
        .trim()
}


// ============================================================
// PDF → BITMAP
// ============================================================

fun renderFirstPdfPage(
    context: Context,
    uri: Uri
): Bitmap? {

    var descriptor: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    var page: PdfRenderer.Page? = null

    return try {

        descriptor =
            context
                .contentResolver
                .openFileDescriptor(
                    uri,
                    "r"
                )

        if (descriptor == null) {
            return null
        }

        renderer =
            PdfRenderer(
                descriptor
            )

        if (
            renderer.pageCount <= 0
        ) {
            return null
        }

        page =
            renderer.openPage(0)

        val scale = 2

        val bitmap =
            Bitmap.createBitmap(
                page.width * scale,
                page.height * scale,
                Bitmap.Config.ARGB_8888
            )

        bitmap.eraseColor(Color.WHITE)

        page.render(
            bitmap,
            Rect(
                0,
                0,
                bitmap.width,
                bitmap.height
            ),
            null,
            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )

        bitmap

    } catch (_: Exception) {

        null

    } finally {

        try {
            page?.close()
        } catch (_: Exception) {
        }

        try {
            renderer?.close()
        } catch (_: Exception) {
        }

        try {
            descriptor?.close()
        } catch (_: Exception) {
        }
    }
}


// ============================================================
// DISTANCE
// ============================================================

fun distanceSquared(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float
): Float {

    val dx = x1 - x2
    val dy = y1 - y2

    return dx * dx + dy * dy
}


// ============================================================
// CAMERA EXIF
// ============================================================

fun rotateBitmapFromExif(
    context: Context,
    uri: Uri,
    bitmap: Bitmap
): Bitmap {

    return try {

        val orientation =
            context.contentResolver
                .openInputStream(uri)
                ?.use { stream ->

                    val exif =
                        ExifInterface(stream)

                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
                ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()

        when (orientation) {

            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(90f)
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
            }

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.preScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.preScale(1f, -1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }

        if (matrix.isIdentity) {

            bitmap

        } else {

            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        }

    } catch (_: Exception) {

        bitmap
    }
}
