
package com.smsguard.app

import android.content.Context

object SmsClassifierEngine {

    private var classifier: OnnxSmsClassifier? = null

    @Synchronized
    fun analyze(
        context: Context,
        message: String
    ): SmsPrediction {

        val activeClassifier = classifier
            ?: OnnxSmsClassifier(
                context.applicationContext
            ).also {
                classifier = it
            }

        return activeClassifier.analyze(message)
    }
}
