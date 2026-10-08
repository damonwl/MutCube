package com.dwl.mutcube.ui

/** RecognitionService reports an Android error code, not an HTTP status or response body. */
internal fun systemRecognitionErrorDescription(code: Int): String = when (code) {
    1 -> "系统语音识别网络超时（ERROR_NETWORK_TIMEOUT，代码 1）"
    2 -> "系统语音识别网络失败（ERROR_NETWORK，代码 2）"
    else -> "系统语音识别失败（代码 $code）"
}
