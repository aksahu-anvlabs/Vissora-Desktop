package org.anvlabs.vissora

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawWithCache
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

import kotlinx.coroutines.isActive
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image as SkiaImage
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicReference

fun Brush.Companion.angledGradient(colors: List<Color>, angleInDegrees: Float, size: Size): Brush {
    val angleRad = (angleInDegrees * (PI / 180f)).toFloat()

    val centerX = size.width / 2f
    val centerY = size.height / 2f

    val x = cos(angleRad)
    val y = sin(angleRad)

    val maxRadius = kotlin.math.hypot(centerX, centerY)

    val start = Offset(x = centerX - x * maxRadius, y = centerY - y * maxRadius)
    val end = Offset(x = centerX + x * maxRadius, y = centerY + y * maxRadius)

    return Brush.linearGradient(colors = colors, start = start, end = end)
}

var streamData by mutableStateOf("""
[
    {
        "name": "cam 1",
        "source": "../assets/videos/g7.mpeg",
        "loc": "D.A.V Public School",
        "url": "rtsp://127.0.0.1:8554/camera2",
        "detection": false,
        "nv": 0,
        "nf": 0,
        "fps": 60
    },
    {
        "name": "cam 2",
        "source": "../assets/videos/video3.mp4",
        "loc": "D.A.V Public School",
        "url": "rtsp://127.0.0.1:8554/camera",
        "stream": true,
        "detection": false,
        "nv": 0,
        "nf": 0,
        "fps": 60
    },
    {
        "name": "cam 3",
        "source": "../assets/videos/video2.mp4",
        "loc": "D.A.V Public School",
        "url": "rtsp://127.0.0.1:8554/camera",
        "stream": true,
        "detection": false,
        "nv": 0,
        "nf": 0,
        "fps": 60
    },
    {
        "name": "cam 4",
        "source": "../assets/videos/video4.mp4",
        "loc": "D.A.V Public School",
        "url": "rtsp://127.0.0.1:8554/camera",
        "stream": true,
        "detection": false,
        "nv": 0,
        "nf": 0,
        "fps": 60
    }
]
""")

@Suppress("DEPRECATION")
@Composable
fun App(onExitApp: () -> Unit) {
    var currentImage by remember {
        mutableStateOf<ImageBitmap?>(null)
    }
    val InterFont = FontFamily(
        Font(
            resource = "fonts/inter.ttf",
            weight = FontWeight.Normal
        )
    )
    val openSans = FontFamily(
        Font(
            resource = "fonts/open_sans.ttf",
            weight = FontWeight.Normal
        )
    )
    var index by remember { mutableStateOf(2) }
    var btnData = listOf("CPanel", "Reverse Tracer", "Embeddings", "Maps", "Accounts", "Settings", "Close")
    val defaultPadding: PaddingValues = ButtonDefaults.ContentPadding

    LaunchedEffect(Unit){

    }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {

        Row(
            modifier = Modifier.fillMaxSize().background(Color(0xff0a0a0a)),
            horizontalArrangement = Arrangement.Start
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(20.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1d1d1d)).weight(0.3f)){
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier=Modifier.fillMaxSize().padding(20.dp)){
                    Spacer(modifier=Modifier.height(20.dp))
                    Row(modifier=Modifier.fillMaxWidth().padding(defaultPadding), horizontalArrangement=Arrangement.spacedBy(20.dp), verticalAlignment=Alignment.CenterVertically){
                        Box(modifier=Modifier.size(45.dp).clip(RoundedCornerShape(12.dp)).drawWithCache{
                            val gradient = Brush.angledGradient(
                                colors = listOf(
                                    Color(0xFF38BDF8),
                                    Color(0xFF00CC66)
                                ),
                                angleInDegrees = 45f,
                                size = size
                            )
                            onDrawBehind {
                                drawRect(brush = gradient)
                            }
                        }){
                            Icon(
                                painter = painterResource("drawable/logo-black.svg"),
                                modifier=Modifier.size(40.dp).align(Alignment.Center),
                                tint = Color.Black,
                                contentDescription = null
                            )
                        }
                        Column(verticalArrangement=Arrangement.spacedBy(0.dp)){
                            Text(text="Vissora Desktop", fontSize=13.5.sp, color=Color(0xffcccccc), fontFamily = openSans, fontWeight=FontWeight.Bold)
                            Text(text="v1.1.3", color=Color(0xff999999), fontFamily = InterFont, fontSize=11.5.sp)
                        }
                    }
                    Spacer(modifier=Modifier.height(50.dp))
                    for(i in 0 until btnData.size){
                        if(i == 5){
                            Divider(color = Color(0xff333333), thickness = 1.dp, modifier=Modifier.fillMaxWidth().padding(defaultPadding))
                        }
                        Button(modifier=Modifier.widthIn(min=200.dp, max=300.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = if(i == index) Color.White else Color(0xff999999)
                            ),
                            onClick = {
                                index = i
                            }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(20.dp)){
                                Icon(
                                    painter = painterResource("icons/${btnData[i].lowercase().replace(" ", "_")}.svg"),
                                    modifier=Modifier.size(20.dp),
                                    tint = if(i == index) Color.White else Color(0xff999999),
                                    contentDescription = null
                                )
                                Text(btnData[i], fontWeight=FontWeight.Normal, fontFamily=InterFont, fontSize=13.sp)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(15.dp))
            Column(modifier = Modifier.weight(1f)) {
                if(index == 0){
                    CPanelWindow(streamData){x ->
                        streamData = x
                    }
                }
                else if(index == 1){
                    AccessWindow(streamData)
                }
                else if(index == 2){
                    EmbWindow()
                }
                else if(index == 6){
                    onExitApp()
                }
                /*Canvas(
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                ){
                    currentImage?.let { bitmap ->
                        val scale = minOf(
                            size.width / bitmap.width.toFloat(),
                            size.height / bitmap.height.toFloat()
                        )

                        val destWidth = bitmap.width * scale
                        val destHeight = bitmap.height * scale

                        val topLeftX = (size.width - destWidth) / 2f
                        val topLeftY = (size.height - destHeight) / 2f

                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(topLeftX.roundToInt(), topLeftY.roundToInt()),
                            dstSize = IntSize(destWidth.roundToInt(), destHeight.roundToInt())
                        )
                    }
                }*/
            }
        }
    }
}