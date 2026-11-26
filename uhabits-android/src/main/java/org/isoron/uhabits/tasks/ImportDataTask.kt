package org.isoron.uhabits.tasks

import android.util.Log
import org.isoron.platform.io.UserFile
import org.isoron.platform.io.begin
import org.isoron.platform.io.commit
import org.isoron.platform.io.run
import org.isoron.uhabits.core.io.GenericImporter
import org.isoron.uhabits.core.models.ModelFactory
import org.isoron.uhabits.core.models.sqlite.SQLModelFactory
import org.isoron.uhabits.core.tasks.Task

class ImportDataTask(
    private val importer: GenericImporter,
    modelFactory: ModelFactory,
    private val file: UserFile,
    private val listener: Listener
) : Task {
    private var result = 0
    private var diagnostic: String? = null
    private val modelFactory: SQLModelFactory = modelFactory as SQLModelFactory

    override suspend fun doInBackground() {
        var transactionStarted = false
        var stage = "开始数据库事务"
        try {
            modelFactory.database.begin()
            transactionStarted = true

            stage = "识别备份文件"
            if (!importer.canHandle(file)) {
                result = NOT_RECOGNIZED
            } else {
                stage = "读取备份、迁移数据库并写入习惯数据"
                importer.importHabitsFromFile(file)
                result = SUCCESS
            }

            stage = "提交导入数据"
            modelFactory.database.commit()
            transactionStarted = false
        } catch (e: Throwable) {
            result = FAILED
            val causes = generateSequence(e) { it.cause }
                .take(8)
                .joinToString("\n") { cause ->
                    "${cause.javaClass.simpleName}: ${cause.message ?: "没有提供错误详情"}"
                }
            diagnostic = "${stage}\n导入器内部阶段：${importer.lastImportStage}\n${causes}"
            Log.e("ImportDataTask", "Import failed at: ${stage}", e)
            if (transactionStarted) {
                try {
                    modelFactory.database.run("ROLLBACK")
                } catch (rollbackError: Throwable) {
                    Log.e("ImportDataTask", "Rollback failed", rollbackError)
                    diagnostic = "${diagnostic}；回滚也失败：${rollbackError.javaClass.simpleName}"
                }
            }

            // The in-memory HabitList is not part of the SQLite transaction.
            // Reload it after rollback so the UI cannot keep showing uncommitted habits.
            try {
                importer.reloadHabitListCache()
            } catch (reloadError: Throwable) {
                Log.e("ImportDataTask", "Failed to reload habit cache after import failure", reloadError)
                diagnostic = "${diagnostic}；刷新缓存失败：${reloadError.javaClass.simpleName}"
            }
        }
    }

    override fun onPostExecute() {
        listener.onImportDataFinished(result, diagnostic)
    }

    fun interface Listener {
        fun onImportDataFinished(result: Int, diagnostic: String?)
    }

    companion object {
        const val FAILED = 3
        const val NOT_RECOGNIZED = 2
        const val SUCCESS = 1
    }
}
