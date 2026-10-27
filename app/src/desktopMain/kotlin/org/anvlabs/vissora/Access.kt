package org.anvlabs.vissora

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*

import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size

import org.json.JSONObject
import org.json.JSONArray
import java.nio.ByteBuffer
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import coil3.compose.AsyncImage
import kotlin.io.path.createTempDirectory

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar
import java.time.LocalTime
import java.nio.file.Path
import java.awt.Color as AwtColor
import javax.swing.JColorChooser
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import kotlin.io.path.outputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.client.request.forms.*
import io.ktor.utils.io.streams.*
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image as SkiaImage
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

val client = HttpClient(CIO)
val tempDir: Path = createTempDirectory(prefix = "app_temp_")

suspend fun uploadFile(filePath: String, requestBody: String): Pair<Int, String> {
    val file = File(filePath)

    require(file.isFile) { "File does not exist: $filePath" }

    val response = client.post("http://0.0.0.0:5000/match") {
        setBody(
            MultiPartFormDataContent(
                formData {
                    // 1. Append the JSON string as a form field.
                    // NOTE: Change "requestBody" to whatever form-data key your server expects (e.g., "data", "payload", or "json")
                    append("requestBody", requestBody)

                    // 2. Append the file
                    append(
                        "file",
                        file.readBytes(),
                        Headers.build {
                            append(
                                HttpHeaders.ContentDisposition,
                                "filename=\"${file.name}\""
                            )
                            append(
                                HttpHeaders.ContentType,
                                ContentType.Application.OctetStream.toString()
                            )
                        }
                    )
                }
            )
        )
    }
    val statusCode: Int = response.status.value
    val responseText: String = response.bodyAsText()

    return Pair(statusCode, responseText)
}

suspend fun downloadFileToTempDir(downloadUrl: String, fileName: String): Path {
    val targetPath = tempDir.resolve(fileName)
    client.prepareGet(downloadUrl).execute { httpResponse ->
        val channel = httpResponse.bodyAsChannel()
        withContext(Dispatchers.IO) {
            targetPath.outputStream().use { fileStream ->
                channel.copyTo(fileStream)
            }
        }
    }
    
    return targetPath
}

fun showSystemColorPicker(
    initialColor: AwtColor = AwtColor.BLACK,
    title: String = "Select Color"
): String? {
    val chosenColor = JColorChooser.showDialog(null, title, initialColor)
    return chosenColor?.let {
        // Formats as RRGGBB hex string (e.g. "38BDF8")
        String.format("%02X%02X%02X", it.red, it.green, it.blue)
    }
}

fun openImageInSystemViewer(targetPath: Path) {
    val file = targetPath.toFile()
    
    // Check if the OS supports Desktop operations
    if (java.awt.Desktop.isDesktopSupported()) {
        val desktop = java.awt.Desktop.getDesktop()
        if (file.exists()) {
            // Opens the file in the default OS image viewer
            desktop.open(file) 
        } else {
            println("File does not exist: $file")
        }
    } else {
        println("Desktop operations are not supported on this system.")
    }
}

suspend fun makePostRequest(url: String, requestBody: String = ""): Pair<Int, String> {
    val response: HttpResponse = client.post(url) {
        if (requestBody.isNotEmpty()) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }

    val statusCode: Int = response.status.value
    val responseText: String = response.bodyAsText()

    return Pair(statusCode, responseText)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(onDateSelected: (Long?) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateSelected(datePickerState.selectedDateMillis)
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModal(
    onTimeSelected: (hour: Int, minute: Int) -> Unit, 
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = 17,
        initialMinute = 10,
        is24Hour = true // Set to false if you prefer AM/PM format
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        }
    )
}

suspend fun openFilePicker(extensions: List<String> = emptyList()): String? = withContext(Dispatchers.Main) {
    val fileChooser = JFileChooser().apply {
        isMultiSelectionEnabled = false
        if (extensions.isNotEmpty()) {
            fileFilter = FileNameExtensionFilter("Allowed Files", *extensions.toTypedArray())
        }
    }
    
    val result = fileChooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        return@withContext fileChooser.selectedFile.absolutePath
    }
    return@withContext null
}

fun Modifier.checkerboardBackground(tileSize: Dp = 10.dp, colorA: Color = Color.Red, colorB: Color = Color.White): Modifier = this.drawBehind {
    val tilePx = tileSize.toPx()
    val columns = (size.width / tilePx).toInt() + 1
    val rows = (size.height / tilePx).toInt() + 1

    drawRect(color = colorA)

    for (row in 0 until rows) {
        for (col in 0 until columns) {
            if ((row + col) % 2 == 1) {
                drawRect(
                    color = colorB,
                    topLeft = Offset(col * tilePx, row * tilePx),
                    size = Size(tilePx, tilePx)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessWindow(streamData: String){
	var VRNText by remember { mutableStateOf("") }
	var AIPrompt by remember { mutableStateOf("") }
	var filePath by remember { mutableStateOf("No file selected") }
    val coroutineScope = rememberCoroutineScope()
    var fullscreen by remember { mutableStateOf(false) }
    var vnum by remember { mutableStateOf(0) }
    var selDate by remember { mutableStateOf(0) }
    var selTime by remember { mutableStateOf(0) }
    var sd by remember { mutableStateOf(0L) }
    var ed by remember { mutableStateOf(0L) }
    var st by remember { mutableStateOf(Pair(0, 0)) }
    var et by remember { mutableStateOf(Pair(0, 0)) }
    var red by remember { mutableStateOf(0) }
    var green by remember { mutableStateOf(0) }
    var blue by remember { mutableStateOf(0) }
    var hexCode by remember { mutableStateOf("000000") }
    val scope = rememberCoroutineScope()
    var anprRes by remember { mutableStateOf("{}") }
    var facedt by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("Any") }
	var color by remember { mutableStateOf("000000") }
	var mainData by remember { mutableStateOf(JSONArray()) }
    var trans by remember { mutableStateOf(true) }

    LaunchedEffect(Unit){
    	ed = System.currentTimeMillis()
    	sd = LocalDate.now(ZoneOffset.UTC).minusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    	val now = LocalTime.now()
    	et = Pair(now.hour, now.minute)
    	st = Pair(0, 0)

    	scope.launch{
    		val (status, body) = makePostRequest(
        		url = "http://0.0.0.0:5000/vech_info",
        		requestBody = "{}"
        	)
        	if(status == 200){
        		anprRes = body
        	}
    	}
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

    if(selDate != 0){
    	DatePickerModal({ x ->
    		if(selDate < 0){
    			sd = x ?: 0
    		}
    		else{
    			ed = x ?: 0
    		}
    	}){
    		selDate = 0
    	}
    }

    if(selTime != 0){
    	TimePickerModal({ x, y ->
    		if(selTime < 0){
    			st = Pair(x, y)
    		}
    		else{
    			et = Pair(x, y)
    		}
    	}){
    		selTime = 0
    	}
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(top=30.dp, end=20.dp)) {
        val rootMaxWidth = maxWidth
        val rootMaxHeight = maxHeight
		Column(modifier=Modifier.fillMaxSize().padding(20.dp)){
			Row(modifier=Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
				Text(text="Reverse Search", color = Color(0xffffffff), fontSize=30.sp, fontFamily=openSans, fontWeight=FontWeight.Bold)
				Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
					Button(modifier=Modifier.wrapContentWidth(),
		                colors = ButtonDefaults.buttonColors(
		                    containerColor = Color(0xffcccccc),
		                    contentColor = Color(0xff1a1a1a)
		                ),
		                onClick = {
		                	facedt = false
		                	vnum = -1
		                    fullscreen = true
		                    val formatter = SimpleDateFormat("dd-MM-yy", Locale.getDefault())
		                    scope.launch{
		                    	val (status, body) = makePostRequest(
		                    		url = "http://0.0.0.0:5000/get_vechile",
		                    		requestBody = """{"sd":"${formatter.format(Date(sd))}", "ed":"${formatter.format(Date(ed))}", "st":"${st.first}:${st.second}:00", "et":"${et.first}:${et.second}:00", "vrn": "$VRNText", "mode": "$selected", "color":"${if(trans) "" else color}"}"""
		                    	)
		                    	if(status == 200){
		                    		mainData = JSONArray(body)
		                    		vnum = mainData.length()
		                    	}
		                    }
		                }
		            ) {
		                Text("Search Vechile", fontWeight = FontWeight.Bold, fontFamily=InterFont)
		            }

		            Button(modifier=Modifier.wrapContentWidth(),
		                colors = ButtonDefaults.buttonColors(
		                    containerColor = Color(0xffcccccc),
		                    contentColor = Color(0xff1a1a1a)
		                ),
		                onClick = {
		                	facedt = true
		                    val formatter = SimpleDateFormat("dd-MM-yy", Locale.getDefault())
		                    scope.launch{
		                    	val path = openFilePicker()
		                    	if (path != null) {
		                    		print("PATH: " + path)
		                    		vnum = -1
		                    		fullscreen = true
			                    	val (status, body) = uploadFile(
			                    		path,
			                    		requestBody = """{"sd":"${formatter.format(Date(sd))}", "ed":"${formatter.format(Date(ed))}", "st":"${st.first}:${st.second}:00", "et":"${et.first}:${et.second}:00"}"""
			                    	)
			                    	if(status == 200){
			                    		mainData = JSONArray(body)
		                    			vnum = mainData.length()
			                    	}
			                    }
		                    }   
		                }
		            ) {
		                Text("Scan Face", fontWeight = FontWeight.Bold, fontFamily=InterFont)
		            }
		        }
			}
			Divider(color=Color(0xFF333333), thickness=1.dp, modifier=Modifier.fillMaxWidth().padding(top=5.dp))
			Spacer(modifier=Modifier.height(10.dp))
			Text(text="Search for information on vechile and number plates detected within a timeline.", color = Color(0xffe2e2e2), fontFamily=openSans)
			Spacer(modifier=Modifier.height(60.dp))
			Row(modifier=Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(50.dp)){
				Column(modifier=Modifier.weight(0.8f).padding(start=30.dp, end=30.dp)){
					Row(modifier=Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween){
						Row(modifier=Modifier.wrapContentWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(30.dp)){
							Button(
							    onClick = { selDate = -1 },
							    colors = ButtonDefaults.buttonColors(
							        containerColor = Color(0xff333333),
							        contentColor = Color.White
							    )
							) {
								Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(15.dp)){
								    Icon(
								        painter = painterResource("icons/calender.svg"),
					                    modifier=Modifier.size(14.dp),
					                    contentDescription = null
								    )
								    if(sd != 0L){
								    	val formatter = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
								    	Text(text=formatter.format(Date(sd)), fontFamily=InterFont)
									}
								}
							}
							Text(text="ST", color=Color(0xffcccccc))
							Button(
							    onClick = { selTime = -1 },
							    colors = ButtonDefaults.buttonColors(
							        containerColor = Color(0xff333333),
							        contentColor = Color.White
							    )
							) {
								Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(15.dp)){
								    Icon(
								        painter = painterResource("icons/clock.svg"),
					                    modifier=Modifier.size(14.dp),
					                    contentDescription = null
								    )
								    Text(text = String.format("%02d:%02d:00", st.first, st.second), fontFamily=InterFont)
								}
							}
						}
					}
					Spacer(modifier=Modifier.height(50.dp))
					Text(text="Plate no.", color=Color.White, fontSize=15.sp, fontFamily=openSans)
					Spacer(modifier=Modifier.height(10.dp))
					OutlinedTextField(
					    modifier = Modifier.fillMaxWidth(),
					    value = VRNText,
					    onValueChange = { newValue -> VRNText = newValue.uppercase() },
					    placeholder = { Text("OD 65 3923") },
					    shape = RoundedCornerShape(8.dp), // adjust corner radius as needed
					    colors = OutlinedTextFieldDefaults.colors(
					        focusedBorderColor = Color(0xFF38BDF8),
					        unfocusedBorderColor = Color(0xFF333333),
					        focusedContainerColor = Color(0xFF1A1A1A),
					        unfocusedContainerColor = Color(0xFF1A1A1A)
					    )
					)
				    Spacer(modifier=Modifier.height(40.dp))
				    Text(text="Vechile info", color=Color.White, fontSize=15.sp, fontFamily=openSans)
				    Spacer(modifier=Modifier.height(10.dp))
				    var expanded by remember { mutableStateOf(false) }
					var options = listOf("Any", "Bicycle", "Car", "Motorcycle", "Bus", "Truck")
					Row(modifier=Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
					    Box(modifier = Modifier.fillMaxWidth(1.0f)) {
					        TextField(
					            modifier = Modifier.fillMaxWidth(),
					            readOnly = true,
					            value = "Vechile type: $selected",
					            textStyle = TextStyle(
							        fontFamily = InterFont,
							        fontSize = 14.sp,
							        color = Color.White
							    ),
					            onValueChange = {},
					            trailingIcon = {
					                Icon(
					                    painter = painterResource("icons/arrow_down.svg"), // or Icons.Default.ArrowDropDown
					                    contentDescription = "Expand",
					                    tint = Color.White
					                )
					            },
					            colors = TextFieldDefaults.colors(
					                focusedIndicatorColor = Color.Transparent,
					                unfocusedIndicatorColor = Color.Transparent,
					                disabledIndicatorColor = Color.Transparent,
					                errorIndicatorColor = Color.Transparent,
					                focusedContainerColor = Color(0xFF333333),
					                unfocusedContainerColor = Color(0xFF333333),
					                focusedTextColor = Color.White,
					                unfocusedTextColor = Color.White
					            )
					        )

					        // Overlay Box to capture clicks across the entire field
					        Box(
					            modifier = Modifier.matchParentSize().clickable { expanded = !expanded }
					        )

					        DropdownMenu(
					            expanded = expanded,
					            onDismissRequest = { expanded = false },
					            modifier = Modifier.background(Color(0xFF222222))
					        ) {
					            options.forEach { selectionOption ->
					                DropdownMenuItem(
					                    text = { Text(selectionOption, color = Color.White) },
					                    onClick = {
					                        selected = selectionOption
					                        expanded = false
					                    }
					                )
					            }
					        }
					    }
						// IconButton(
						//     modifier=Modifier.fillMaxHeight().aspectRatio(1.0f).clip(RoundedCornerShape(10.dp)),
						//     onClick = {
						        
						//     },
						//     colors = IconButtonDefaults.iconButtonColors(
						//         containerColor = Color(0xFF333333),
						//         contentColor = Color.White
						//     )
						// ) {
						//     Icon(
						//         painter = painterResource("icons/color.svg"),
						//         contentDescription = null
						//     )
						// }
					}
					Spacer(modifier=Modifier.height(20.dp))
					Row(modifier=Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
					    Column(modifier=Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFcccccc)).clip(RoundedCornerShape(10.dp)).clickable{
					    	val selectedHex = showSystemColorPicker()
        					if (selectedHex != null){
                                trans = false
        						color = selectedHex
        					}
					    }){
                            if(!trans){
                                Column(modifier=Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(8.dp)).background(Color("FF$color".toLong(16)))){}
                            }
                            else{
    				        	Column(modifier=Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(8.dp)).checkerboardBackground(
                                    tileSize = 12.dp,
                                    colorA = Color.Black.copy(alpha=0.5f),
                                    colorB = Color.White.copy(alpha=0.5f)
                                )){}
                            }
				        }
					}
                    Spacer(modifier=Modifier.height(5.dp))
				    Row(modifier=Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(5.dp)){
                        Checkbox(
                            checked = trans,
                            onCheckedChange = { trans = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color.White,
                                checkmarkColor = Color.Black,
                                uncheckedColor = Color.Gray
                            )
                        )
                        Text(text="Transparency", color=Color.White, fontSize=15.sp, fontFamily=openSans)
                    }
				}
				Column(modifier=Modifier.weight(1.0f).padding(start=40.dp, end=40.dp), horizontalAlignment=Alignment.CenterHorizontally){
					Row(modifier=Modifier.wrapContentWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(30.dp)){
						Button(
						    onClick = { selDate = 1 },
						    colors = ButtonDefaults.buttonColors(
						        containerColor = Color(0xff333333),
						        contentColor = Color.White
						    )
						) {
							Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(15.dp)){
							    Icon(
							        painter = painterResource("icons/calender.svg"),
				                    modifier=Modifier.size(14.dp),
				                    contentDescription = null
							    )
							    if(ed != 0L){
							    	val formatter = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
							    	Text(text=formatter.format(Date(ed)), fontFamily=InterFont)
								}
							}
						}
						Text(text="ET", color=Color(0xffcccccc))
						Button(
						    onClick = { selTime = 1 },
						    colors = ButtonDefaults.buttonColors(
						        containerColor = Color(0xff333333),
						        contentColor = Color.White
						    )
						) {
							Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(15.dp)){
							    Icon(
							        painter = painterResource("icons/clock.svg"),
				                    modifier=Modifier.size(14.dp),
				                    contentDescription = null
							    )
							    Text(text = String.format("%02d:%02d:00", et.first, et.second), fontFamily=InterFont)
							}
						}
					}
					Spacer(modifier=Modifier.height(50.dp))
					Text(text="ANPR Results", color = Color(0xffffffff), fontSize=15.sp, fontFamily=InterFont, fontWeight=FontWeight.Bold)
					Divider(color=Color(0xFF333333), thickness=1.dp, modifier=Modifier.fillMaxWidth().padding(top=5.dp))
					Spacer(modifier=Modifier.height(5.dp))
					Row{
						Text(modifier=Modifier.weight(0.3f), text="Plate number", color=Color.White, fontFamily=InterFont, fontSize=12.sp, textAlign=TextAlign.Center)
						Text(modifier=Modifier.weight(0.2f), text="cam", color=Color.White, fontFamily=InterFont, fontSize=12.sp, textAlign=TextAlign.Center)
						Text(modifier=Modifier.weight(0.2f), text="date", color=Color.White, fontFamily=InterFont, fontSize=12.sp, textAlign=TextAlign.Center)
						Text(modifier=Modifier.weight(0.2f), text="time", color=Color.White, fontFamily=InterFont, fontSize=12.sp, textAlign=TextAlign.Center)
					}
					val obj = JSONObject(anprRes)
					val dtIterator = obj.keys()
					while (dtIterator.hasNext()) {
					    val dt = dtIterator.next() as String
					    val dates = obj.getJSONObject(dt)
					    
					    val camIterator = dates.keys()
					    while (camIterator.hasNext()) {
					        val cam = camIterator.next() as String
					        val camObj = dates.getJSONObject(cam)
					        
					        // Ensure "dt" array actually exists in this cam object
					        if (camObj.has("dt")) {
					            val dets = camObj.getJSONArray("dt")
					            
					            for (i in 0 until dets.length()) {
					                val data = dets.getJSONObject(i)
					                val pt = data.optString("pt", "").trim()
					                
					                if (pt.isNotEmpty()) {
					                    // JSON has decimals for "tm" (e.g. 1792047477.218123), so we must getDouble then convert to Long
					                    val tm = data.getDouble("tm").toLong()
					                    
					                    val dateTime = java.time.Instant.ofEpochSecond(tm).atZone(java.time.ZoneId.systemDefault())
					                    val formattedDate = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
					                    val formattedTime = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
					                    
					                    Spacer(modifier = Modifier.height(5.dp))
					                    Row {
					                        Text(modifier = Modifier.weight(0.3f), text = pt, color = Color(0xFFCCCCCC), fontFamily = InterFont, fontSize = 12.sp, textAlign = TextAlign.Center)
					                        Text(modifier = Modifier.weight(0.2f), text = cam, color = Color(0xFFCCCCCC), fontFamily = InterFont, fontSize = 12.sp, textAlign = TextAlign.Center)
					                        Text(modifier = Modifier.weight(0.2f), text = formattedDate, color = Color(0xFFCCCCCC), fontFamily = InterFont, fontSize = 12.sp, textAlign = TextAlign.Center)
					                        Text(modifier = Modifier.weight(0.2f), text = formattedTime, color = Color(0xFFCCCCCC), fontFamily = InterFont, fontSize = 12.sp, textAlign = TextAlign.Center)
					                    }
					                }
					            }
					        }
					    }
					}
				}
			}
			Row(modifier=Modifier.fillMaxWidth().padding(top=60.dp, start=30.dp)){
			// 	Spacer(modifier=Modifier.fillMaxWidth(.15f))
			// 	Button(modifier=Modifier.widthIn(min=150.dp, max=350.dp),
	        //         colors = ButtonDefaults.buttonColors(
	        //             containerColor = Color(0xFF38BDF8),
	        //             contentColor = Color.Black
	        //         ),
	        //         onClick = {
	        //         	vnum = -1
	        //             fullscreen = true
	        //             val formatter = SimpleDateFormat("dd-MM-yy", Locale.getDefault())
	        //             scope.launch{
	        //             	val (status, body) = makePostRequest(
	        //             		url = "http://0.0.0.0:5000/get_vechile",
	        //             		requestBody = """{"sd":"${formatter.format(Date(sd))}", "ed":"${formatter.format(Date(ed))}", "st":"${st.first}:${st.second}:00", "et":"${et.first}:${et.second}:00"}"""
	        //             	)
	        //             	if(status == 200){
	        //             		vnum = body.toInt()
	        //             	}
	        //             }
	        //         }
	        //     ) {
	        //         Text("Search Vechile", fontWeight = FontWeight.Bold)
	        //     }
	        //     Spacer(modifier=Modifier.fillMaxWidth(.5f))
	        //     Button(modifier=Modifier.widthIn(min=150.dp, max=350.dp),
	        //         colors = ButtonDefaults.buttonColors(
	        //             containerColor = Color(0xffe2e2e2),
	        //             contentColor = Color.Black
	        //         ),
	        //         onClick = {
	        //             val formatter = SimpleDateFormat("dd-MM-yy", Locale.getDefault())
	        //             scope.launch{
	        //             	val path = openFilePicker()
	        //             	if (path != null) {
	        //             		print("PATH: " + path)
	        //             		vnum = -1
	        //             		fullscreen = true
		    //                 	val (status, body) = uploadFile(
		    //                 		path,
		    //                 		requestBody = """{"sd":"${formatter.format(Date(sd))}", "ed":"${formatter.format(Date(ed))}", "st":"${st.first}:${st.second}:00", "et":"${et.first}:${et.second}:00"}"""
		    //                 	)
		    //                 	if(status == 200){
		    //                 		vnum = body.toInt()
		    //                 	}
		    //                 }
	        //             }
	        //         }
	        //     ) {
	        //         Text("Upload Photo", fontWeight = FontWeight.Bold)
	        //     }
			}
		}
		if(fullscreen){
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { fullscreen = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(rootMaxWidth * 0.8f)   // Exactly 80% of window width
                        .height(rootMaxHeight * 0.8f) // Exactly 80% of window height
                        .clickable(enabled = false, onClick = {}), // Prevent click propagation
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color(0xff333333))
                    ) {
                    	Box(modifier = Modifier.weight(.9f).fillMaxSize().padding(15.dp)){
	                    	if(vnum > 0){
		                        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(15.dp), verticalArrangement = Arrangement.spacedBy(15.dp)){
		                			items(vnum){ index ->
		                				Column(verticalArrangement=Arrangement.spacedBy(10.dp), horizontalAlignment=Alignment.CenterHorizontally){
			                				Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f/0.65f).clip(RoundedCornerShape(8.dp))){
			                					var path by remember(index) { mutableStateOf<Path?>(null) }

											    LaunchedEffect(index) {
											        path = downloadFileToTempDir(
											            "http://localhost:5000/files/${index}.jpg",
											            "${index}.jpg"
											        )
											    }

											    val bitmap = remember(path) {
											        path?.let { filePath ->
											            java.nio.file.Files.newInputStream(filePath).use { stream ->
											                androidx.compose.ui.res.loadImageBitmap(stream)
											            }
											        }
											    }

											    bitmap?.let {
				                					Image(
												        bitmap = it,
												        contentDescription = "Loaded from remote URL",
												        contentScale = ContentScale.Fit,
												        modifier=Modifier.fillMaxSize().background(Color.Black)
												    )
												    Row(modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp), horizontalArrangement=Arrangement.End){
												    	IconButton(
								                            colors = IconButtonDefaults.iconButtonColors(
								                                containerColor = Color.White.copy(alpha=0.3f),
								                                contentColor = Color.Black
								                            ),
								                            onClick = {
								                            	openImageInSystemViewer(path!!)
								                            }
								                        ) {
								                            Icon(
				                                                painter = painterResource("icons/full_screen.svg"),
				                                                modifier=Modifier.size(20.dp),
				                                                tint = Color.Black,
				                                                contentDescription = null
				                                            )
								                        }
												    }
												}
			                				}
			                				var cam = mainData.getJSONArray(index)
					                        val dateTime = java.time.Instant.ofEpochSecond(cam.getLong(1)).atZone(java.time.ZoneId.systemDefault())
						                    val formattedDate = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
						                    val formattedTime = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
					                        Text(text="${cam.getString(0)}, $formattedDate | $formattedTime", fontSize=12.sp, fontFamily=InterFont)
					                    }
		                			}
		                		}
		                	}
		                	else{
		                		Column(modifier=Modifier.align(Alignment.Center), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(20.dp)){
		                    		Text(text=if(vnum == 0) "No results." else "Processing your request..", fontSize=16.sp, color=Color(0xffcccccc))
		                    		if(vnum != 0){
			                    		CircularProgressIndicator(
									        modifier = Modifier.size(30.dp),
									        color = Color(0xffcccccc),
									        strokeWidth = 4.dp
									    )
									}
		                    	}
		                	}
	                	}
                		Row(modifier=Modifier.fillMaxWidth().weight(.1f).background(Color(0xff1a1a1a)), horizontalArrangement=Arrangement.Center, verticalAlignment=Alignment.CenterVertically){
                			Text(text=if(facedt && vnum > 0) "$vnum results" else if(vnum > 0) "$vnum vechiles detected" else "", color=Color.White, fontSize=14.sp)
                		}
                    }
                }
            }
        }
	}
}