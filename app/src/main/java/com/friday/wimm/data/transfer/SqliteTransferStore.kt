package com.friday.wimm.data.transfer

import android.content.ContentValues
import com.friday.wimm.data.database.DatabaseHelper

/** [TransferStore] 的 SQLite 实现 */
class SqliteTransferStore(private val helper: DatabaseHelper) : TransferStore {

    override fun insert(transfer: PendingTransfer): Long {
        val values = ContentValues().apply {
            put("direction", transfer.direction.value)
            put("amountCents", transfer.amountCents)
            put("merchant", transfer.merchant)
            put("createdAt", transfer.createdAt)
            put("status", transfer.status.value)
            put("source", transfer.source)
            put("note", transfer.note)
        }
        return helper.writableDatabase.insert("pending_transfers", null, values)
    }

    override fun get(id: Long): PendingTransfer? {
        val cursor = helper.readableDatabase.query(
            "pending_transfers", null, "id = ?", arrayOf(id.toString()),
            null, null, null
        )
        var result: PendingTransfer? = null
        if (cursor.moveToFirst()) result = cursor.toTransfer()
        cursor.close()
        return result
    }

    override fun updateStatus(id: Long, status: TransferStatus) {
        val values = ContentValues().apply { put("status", status.value) }
        helper.writableDatabase.update("pending_transfers", values, "id = ?", arrayOf(id.toString()))
    }

    override fun fillAmount(id: Long, amountCents: Long) {
        val values = ContentValues().apply { put("amountCents", amountCents) }
        helper.writableDatabase.update("pending_transfers", values, "id = ?", arrayOf(id.toString()))
    }

    override fun query(
        direction: TransferDirection,
        since: Long,
        status: TransferStatus?
    ): List<PendingTransfer> {
        val selection = buildString {
            append("direction = ? AND createdAt >= ?")
            if (status != null) append(" AND status = ?")
        }
        val args = buildList {
            add(direction.value)
            add(since.toString())
            if (status != null) add(status.value)
        }.toTypedArray()

        val cursor = helper.readableDatabase.query(
            "pending_transfers", null, selection, args, null, null, "createdAt DESC"
        )
        val results = mutableListOf<PendingTransfer>()
        while (cursor.moveToNext()) results.add(cursor.toTransfer())
        cursor.close()
        return results
    }

    private fun android.database.Cursor.toTransfer(): PendingTransfer = PendingTransfer(
        id = getLong(getColumnIndexOrThrow("id")),
        direction = TransferDirection.fromValue(getString(getColumnIndexOrThrow("direction")))
            ?: TransferDirection.OUT,
        amountCents = getLong(getColumnIndexOrThrow("amountCents")),
        merchant = getString(getColumnIndexOrThrow("merchant")) ?: "",
        createdAt = getLong(getColumnIndexOrThrow("createdAt")),
        status = TransferStatus.fromValue(getString(getColumnIndexOrThrow("status"))),
        source = getString(getColumnIndexOrThrow("source")) ?: "",
        note = getString(getColumnIndexOrThrow("note")) ?: ""
    )
}
