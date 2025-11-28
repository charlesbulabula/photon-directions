package com.neo.maps.ui.common

import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

fun FragmentActivity.toastShort(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun View.show() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}
