package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startSvc("listen") }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("j", MODE_PRIVATE)
        setContent { Screen(p, { startSvc("talk") }, { startSvc("listen") }) }
        perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun startSvc(a: String) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startForegroundService(Intent(this, JarvisService::class.java).setAction(a))
    }
}

@Composable
fun Screen(p: SharedPreferences, onTalk: () -> Unit, onSaved: () -> Unit) {
    var gk by remember { mutableStateOf(p.getString("gk", "") ?: "") }
    var pk by remember { mutableStateOf(p.getString("pk", "") ?: "") }
    var show by remember { mutableStateOf(gk.isBlank()) }
    val st by Jarvis.state.collectAsState()
    val label = when (st) { "idle" -> "Say “Jarvis” or tap the orb"; "connecting" -> "Connecting…"; "listening" -> "Listening"; "speaking" -> "Speaking"; else -> st }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A1D21), Color(0xFF0D0F11)))), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NeuronBall(Modifier.size(300.dp).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTalk() })
                Spacer(Modifier.height(24.dp))
                Text(label, color = Color(0xFF9AA7B2), fontSize = 15.sp)
            }
            if (show) Column(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
                OutlinedTextField(gk, { gk = it }, Modifier.fillMaxWidth(), label = { Text("Gemini API key") }, singleLine = true)
                OutlinedTextField(pk, { pk = it }, Modifier.fillMaxWidth(), label = { Text("Picovoice key (wake word)") }, singleLine = true)
                Button({ p.edit().putString("gk", gk.trim()).putString("pk", pk.trim()).apply(); show = false; onSaved() }) { Text("Save") }
            } else TextButton({ show = true }, Modifier.align(Alignment.BottomEnd)) { Text("API keys") }
        }
    }
}

@Composable
fun NeuronBall(mod: Modifier) {
    val n = 48
    val pts = remember { List(n) { i -> val y = 1f - 2f * (i + 0.5f) / n; val r = sqrt(1f - y * y); val a = i * 2.3999632f; floatArrayOf(cos(a) * r, y, sin(a) * r) } }
    val edges = remember { buildList { for (i in 0 until n) for (j in i + 1 until n) { val a = pts[i]; val b = pts[j]; val d = (a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2); if (d < 0.5f) add(i to j) } } }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { t = it / 1e9f } }
    val lv by Jarvis.level.collectAsState()
    val st by Jarvis.state.collectAsState()
    val active = st == "listening" || st == "speaking" || st == "connecting"
    val energy by animateFloatAsState(if (active) 0.12f + lv * 0.3f else 0f, spring(stiffness = 120f), label = "e")
    val tint by animateColorAsState(if (active) Color(0xFF5EE0FF) else Color(0xFF7C8A96), tween(600), label = "c")
    Canvas(mod) {
        val c = center; val R = size.minDimension / 2 * (0.6f + energy)
        val ay = t * (0.3f + energy); val ax = t * 0.17f
        val cy = cos(ay); val sy = sin(ay); val cx = cos(ax); val sx = sin(ax)
        val xs = FloatArray(n); val ys = FloatArray(n); val zs = FloatArray(n)
        for (i in 0 until n) {
            val p = pts[i]
            val x1 = p[0] * cy + p[2] * sy; val z1 = -p[0] * sy + p[2] * cy
            val y2 = p[1] * cx - z1 * sx; val z2 = p[1] * sx + z1 * cx
            val s = 1f + z2 * 0.22f
            xs[i] = c.x + x1 * R * s; ys[i] = c.y + y2 * R * s; zs[i] = z2
        }
        drawCircle(tint.copy(alpha = 0.05f + energy * 0.2f), R * 1.25f, c)
        for ((i, j) in edges) drawLine(tint.copy(alpha = (0.12f + (zs[i] + zs[j] + 2f) * 0.1f).coerceIn(0f, 1f)), Offset(xs[i], ys[i]), Offset(xs[j], ys[j]), 1.2f)
        for (i in 0 until n) {
            val d = (zs[i] + 1f) / 2f; val o = Offset(xs[i], ys[i])
            drawCircle(tint.copy(alpha = 0.15f + d * 0.25f), 9f + lv * 6f, o)
            drawCircle(tint.copy(alpha = 0.5f + d * 0.5f), 2.5f + d * 2.5f + lv * 3f, o)
        }
    }
}
