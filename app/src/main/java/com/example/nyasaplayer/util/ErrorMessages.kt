package com.example.nyasaplayer.util

import com.google.firebase.FirebaseNetworkException
import java.net.UnknownHostException

fun Exception.isNetworkError(): Boolean =
    this is FirebaseNetworkException || this is UnknownHostException
