package com.yunx.app.data.db

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager

class AccountMigrationTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun preservesOldSingleAccountAndSupportsPrimarySwitchAfterMigration() = runBlocking {
        Class.forName("org.sqlite.JDBC")
        val file = File(tmp.root, "legacy.db")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE quark_account (id TEXT PRIMARY KEY NOT NULL, cookie TEXT NOT NULL, nickname TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                it.execute("INSERT INTO quark_account VALUES ('single', 'old-cookie', '原账号', 1)")
            }
        }
        val db = AppDatabase.openFile(file)
        try {
            val accounts = db.quarkAccountDao()
            val old = accounts.getAccount()!!
            assertEquals("old-cookie", old.cookie)
            assertEquals("原账号", old.nickname)
            assertTrue(old.isPrimary)
            DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT cookie FROM quark_account WHERE id = 'single'").use { row ->
                        assertTrue(row.next()); assertTrue(row.getString(1).startsWith("yunx:v1:"))
                    }
                }
            }
            accounts.upsert(QuarkAccountEntity(id = "second", cookie = "new-cookie", nickname = "新账号", isPrimary = false))
            assertEquals(2, accounts.getAccounts().size)
            accounts.setPrimary("second")
            accounts.updateRemark("second", "工作")
            assertEquals("new-cookie", accounts.getAccount()!!.cookie)
            assertEquals("工作", accounts.getAccount()!!.remark)
            accounts.deleteById("second")
            assertEquals("old-cookie", accounts.getAccount()!!.cookie)
            assertEquals(1, accounts.getAccounts().count { it.isPrimary })
        } finally { db.closeForTest() }
        // Reopen the migrated database: credentials and primary selection survive a restart.
        val reopened = AppDatabase.openFile(file)
        try { assertEquals("old-cookie", reopened.quarkAccountDao().getAccount()!!.cookie) }
        finally { reopened.closeForTest() }
    }
}
