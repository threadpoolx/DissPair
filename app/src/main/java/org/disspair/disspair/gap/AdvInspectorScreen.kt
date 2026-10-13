package org.disspair.disspair.gap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.disspair.disspair.ui.Pal

/**
 * GAP advertisement inspector for one device. Reads from [store] twice a second, so it
 * keeps updating while a BLE scan is running and freezes when the scan stops.
 */
@Composable
fun AdvInspectorOverlay(
    mac: String,
    fallbackName: String,
    store: AdvStore,
    scanning: Boolean,
    onClose: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onAnalyseGatt: () -> Unit,
) {
    BackHandler { onClose() }
    var snap by remember { mutableStateOf(store.snapshot(mac)) }
    LaunchedEffect(mac) {
        while (true) { snap = store.snapshot(mac); delay(500) }
    }
    val s = snap
    val parsed = remember(s?.raw?.contentHashCode()) { AdParser.parse(s?.raw) }
    val c = Pal.c

    Box(Modifier.fillMaxSize().background(c.overlay)) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Spacer(Modifier.statusBarsPadding())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ADVERTISING (GAP)", color = c.cyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${s?.name ?: fallbackName} | $mac", color = c.textSecondary, fontSize = 10.sp)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = c.text) }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s != null) {
                    Pill("COPY", Color(0xFF2E7D32)) { onCopy(AdvAnalysis.report(s, parsed)) }
                    Pill("SHARE", Color(0xFF2E7D32)) { onShare(AdvAnalysis.report(s, parsed)) }
                    if (s.connectable != false) Pill("ANALYSE GATT", Color(0xFF0F61B7)) { onAnalyseGatt() }
                }
            }
            if (!scanning) {
                Text("BLE scan is stopped, so values are frozen. Start SCAN BLE to keep collecting.",
                    color = c.amber, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
            }

            if (s == null) {
                Text("No advertisements captured for this device yet.", color = c.textSecondary, fontSize = 12.sp)
                return@Column
            }

            val insights = AdvAnalysis.observationInsights(s) + parsed.insights
            LazyColumn(Modifier.weight(1f)) {
                item { SectionTitle("OVERVIEW") }
                item {
                    Block {
                        Kv("Address", s.address.kind.label + if (s.address.certain) "" else " (unconfirmed)")
                        Kv("", s.address.kind.explain, small = true)
                        s.address.note?.let { Kv("", it, small = true) }
                        Kv("Observed", "${s.packets} packets over ${s.seenForMs / 1000}s")
                        Kv("Interval", s.intervalMs?.let { "~%.0f ms".format(it) } ?: "collecting...")
                        Kv("PDU", listOf(
                            when (s.isLegacy) { true -> "Legacy"; false -> "Extended"; null -> "?" },
                            when (s.connectable) { true -> "connectable"; false -> "non-connectable"; null -> "?" },
                        ).joinToString(", "))
                        Kv("PHY", "${AdvAnalysis.phyName(s.primaryPhy)} / ${AdvAnalysis.phyName(s.secondaryPhy)}")
                        s.txPower?.let { Kv("TX power", "$it dBm") }
                        s.sid?.takeIf { it != 255 }?.let { Kv("Adv SID", "$it") }
                        s.periodicIntervalMs?.let { Kv("Periodic", "%.2f ms".format(it)) }
                        Kv("Payloads", "${s.payloadVariants} distinct")
                    }
                }
                item {
                    SectionTitle("RSSI  " + if (s.rssi.isNotEmpty())
                        "now ${s.rssi.last()}  min ${s.rssi.min()}  max ${s.rssi.max()} dBm" else "")
                }
                item { RssiChart(s.rssi) }

                if (insights.isNotEmpty()) {
                    item { SectionTitle("FINDINGS (${insights.size})") }
                    items(insights) { InsightRow(it) }
                }

                item { SectionTitle("AD STRUCTURES (${parsed.structures.size})") }
                items(parsed.structures) { st ->
                    Block {
                        Text("0x%02X  %s".format(st.type, st.typeName), color = c.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        st.decoded.forEach { Text(it, color = c.green, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                        Text(st.data.hex().ifEmpty { "(no data)" }, color = c.textSubtle, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                parsed.malformedTail?.let { tail ->
                    item { Block { Text("Malformed tail: ${tail.hex()}", color = c.red, fontSize = 10.sp, fontFamily = FontFamily.Monospace) } }
                }
                item { SectionTitle("RAW") }
                item {
                    Block {
                        Text(s.raw?.hex() ?: "-", color = c.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("On Android, advertising data and scan response are merged here.", color = c.textMuted, fontSize = 9.sp)
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun Pill(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(28.dp)
    ) { Text(label, fontSize = 9.sp) }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, color = Pal.c.green, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun Block(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(Pal.c.surfaceAlt, RoundedCornerShape(8.dp)).padding(10.dp),
        content = content
    )
}

@Composable
private fun Kv(k: String, v: String, small: Boolean = false) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(k, color = Pal.c.textMuted, fontSize = 10.sp, modifier = Modifier.width(72.dp))
        Text(v, color = if (small) Pal.c.textSecondary else Pal.c.text, fontSize = if (small) 9.sp else 11.sp)
    }
}

@Composable
private fun InsightRow(i: Insight) {
    val c = Pal.c
    val color = when (i.severity) { Severity.WARN -> c.red; Severity.NOTE -> c.amber; Severity.INFO -> c.cyan }
    Block {
        Text(i.title, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(i.detail, color = c.textSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun RssiChart(values: List<Int>) {
    val c = Pal.c
    Canvas(
        Modifier.fillMaxWidth().height(56.dp)
            .background(c.console, RoundedCornerShape(8.dp))
            .border(1.dp, c.border, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        if (values.size < 2) return@Canvas
        val lo = -100f; val hi = -20f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * (1f - (v.coerceIn(-100, -20) - lo) / (hi - lo))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = c.green, style = Stroke(width = 2.dp.toPx()))
    }
}
