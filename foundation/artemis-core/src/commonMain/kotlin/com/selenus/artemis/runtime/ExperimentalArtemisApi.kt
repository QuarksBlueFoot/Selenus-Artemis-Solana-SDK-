package com.selenus.artemis.runtime

/**
 * Marks Artemis APIs that may change in minor releases.
 *
 * This is intentionally lightweight (no opt-in enforcement) to keep mobile build
 * friction low while still making stability boundaries clear for SDK reviewers.
 */
@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "This Artemis API is experimental and may change in future releases."
)
@Retention(AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.CONSTRUCTOR
)
annotation class ExperimentalArtemisApi
