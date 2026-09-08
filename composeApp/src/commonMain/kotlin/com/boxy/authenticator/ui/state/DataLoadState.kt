package com.boxy.authenticator.ui.state

sealed interface DataLoadState<out T> {
    data object Initial : DataLoadState<Nothing>
    data object Loading : DataLoadState<Nothing>
    data class Data<T>(val value: T) : DataLoadState<T>
    data class Error(val message: String) : DataLoadState<Nothing>
}
