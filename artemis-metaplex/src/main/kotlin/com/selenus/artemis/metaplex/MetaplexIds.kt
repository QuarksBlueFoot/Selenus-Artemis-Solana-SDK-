package com.selenus.artemis.metaplex

import com.selenus.artemis.runtime.Pubkey

object MetaplexIds {
  val TOKEN_METADATA_PROGRAM = Pubkey.fromBase58("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s")
  val METADATA_SEED = "metadata".encodeToByteArray()
  val EDITION_SEED = "edition".encodeToByteArray()
}
