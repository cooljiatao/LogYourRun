package com.jiatao.logyourrun

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 网页与 Android 原生之间的桥梁（挂载为 window.LogYourRunAndroid）。
 *
 * - exportBackup：把备份 JSON 写到用户选择的文件夹里
 * - importBackup：让用户挑选备份文件，读成文本后回传网页
 *
 * WebView 的 JS 接口调用不在主线程，因此所有 Activity 相关操作都切回主线程执行。
 */
class BackupBridge(private val activity: MainActivity) {

  companion object {
    const val REQ_EXPORT = 1001
    const val REQ_IMPORT = 1002
  }

  private var pendingName: String = "跑步打卡备份.json"
  private var pendingJson: String = ""

  @JavascriptInterface
  fun exportBackup(fileName: String, json: String) {
    pendingName = fileName
    pendingJson = json
    activity.runOnUiThread {
      val intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
          addCategory(Intent.CATEGORY_OPENABLE)
          type = "application/json"
          putExtra(Intent.EXTRA_TITLE, fileName)
        }
      activity.startActivityForResult(intent, REQ_EXPORT)
    }
  }

  @JavascriptInterface
  fun importBackup() {
    activity.runOnUiThread {
      val intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
          addCategory(Intent.CATEGORY_OPENABLE)
          type = "*/*"
          putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
      activity.startActivityForResult(intent, REQ_IMPORT)
    }
  }

  /** 用户选好文件夹后写入文件；由 MainActivity 回调。 */
  fun onExportResult(resultCode: Int, data: Intent?) {
    val uri = data?.data
    if (resultCode != Activity.RESULT_OK || uri == null) {
      toast("已取消导出")
      return
    }
    try {
      activity.contentResolver.openOutputStream(uri, "wt")?.use { out ->
        out.write(pendingJson.toByteArray(Charsets.UTF_8))
        out.flush()
      } ?: throw IllegalStateException("无法写入所选文件夹")
      toast("备份已保存")
    } catch (e: Exception) {
      toast("导出失败：${e.message ?: "无法写入所选文件夹"}")
    }
  }

  /** 读取用户挑中的备份文件并回传给网页里的 applyBackupText。 */
  fun onImportResult(resultCode: Int, data: Intent?) {
    val uri = data?.data
    if (resultCode != Activity.RESULT_OK || uri == null) {
      toast("已取消导入")
      return
    }
    val text =
      try {
        readText(uri)
      } catch (e: Exception) {
        webEval("window.applyBackupText && window.applyBackupText('', ${quote("读取失败：" + (e.message ?: "未知错误"))});")
        return
      }
    webEval("window.applyBackupText && window.applyBackupText(${quote(text)}, '');")
  }

  /** 选中文件的显示名，供提示使用（目前仅内部保留）。 */
  fun displayNameOf(uri: Uri): String? {
    activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) return c.getString(0)
    }
    return null
  }

  private fun readText(uri: Uri): String {
    val input: InputStream = activity.contentResolver.openInputStream(uri) ?: throw IllegalStateException("无法打开文件")
    return input.use { stream ->
      val buffer = ByteArrayOutputStream()
      val chunk = ByteArray(8192)
      while (true) {
        val n = stream.read(chunk)
        if (n < 0) break
        buffer.write(chunk, 0, n)
      }
      buffer.toString("UTF-8")
    }
  }

  private fun webEval(script: String) {
    activity.evaluateInPage(script)
  }

  /** 把文本安全地包成 JS 字符串字面量。 */
  private fun quote(text: String): String {
    val sb = StringBuilder("\"")
    for (ch in text) {
      when (ch) {
        '\\' -> sb.append("\\\\")
        '"' -> sb.append("\\\"")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        '\t' -> sb.append("\\t")
        ' ' -> sb.append("\\u2028")
        ' ' -> sb.append("\\u2029")
        else -> if (ch < ' ') sb.append(String.format("\\u%04x", ch.code)) else sb.append(ch)
      }
    }
    return sb.append('"').toString()
  }

  private fun toast(msg: String) {
    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
  }
}
