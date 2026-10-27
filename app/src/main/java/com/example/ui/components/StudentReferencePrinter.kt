package com.example.ui.components

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.House
import com.example.data.model.Student
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun printStudentReference(
    context: Context,
    student: Student,
    house: House?,
    schoolName: String
) {
    val generatedAt = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        .format(Date())
    val photo = student.photoUrl.takeIf(String::isNotBlank)?.let {
        "<img class=\"photo\" src=\"${it.escapeHtml()}\" alt=\"Student photo\">"
    }.orEmpty()
    val bed = student.bedNumber.takeIf { it > 0 }?.toString() ?: "Not assigned"
    val year = java.time.LocalDate.now().let {
        if (it.monthValue >= 9) it.year else it.year - 1
    }
    val html = """
        <!doctype html>
        <html><head><meta charset="utf-8"><style>
        body{font-family:sans-serif;color:#10243e;padding:28px}
        .card{border:2px solid #163b70;border-radius:16px;max-width:760px;margin:auto;overflow:hidden}
        header{background:#163b70;color:white;padding:18px;font-size:22px;font-weight:bold}
        main{display:flex;gap:22px;padding:24px;align-items:flex-start}
        .photo{width:150px;height:180px;object-fit:cover;border-radius:8px;background:#e2e8f0}
        h1{font-size:26px;margin:0 0 8px}p{font-size:16px;margin:9px 0}
        .notice{border-top:1px solid #cbd5e1;padding:16px;color:#7f1d1d;font-weight:bold}
        .muted{color:#475569;font-size:14px}
        </style></head><body><article class="card">
        <header>${schoolName.escapeHtml()} · Student reference</header>
        <main>$photo<div>
        <h1>${student.fullName.escapeHtml()}</h1>
        <p><b>Student number:</b> ${student.studentId.escapeHtml()}</p>
        <p><b>Reference:</b> ${student.id.escapeHtml()}</p>
        <p><b>Form:</b> ${student.classForm.ifBlank { "Unknown" }.escapeHtml()}</p>
        <p><b>Programme:</b> ${student.program.ifBlank { "Unknown" }.escapeHtml()}</p>
        <p><b>House:</b> ${(house?.name ?: "Unknown").escapeHtml()}</p>
        <p><b>Bed:</b> ${bed.escapeHtml()}</p>
        <p><b>Academic year:</b> $year/${year + 1}</p>
        <p class="muted">Reference generated: $generatedAt</p>
        </div></main>
        <div class="notice">This reference is not a gate pass. Confirm current exeat approval and status with the school before allowing travel.</div>
        </article></body></html>
    """.trimIndent()

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            printManager.print(
                "Student reference ${student.studentId}",
                view.createPrintDocumentAdapter("Student reference ${student.studentId}"),
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .build()
            )
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
