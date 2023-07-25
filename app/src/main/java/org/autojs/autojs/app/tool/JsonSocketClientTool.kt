package org.autojs.autojs.app.tool

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputFilter
import android.view.KeyEvent
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs.pluginclient.JsonSocketClient
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.ui.main.drawer.DrawerMenuDisposableItem
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.inrt.R

class JsonSocketClientTool(context: Context) : AbstractJsonSocketTool(context) {

    private var mClientModeItem: DrawerMenuDisposableItem? = null

    override val isConnected
        get() = devPlugin.isJsonSocketClientConnected

    private var isNormallyClosed
        get() = devPlugin.isClientSocketNormallyClosed
        set(state) {
            devPlugin.isClientSocketNormallyClosed = state
        }

    override val isInMainThread = true

    override fun connect() {
        inputRemoteHost(isAutoConnect = !isNormallyClosed)
    }

    internal fun connectIfNotNormallyClosed() {
        if (!isNormallyClosed) connect()
    }

    internal fun setClientModeItem(clientModeItem: DrawerMenuDisposableItem) {
        mClientModeItem = clientModeItem
    }

    override fun disconnect() {
        mClientModeItem?.subtitle = null
        devPlugin.disconnectJsonSocketClient()
        isNormallyClosed = true
    }

    override fun dispose() {
        stateDisposable?.dispose()
    }

    @SuppressLint("CheckResult")
    private fun inputRemoteHost(isAutoConnect: Boolean) {
        val host = Pref.getServerAddress()
        if (isAutoConnect) {
            devPlugin
                .connectToRemoteServer(host, mClientModeItem, true)
                .subscribe(Observers.emptyConsumer(), Observers.emptyConsumer())
            return
        }
        MaterialDialog.Builder(context)
            .title(R.string.text_pc_server_address)
            .input(context.getString(R.string.text_pc_server_address), host) { dialog, _ ->
                connectToRemoteServer(dialog)
            }
            .neutralText(R.string.dialog_button_history)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { dialog, _ ->
                MaterialDialog.Builder(context)
                    .title(R.string.text_histories)
                    .content(R.string.text_no_histories)
                    .items(JsonSocketClient.serverAddressHistories)
                    .itemsCallback { dHistories, _, _, text ->
                        dHistories.dismiss()
                        dialog.inputEditText?.setText(text)
                        connectToRemoteServer(dialog)
                    }
                    .itemsLongCallback { dHistories, _, _, text ->
                        false.also {
                            MaterialDialog.Builder(context)
                                .title(R.string.text_prompt)
                                .content(R.string.text_confirm_to_delete)
                                .negativeText(R.string.dialog_button_cancel)
                                .positiveText(R.string.dialog_button_confirm)
                                .positiveColorRes(R.color.dialog_button_caution)
                                .onPositive { ds, _ ->
                                    ds.dismiss()
                                    JsonSocketClient.removeFromHistories(text.toString())
                                    dHistories.items?.let {
                                        it.remove(text)
                                        dHistories.notifyItemsChanged()
                                        DialogUtils.toggleContentViewByItems(dHistories)
                                    }
                                }
                                .show()
                        }
                    }
                    .negativeText(R.string.dialog_button_back)
                    .negativeColorRes(R.color.dialog_button_default)
                    .onNegative { dHistories, _ -> dHistories.dismiss() }
                    .autoDismiss(false)
                    .show()
                    .also { DialogUtils.toggleContentViewByItems(it) }
            }
            .negativeText(R.string.text_back)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .autoDismiss(false)
            .dismissListener(onConnectionDialogDismissed)
            .show()
            .also { dialog: MaterialDialog ->
                dialog.setOnKeyListener { _, keyCode, _ ->
                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        connectToRemoteServer(dialog)
                        true
                    } else {
                        false
                    }
                }
                dialog.inputEditText!!.filters += InputFilter { source, start, end, dest, dstart, dend ->
                    if (end > start) {
                        val fullText = dest.substring(0, dstart) +
                                       source.subSequence(start, end) +
                                       dest.substring(dend)
                        if (dstart > 0) {
                            val prevNearest = dest[dstart - 1]
                            if (Regex(rexDot).matches(prevNearest.toString()) && Regex(rexDot).matches(source)) {
                                showSnack(dialog, R.string.error_repeated_dot_symbol)
                                return@InputFilter ""
                            }
                            if (Regex(rexColon).matches(prevNearest.toString()) && Regex(rexColon).matches(source)) {
                                showSnack(dialog, R.string.error_repeated_colon_symbol)
                                return@InputFilter ""
                            }
                        }
                        if (!rexAcceptable.matches(fullText)) {
                            showSnack(dialog, R.string.error_unacceptable_character)
                            return@InputFilter ""
                        }
                        if (!fullText.contains(rexPartialIp)) {
                            showSnack(dialog, R.string.error_invalid_ip_address)
                            return@InputFilter ""
                        }
                        if (!fullText.contains(Regex(rexColon))) {
                            fullText.split(Regex(rexDot)).dropLastWhile { it.isEmpty() }.forEach { s ->
                                if (s.toIntOrNull()?.let { it <= 255 } != true) {
                                    showSnack(dialog, R.string.error_dot_decimal_notation_num_over_255)
                                    return@InputFilter ""
                                }
                            }
                        } else {
                            if (!fullText.matches(rexFullIpWithColon)) {
                                if (!dest.substring(0, dstart).contains(Regex(rexColon)) && dend == dest.length) {
                                    showSnack(dialog, R.string.error_colon_must_follow_a_valid_ip_address)
                                } else {
                                    showSnack(dialog, R.string.error_invalid_ip_address)
                                }
                                return@InputFilter ""
                            }
                            fullText.split(Regex("$rexDot|$rexColon")).dropLastWhile { it.isEmpty() }.forEachIndexed { index, s ->
                                if (index < 4 && s.toIntOrNull()?.let { it <= 255 } != true) {
                                    showSnack(dialog, R.string.error_dot_decimal_notation_num_over_255)
                                    return@InputFilter ""
                                }
                                if (index >= 4 && s.toIntOrNull()?.let { it <= 65535 } != true) {
                                    showSnack(dialog, R.string.error_port_num_over_65535)
                                    return@InputFilter ""
                                }
                            }
                        }
                    }
                    return@InputFilter source
                        .replace(Regex("$rexDot+"), ".")
                        .replace(Regex("$rexColon+"), ":")
                }
            }
    }

    private fun showSnack(dialog: MaterialDialog, strRes: Int) {
        ViewUtils.showSnack(dialog.view, dialog.context.getString(strRes))
    }

    @SuppressLint("CheckResult")
    private fun connectToRemoteServer(dialog: MaterialDialog) {
        val input = dialog.inputEditText?.text?.toString() ?: ""
        if (!rexValidIp.matches(input)) {
            if (input.isEmpty()) {
                ViewUtils.showSnack(dialog.view, dialog.context.getString(R.string.error_ip_address_should_not_be_empty))
            } else {
                ViewUtils.showSnack(dialog.view, dialog.context.getString(R.string.error_invalid_ip_address))
            }
            return
        }
        dialog.dismiss()
        devPlugin
            .connectToRemoteServer(input, mClientModeItem)
            .subscribe({ Pref.setServerAddress(input) }, onConnectionException)
    }

    companion object {

        const val rexDot = "[,.，。\\u0020]"
        const val rexColon = "[:：]"

        private const val rexIpDec = "\\d{1,3}"
        private const val rexPort = "\\d{1,5}"

        val rexPartialIp = Regex("^$rexIpDec($rexDot($rexIpDec($rexDot($rexIpDec($rexDot($rexIpDec)?)?)?)?)?)?")
        val rexFullIpWithColon = Regex("\\d+$rexDot\\d+$rexDot\\d+$rexDot\\d+$rexColon\\d*")
        val rexValidIp = Regex("$rexIpDec$rexDot$rexIpDec$rexDot$rexIpDec$rexDot$rexIpDec($rexColon$rexPort)?")
        val rexAcceptable = Regex("($rexDot|$rexColon|\\d)+")

    }

}