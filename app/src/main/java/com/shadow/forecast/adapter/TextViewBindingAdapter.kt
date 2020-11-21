package com.shadow.forecast.adapter

import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.databinding.BindingAdapter

interface OnTextViewKeyboardListener {
    fun onKeyboardGo(v:TextView)
    fun onKeyboardSearch(v:TextView)
    fun onKeyboardDone(v:TextView)
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
                        listener.onKeyboardSearch(view)
                        true
                    }
                    EditorInfo.IME_ACTION_GO -> {
                        listener.onKeyboardGo(view)
                        true
                    }
                    EditorInfo.IME_ACTION_DONE -> {
                        listener.onKeyboardDone(view)
                        true
                    }
                    else -> false
                }
            }
        }
    }
}