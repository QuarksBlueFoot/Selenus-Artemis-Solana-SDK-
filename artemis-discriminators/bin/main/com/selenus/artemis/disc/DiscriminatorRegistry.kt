package com.selenus.artemis.disc

import com.selenus.artemis.runtime.Pubkey

/**
 * Versioned discriminator registry.
 *
 * Goal:
 * - Keep method naming differences isolated
 * - Allow multiple builds per program id
 * - Avoid hardcoded byte arrays scattered across modules
 */
class DiscriminatorRegistry private constructor(
  private val map: Map<Key, String>
) {

  data class Key(val programId: Pubkey, val buildTag: String, val logicalOp: String)

  fun methodName(programId: Pubkey, buildTag: String, logicalOp: String): String? {
    return map[Key(programId, buildTag, logicalOp)]
  }

  fun discriminator(programId: Pubkey, buildTag: String, logicalOp: String): ByteArray? {
    val m = methodName(programId, buildTag, logicalOp) ?: return null
    return AnchorDiscriminators.global(m)
  }

  companion object {
    fun builder(): Builder = Builder()
  }

  class Builder {
    private val m = LinkedHashMap<Key, String>()

    fun put(programId: Pubkey, buildTag: String, logicalOp: String, methodName: String): Builder {
      m[Key(programId, buildTag, logicalOp)] = methodName
      return this
    }

    fun build(): DiscriminatorRegistry = DiscriminatorRegistry(m.toMap())
  }
}
