package io.github.keithyokoma.gridradiogroup

import android.content.res.TypedArray

/**
 * @author KeithYokoma
 */
internal inline fun <R> TypedArray.use(block: (TypedArray) -> R): R {
    try {
        return block(this)
    } finally {
        this.recycle()
    }
}