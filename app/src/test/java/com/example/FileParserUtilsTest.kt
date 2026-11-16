package com.example

import com.example.utils.FileParserUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileParserUtilsTest {

    @Test
    fun parseCsvSummary_validCsv_computesStatsCorrectly() {
        val csv = """
            Bulan,Penjualan,Biaya
            Jan,100,40
            Feb,200,80
            Mar,300,120
        """.trimIndent()

        val summary = FileParserUtils.tryParseDataSummary("Penjualan Q1", csv)
        assertNotNull(summary)
        assertEquals(3, summary!!.rowCount)
        assertEquals(3, summary.columnCount)
        assertEquals("Penjualan", summary.numericColumnName)
        assertEquals(100.0, summary.minVal, 0.01)
        assertEquals(300.0, summary.maxVal, 0.01)
        assertEquals(200.0, summary.avgVal, 0.01)
        assertEquals(600.0, summary.sumVal, 0.01)
    }

    @Test
    fun parseJsonArraySummary_validJson_computesStatsCorrectly() {
        val json = """
            [
              {"region": "Jakarta", "revenue": 500},
              {"region": "Bandung", "revenue": 300}
            ]
        """.trimIndent()

        val summary = FileParserUtils.tryParseDataSummary("Regional Revenue", json)
        assertNotNull(summary)
        assertEquals(2, summary!!.rowCount)
        assertEquals("revenue", summary.numericColumnName)
        assertEquals(400.0, summary.avgVal, 0.01)
    }
}
