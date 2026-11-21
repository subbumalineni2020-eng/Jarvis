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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.BatteryManager
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val label = when (st) { "idle" -> "SAY “JARVIS” OR TAP THE CORE"; "connecting" -> "ESTABLISHING LINK…"; "listening" -> "LISTENING"; "speaking" -> "SPEAKING"; else -> st }
    val ctx = LocalContext.current
    val bat = remember { ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    var now by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { now = SimpleDateFormat("HH:mm:ss  dd MMM", Locale.getDefault()).format(Date()); delay(1000) } }
    val hc = Color(0xFF5CE1F0)
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF062A33), Color(0xFF01090D))))) {
            Hud(Modifier.fillMaxSize())
            Box(Modifier.size(320.dp).align(Alignment.Center).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTalk() })
            Text("J.A.R.V.I.S", Modifier.align(Alignment.TopStart).padding(top = 52.dp, start = 40.dp), color = hc, fontFamily = FontFamily.Monospace, fontSize = 14.sp, letterSpacing = 4.sp)
            Text(now, Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 40.dp), color = hc, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text(label, Modifier.align(Alignment.TopCenter).padding(top = 100.dp), color = hc.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 3.sp)
            Text("PWR $bat%  ·  NEURAL LINK ONLINE", Modifier.align(Alignment.BottomStart).padding(bottom = 52.dp, start = 40.dp), color = hc.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            if (show) Column(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
                OutlinedTextField(gk, { gk = it }, Modifier.fillMaxWidth(), label = { Text("Gemini API key") }, singleLine = true)
                OutlinedTextField(pk, { pk = it }, Modifier.fillMaxWidth(), label = { Text("Picovoice key (wake word)") }, singleLine = true)
                Button({ p.edit().putString("gk", gk.trim()).putString("pk", pk.trim()).apply(); show = false; onSaved() }) { Text("Save") }
            } else TextButton({ show = true }, Modifier.align(Alignment.BottomEnd).padding(bottom = 40.dp)) { Text("API keys") }
        }
    }
}

@Composable
fun Hud(mod: Modifier) {
    val n = 110
    val jit = remember { val r = kotlin.random.Random(7); List(n) { floatArrayOf(r.nextFloat() * 2f - 1f, 0.6f + r.nextFloat() * 1.6f, 0.3f + r.nextFloat() * 0.7f) } }
    var t by remember { mutableFloatStateOf(0f) }
    var boot by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val s = System.nanoTime(); while (true) withFrameNanos { t = it / 1e9f; boot = ((System.nanoTime() - s) / 2.2e9f).coerceAtMost(1f) } }
    val lv by Jarvis.level.collectAsState()
    val st by Jarvis.state.collectAsState()
    val active = st == "listening" || st == "speaking" || st == "connecting"
    val energy by animateFloatAsState(if (active) 0.1f + lv * 0.32f else 0f, spring(stiffness = 120f), label = "e")
    val tint by animateColorAsState(if (active) Color(0xFF3DF2FF) else Color(0xFF2AA6B8), tween(600), label = "c")
    Canvas(mod) {
        val c = center; val u = size.minDimension; val e = 1f - (1f - boot).pow(3); val a = e
        val amp = 1f + energy * 3.5f
        val R = u * 0.34f * e
        val g = 64f
        var gx = 0f; while (gx < size.width) { drawLine(tint.copy(alpha = 0.04f * a), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += g }
        var gy = 0f; while (gy < size.height) { drawLine(tint.copy(alpha = 0.04f * a), Offset(0f, gy), Offset(size.width, gy), 1f); gy += g }
        val scan = (t * 0.22f % 1f) * size.height
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, tint.copy(alpha = 0.13f * a), Color.Transparent), scan - 90f, scan + 90f), Offset(0f, scan - 90f), Size(size.width, 180f))
        val m = 28f; val L = 64f
        for (sx in listOf(1f, -1f)) for (sy in listOf(1f, -1f)) {
            val ox = if (sx > 0) m else size.width - m; val oy = if (sy > 0) m else size.height - m
            drawLine(tint.copy(alpha = 0.7f * a), Offset(ox, oy), Offset(ox + sx * L, oy), 3f)
            drawLine(tint.copy(alpha = 0.7f * a), Offset(ox, oy), Offset(ox, oy + sy * L), 3f)
        }
        val gl = maxOf(R * 1.7f, 2f); drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = (0.22f + energy * 0.4f) * a), Color.Transparent), c, gl), gl, c)
        // hologram globe
        val gr = R * (0.42f + energy * 0.25f); val ay = t * 0.5f; val tilt = 0.4f
        fun proj(x: Float, y: Float, z: Float): Offset {
            val x1 = x * cos(ay) + z * sin(ay); val z1 = -x * sin(ay) + z * cos(ay)
            return Offset(c.x + x1 * gr, c.y + (y * cos(tilt) - z1 * sin(tilt)) * gr)
        }
        fun curve(al: Float, f: (Float) -> Offset) {
            val pa = Path()
            for (i in 0..40) { val o = f(i * 6.2831855f / 40); if (i == 0) pa.moveTo(o.x, o.y) else pa.lineTo(o.x, o.y) }
            drawPath(pa, tint.copy(alpha = al * a), style = Stroke(1f))
        }
        for (k in -2..2) { val la = k * 0.5f; val rr = cos(la); val yy = sin(la); curve(0.4f) { s -> proj(rr * cos(s), yy, rr * sin(s)) } }
        for (k in 0 until 6) { val ph = k * 0.5236f; curve(0.3f) { s -> proj(cos(ph) * cos(s), sin(s), sin(ph) * cos(s)) } }
        drawCircle(tint.copy(alpha = 0.75f * a), gr, c, style = Stroke(1.5f))
        drawCircle(tint.copy(alpha = 0.6f * a), gr * 0.1f, c, blendMode = BlendMode.Plus)
        // tick ring
        for (i in 0 until 72) {
            val ang = i * 0.087266f + t * 0.15f; val len = if (i % 6 == 0) 16f else 7f; val r1 = R * 0.78f
            drawLine(tint.copy(alpha = 0.5f * a), Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1), Offset(c.x + cos(ang) * (r1 + len), c.y + sin(ang) * (r1 + len)), 1.5f)
        }
        drawCircle(tint.copy(alpha = 0.35f * a), R * 0.88f, c, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 12f), t * 20f)))
        // rotating arcs with glow
        fun arc(rad: Float, st0: Float, sw: Float, w: Float) {
            val tl = Offset(c.x - rad, c.y - rad); val sz = Size(rad * 2, rad * 2)
            drawArc(tint.copy(alpha = 0.14f * a), st0, sw, false, tl, sz, style = Stroke(w * 4))
            drawArc(tint.copy(alpha = 0.9f * a), st0, sw, false, tl, sz, style = Stroke(w))
        }
        for (k in 0 until 3) arc(R * 0.96f, -t * 40f + k * 120f, 70f + energy * 60f, 3f)
        for (k in 0 until 2) arc(R * 0.68f, t * 70f + k * 180f, 50f, 2f)
        // circular voice visualizer
        for (i in 0 until 72) {
            val ang = i * 0.087266f; val bl = 3f + lv * u * 0.06f * (0.5f + 0.5f * sin(i * 1.7f + t * 9f)); val r1 = R * 1.04f
            drawLine(tint.copy(alpha = (if (active) 0.85f else 0.25f) * a), Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1), Offset(c.x + cos(ang) * (r1 + bl), c.y + sin(ang) * (r1 + bl)), 2f, blendMode = BlendMode.Plus)
        }
        // wavy particle ring
        val wr = R * 1.2f; val wp = Path()
        for (q in 0 until n) {
            val th = q * 6.2831855f / n
            val w = (sin(3 * th + t * 0.9f) * 0.035f + sin(5 * th - t * 1.3f) * 0.022f + sin(2 * th + t * 0.5f) * 0.03f) * amp
            val rb = wr * (1f + w); val px = c.x + cos(th) * rb; val py = c.y + sin(th) * rb
            if (q == 0) wp.moveTo(px, py) else wp.lineTo(px, py)
            val j = jit[q]; val rr = rb + j[0] * u * 0.012f * (1f + energy * 4f)
            drawCircle(tint.copy(alpha = j[2] * a), j[1], Offset(c.x + cos(th) * rr, c.y + sin(th) * rr), blendMode = BlendMode.Plus)
        }
        wp.close(); drawPath(wp, tint.copy(alpha = 0.45f * a), style = Stroke(1.2f))
        drawCircle(tint.copy(alpha = 0.15f * a), R * 1.42f, c, style = Stroke(1f))
    }
}
