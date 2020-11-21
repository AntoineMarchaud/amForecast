package com.shadow.forecast.adapter

import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.databinding.BindingAdapter

interface OnTextViewKeyboardListener {
    fun onKeyboardGo()
    fun onKeyboardSearch()
    fun onKeyboardDone()
}

object TextViewKeyboardBindingAdapter {
    @JvmStatic
    @BindingAdapter("onActionKeyboard")
    fun setOnActionKeyboard(view: TextView, listener: OnTextViewKeyboardListener?) {
        if (listener == null) {
            view.setOnEditorActionListener(null)
        } else {
            view.setOnEditorActionListener { v, actionId, event ->
                when (actionId) {
                    EditorInfo.IME_ACTION_SEARCH -> {
                        listener.onKeyboardSearch()
                        true
                    }
                    EditorInfo.IME_ACTION_GO -> {
                        listener.onKeyboardGo()
                        true
                    }
                    EditorInfo.IME_ACTION_DONE -> {
                        listener.onKeyboardDone()
                        true
                    }
                    else -> false
                }
            }
        }
    }
}