package com.vsoininen.b6companion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.vsoininen.b6companion.model.CurvePoint

@Composable
fun ChargingCurveChart(
    curvePoints: List<CurvePoint>,
    currentTimeMinutes: Double,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(curvePoints) {
        if (curvePoints.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = curvePoints.map { it.timeMinutes },
                    y = curvePoints.map { it.voltage }
                )
            }
        }
    }

    // Y range: pad below the min voltage so the curve fills the chart height
    val minVoltage = curvePoints.minOfOrNull { it.voltage } ?: 3.0
    val yMin = (minVoltage - 0.05).coerceAtLeast(2.5)
    val yMax = 4.25

    val lineColor = MaterialTheme.colorScheme.primary
    val bottomFormatter = CartesianValueFormatter { _, value, _ ->
        "${value.toInt()}m"
    }
    val startFormatter = CartesianValueFormatter { _, value, _ ->
        "%.2fV".format(value)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Charging Curve",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(fill(lineColor))
                            )
                        ),
                        rangeProvider = CartesianLayerRangeProvider.fixed(
                            minY = yMin,
                            maxY = yMax
                        )
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        valueFormatter = startFormatter
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = bottomFormatter
                    )
                ),
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(scrollEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }
    }
}
