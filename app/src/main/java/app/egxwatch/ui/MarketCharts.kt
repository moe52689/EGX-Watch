package app.egxwatch.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.egxwatch.data.ChartPoint
import com.patrykandpatrick.vico.compose.cartesian.*
import com.patrykandpatrick.vico.compose.cartesian.axis.*
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.*
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.*
import com.patrykandpatrick.vico.core.cartesian.data.*
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import java.time.*
import java.time.format.DateTimeFormatter

/** Input is a bounded database window; Vico supplies touch inspection, pan and zoom. */
@Composable fun PriceChart(points:List<ChartPoint>,currency:String,modifier:Modifier=Modifier,compact:Boolean=false,
 color:Color=MaterialTheme.colorScheme.primary,overlays:Map<String,Double> = emptyMap(),eventTimes:List<Long> = emptyList(),onEvent:(Long)->Unit = {}) {
 if(points.size<2) {
  Text(if(points.isEmpty()) "Building chart · collecting your first observation" else "First observation collected · chart grows with the next update",modifier,style=MaterialTheme.typography.bodySmall)
  return
 }
 val producer=remember { CartesianChartModelProducer() }
 LaunchedEffect(points,overlays) { producer.runTransaction { lineSeries {
  series(points.map { it.time/60000.0 },points.map { it.price })
  overlays.values.forEach { value -> series(listOf(points.first().time/60000.0,points.last().time/60000.0),listOf(value,value)) }
 } } }
 val zone=ZoneId.systemDefault();val formatter=remember { DateTimeFormatter.ofPattern("dd MMM HH:mm") }
 val label=rememberTextComponent(color=MaterialTheme.colorScheme.onSurface,background=rememberShapeComponent(fill(MaterialTheme.colorScheme.surfaceContainerHigh)))
 val marker=rememberDefaultCartesianMarker(label=label,guideline=rememberAxisGuidelineComponent(),valueFormatter=DefaultCartesianMarker.ValueFormatter { context,targets ->
  val date=targets.firstOrNull()?.x?.let { Instant.ofEpochMilli((it*60000).toLong()).atZone(zone).format(formatter) } ?: ""
  "$date\n${DefaultCartesianMarker.ValueFormatter.default().format(context,targets)} $currency"
 })
 val eventMarker=rememberDefaultCartesianMarker(label=label,guideline=rememberAxisGuidelineComponent(),valueFormatter=DefaultCartesianMarker.ValueFormatter { _,_ -> "◆" })
 val eventCallback by rememberUpdatedState(onEvent)
 val eventListener=remember(points,eventTimes) { object:com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener {
  override fun onShown(marker:com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker,targets:List<com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker.Target>) {
   val x=targets.firstOrNull()?.x ?: return
   eventTimes.firstOrNull { time -> points.minByOrNull { kotlin.math.abs(it.time-time) }?.let { kotlin.math.abs(it.time/60000.0-x)<.001 }==true }?.let(eventCallback)
  }
 } }
 CartesianChartHost(rememberCartesianChart(rememberLineCartesianLayer(lineProvider=LineCartesianLayer.LineProvider.series(
  listOf(LineCartesianLayer.rememberLine(LineCartesianLayer.LineFill.single(fill(color)))) + overlays.keys.map { LineCartesianLayer.rememberLine(LineCartesianLayer.LineFill.single(fill(MarketPalette.analytics())),stroke=LineCartesianLayer.LineStroke.dashed()) })),
  startAxis=if(compact) null else VerticalAxis.rememberStart(),
  bottomAxis=if(compact) null else HorizontalAxis.rememberBottom(valueFormatter=CartesianValueFormatter { _,value,_->
   Instant.ofEpochMilli((value*60000).toLong()).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) }),
  marker=if(compact) null else marker,markerVisibilityListener=if(compact) null else eventListener,persistentMarkers={ if(!compact) eventTimes.takeLast(20).forEach { time ->
   points.minByOrNull { kotlin.math.abs(it.time-time) }?.let { eventMarker at (it.time/60000.0) }
  } }),producer,modifier.fillMaxWidth().height(if(compact) 56.dp else 240.dp).semantics {
   contentDescription="Collected price chart, ${points.size} displayed points. Latest ${points.last().price} $currency. Touch to inspect; pinch to zoom."
  },scrollState=rememberVicoScrollState(scrollEnabled=!compact))
}
