package com.example.ui.components

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.ExeatRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prints the paper copy of an approved exeat pass for the gate and the student to carry.
 *
 * This document is only as trustworthy as the app record behind it: the pass asks the bearer
 * to present the printed copy and asks staff elsewhere to verify the pass with the school if
 * they are in doubt, because the app has no way to hand a guardian-confirmed original to
 * third parties.
 */
fun printExeatPass(
    context: Context,
    exeat: ExeatRequest,
    houseName: String?,
    schoolName: String,
    schoolAddress: String,
    schoolPhone: String
) {
    val timeFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm", Locale.getDefault())
    val printedAt = SimpleDateFormat("d MMMM yyyy 'at' HH:mm", Locale.getDefault()).format(Date())
    val html = """
        <!doctype html>
        <html><head><meta charset="utf-8"><style>
        body{font-family:sans-serif;color:#10243e;padding:26px}
        .card{border:2px solid #163b70;border-radius:16px;max-width:760px;margin:auto;overflow:hidden}
        header{background:#163b70;color:white;padding:18px;text-align:center}
        header .title{font-size:24px;font-weight:bold;letter-spacing:1px}
        header .sub{font-size:13px;margin-top:4px}
        main{padding:22px}
        h2{font-size:18px;margin:0 0 10px}
        p{font-size:15px;margin:8px 0}
        .row{display:flex;gap:18px}
        .row > div{flex:1}
        .pass-id{font-family:monospace;font-weight:bold}
        footer{border-top:1px solid #cbd5e1;padding:14px 22px;color:#475569;font-size:13px}
        .signature{margin-top:34px;border-top:1px dotted #475569;width:260px;padding-top:6px;font-size:13px}
        .notice{padding:14px 22px;background:#fef3c7;color:#7f1d1d;font-size:13px;font-weight:bold}
        </style></head><body><article class="card">
        <header>
            <div class="title">EXEAT GATE PASS</div>
            <div class="sub">${schoolName.escapeHtml()} · ${schoolAddress.escapeHtml()} · ${schoolPhone.escapeHtml()}</div>
        </header>
        <main>
            <h2>${exeat.studentName.escapeHtml()} (${exeat.studentIndexNumber.escapeHtml()})</h2>
            <div class="row"><div>
                <p><b>Pass ID:</b> <span class="pass-id">${exeat.id.escapeHtml()}</span></p>
                <p><b>House:</b> ${(houseName ?: "Unknown").escapeHtml()}</p>
                <p><b>Category:</b> ${exeat.category.escapeHtml()}</p>
            </div><div>
                <p><b>Departure window:</b><br>${timeFormat.format(Date(exeat.departureTime)).escapeHtml()}</p>
                <p><b>Expected return:</b><br>${timeFormat.format(Date(exeat.expectedReturnTime)).escapeHtml()}</p>
            </div></div>
            <p><b>Destination:</b> ${exeat.destination.escapeHtml()}</p>
            <p><b>Reason given:</b> ${exeat.reason.escapeHtml()}</p>
            ${exeat.conditionNotes?.takeIf(String::isNotBlank)?.let {
                "<p><b>Approval conditions:</b> ${it.escapeHtml()}</p>"
            }.orEmpty()}
            <p><b>Status on this printout:</b> ${exeat.status.escapeHtml()}</p>
            <p><b>Approved on the school system by:</b> ${(exeat.approvedBy ?: "Unknown").escapeHtml()}</p>
            <div class="signature">Signature of issuing staff<br><br>__________________________</div>
        </main>
        <div class="notice">
            Gate staff: verify this student and pass against the school system before release.
            If anything looks altered, or the return window has passed, contact the school on
            ${schoolPhone.escapeHtml()}.
        </div>
        <footer>Printed from the school boarding record on $printedAt. This pass records an
        approval made in the Edenest boarding app; it is not proof of parent or guardian consent.</footer>
        </article></body></html>
    """.trimIndent()

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            printManager.print(
                "Exeat pass ${exeat.studentIndexNumber}",
                view.createPrintDocumentAdapter("Exeat pass ${exeat.studentIndexNumber}"),
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
