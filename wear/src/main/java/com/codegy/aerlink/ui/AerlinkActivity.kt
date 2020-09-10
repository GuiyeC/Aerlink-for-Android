package com.codegy.aerlink.ui

import android.util.Log
import android.view.View
import com.codegy.aerlink.R
import com.codegy.aerlink.connection.ConnectionState
import kotlinx.android.synthetic.main.merge_connection_info.*
import kotlinx.android.synthetic.main.merge_error.*
import kotlinx.android.synthetic.main.merge_loading.*

abstract class AerlinkActivity : ServiceActivity() {
    override fun updateInterfaceForState(state: ConnectionState) {
        Log.d(LOG_TAG, "updateInterfaceForState :: state: $state")
        val connectionInfoView = connectionInfoView ?: return
        val connectionInfoTextView = connectionInfoTextView ?: return

        runOnUiThread {
            if (state == ConnectionState.Ready) {
                connectionInfoView.visibility = View.GONE
                return@runOnUiThread
            }

            connectionInfoView.visibility = View.VISIBLE
            when (state) {
                ConnectionState.Ready -> {}
                ConnectionState.Connecting -> {
                    connectionInfoTextView.setTextColor(getColor(R.color.connection_state_connecting))
                    connectionInfoTextView.text = getString(R.string.connection_state_connecting)
                    connectionInfoHelpTextView?.text = getString(R.string.connection_state_disconnected_help)
                }
                ConnectionState.Bonding -> {
                    connectionInfoTextView.setTextColor(getColor(R.color.connection_state_bonding))
                    connectionInfoTextView.text = getString(R.string.connection_state_bonding)
                    connectionInfoHelpTextView?.text = getString(R.string.connection_state_disconnected_help)
                }
                ConnectionState.Disconnected -> {
                    connectionInfoTextView.setTextColor(getColor(R.color.connection_state_disconnected))
                    connectionInfoTextView.text = getString(R.string.connection_state_disconnected)
                    connectionInfoHelpTextView?.text = getString(R.string.connection_state_disconnected_help)
                }
                ConnectionState.Stopped -> {
                    connectionInfoTextView.setTextColor(getColor(R.color.connection_state_stopped))
                    connectionInfoTextView.text = getString(R.string.connection_state_stopped)
                    connectionInfoHelpTextView?.text = getString(R.string.connection_state_stopped_help)
                }
            }
        }
    }

    fun showLoading(visible: Boolean) {
        Log.d(LOG_TAG, "showLoading :: Visible: $visible")
        val loadingView = loadingView ?: return
        if (visible && loadingView.visibility == View.VISIBLE
                || !visible && loadingView.visibility == View.GONE) {
            return
        }
        runOnUiThread {
            loadingView.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    fun showError(visible: Boolean) {
        Log.d(LOG_TAG, "showError :: Visible: $visible")
        runOnUiThread {
            errorView?.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    fun restartConnectionAction(view: View) {
        stopService()
        startService()
    }

    companion object {
        private val LOG_TAG: String = AerlinkActivity::class.java.simpleName
    }
}