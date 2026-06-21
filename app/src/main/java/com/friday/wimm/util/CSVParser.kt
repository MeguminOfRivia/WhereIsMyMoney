package com.friday.wimm.util

import com.friday.wimm.data.model.Transaction
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CSVParser {
    fun parse(inputStream: InputStream): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val reader = BufferedReader(InputStreamReader(inputStream))

        reader.useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    val parts = line.split(",")
                    if (parts.size >= 3) {
                        try {
                            val amount = parts[0].trim().toDoubleOrNull()
                            val merchant = parts[1].trim()
                            val timestamp = System.currentTimeMillis()

                            if (amount != null && merchant.isNotBlank()) {
                                transactions.add(
                                    Transaction(
                                        amount = amount,
                                        merchant = merchant,
                                        source = "csv",
                                        timestamp = timestamp
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // 跳过解析失败的行
                        }
                    }
                }
            }
        }

        return transactions
    }
}
