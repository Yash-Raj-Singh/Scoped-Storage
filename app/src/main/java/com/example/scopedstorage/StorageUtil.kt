package com.example.scopedstorage

import android.os.Build

inline fun <T> sdk29AndUp(onSDK29: () -> T): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
        onSDK29()
    }else{
        null
    }
}