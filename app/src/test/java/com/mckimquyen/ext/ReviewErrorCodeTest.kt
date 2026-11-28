package com.mckimquyen.ext

import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.model.ReviewErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Audit R6: the review listener read `(task.exception as ReviewException).errorCode`. A failed task whose exception is null, or any other type, threw
 * NullPointerException / ClassCastException on the main thread inside the callback (at most once a week, on ActHome start). The helper never throws.
 */
class ReviewErrorCodeTest {

    @Test fun aReviewExceptionGivesItsOwnCode() {
        assertEquals(ReviewErrorCode.PLAY_STORE_NOT_FOUND, reviewErrorCodeOf(ReviewException(ReviewErrorCode.PLAY_STORE_NOT_FOUND)))
    }

    @Test fun anotherCodeIsNotConfusedWithTheFirst() {
        // Positive control: the helper really reads the code, it does not return one fixed value.
        assertEquals(ReviewErrorCode.INTERNAL_ERROR, reviewErrorCodeOf(ReviewException(ReviewErrorCode.INTERNAL_ERROR)))
    }

    @Test fun aNullExceptionGivesTheUnknownCodeInsteadOfThrowing() {
        assertEquals(REVIEW_ERROR_UNKNOWN, reviewErrorCodeOf(null))
    }

    @Test fun anExceptionOfAnotherTypeGivesTheUnknownCodeInsteadOfThrowing() {
        assertEquals(REVIEW_ERROR_UNKNOWN, reviewErrorCodeOf(IOException("network")))
        assertEquals(REVIEW_ERROR_UNKNOWN, reviewErrorCodeOf(IllegalStateException("not signed in")))
    }
}
