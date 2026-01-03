package com.selenus.artemis.nft

import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.runtime.Pubkey
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NftClient(private val rpc: RpcApi) {

  // --- Metaplex-style convenience methods (indexer-free) ---

  /**
   * Metaplex-style single mint lookup. Returns null if the metadata PDA is missing.
   */
  suspend fun findByMint(mint: Pubkey): Nft? {
    val meta = fetchMetadata(mint) ?: return null
    val edition = fetchMasterEdition(mint)
    return Nft(mint = mint, metadata = meta, masterEdition = edition)
  }

  suspend fun findByMint(mintBase58: String): Nft? = findByMint(Pubkey.fromBase58(mintBase58))

  /**
   * Batch fetch metadata (and master editions when present) for a list of mints.
   */
  suspend fun findAllByMintList(mints: List<Pubkey>): List<Nft> {
    if (mints.isEmpty()) return emptyList()
    val metaKeys = mints.map { Pdas.metadataPda(it).toBase58() }
    val editionKeys = mints.map { Pdas.masterEditionPda(it).toBase58() }
    val allKeys = metaKeys + editionKeys

    val datas = getMultipleAccountsBase64(allKeys)
    val out = ArrayList<Nft>(mints.size)

    for (i in mints.indices) {
      val mint = mints[i]
      val metaData = datas.getOrNull(i) ?: continue
      val metadata = MetadataParser.parse(mint, metaData)
      val editionData = datas.getOrNull(mints.size + i)
      val edition = if (editionData == null) null else MasterEditionParser.parse(mint, editionData)
      out.add(Nft(mint = mint, metadata = metadata, masterEdition = edition))
    }
    return out
  }

  suspend fun findAllByMintListBase58(mintBase58: List<String>): List<Nft> =
    findAllByMintList(mintBase58.map { Pubkey.fromBase58(it) })

  /**
   * Metaplex-style owner lookup (heuristic): token accounts with amount == 1.
   *
   * Note: Without an indexer, this can include 1-of-SFTs and other edge-cases.
   */
  suspend fun findAllByOwner(owner: Pubkey): List<WalletOwnedNft> {
    val wallet = listWalletNfts(owner.toBase58())
    if (wallet.isEmpty()) return emptyList()
    val mints = wallet.map { it.mint }
    val editions = findAllByMintList(mints).associateBy { it.mint }

    return wallet.map {
      val e = editions[it.mint]
      WalletOwnedNft(
        mint = it.mint,
        tokenAccount = it.tokenAccount,
        metadata = it.metadata,
        masterEdition = e?.masterEdition,
      )
    }
  }

  suspend fun findAllByOwner(ownerBase58: String): List<WalletOwnedNft> = findAllByOwner(Pubkey.fromBase58(ownerBase58))

  /**
   * Find NFTs by verified creator using getProgramAccounts + memcmp.
   *
   * This mirrors the classic Metaplex technique and may be heavy on public RPCs.
   * The creator position is 1-indexed (i.e., position=1 is the first creator).
   */
  suspend fun findAllByCreator(creator: Pubkey, position: Int = 1): List<NftMetadata> {
    require(position >= 1) { "position must be >= 1" }

    val creatorOffset = 326 + (position - 1) * 34
    val filters = kotlinx.serialization.json.buildJsonArray {
      add(kotlinx.serialization.json.buildJsonObject {
        put("memcmp", kotlinx.serialization.json.buildJsonObject {
          put("offset", kotlinx.serialization.json.JsonPrimitive(creatorOffset))
          put("bytes", kotlinx.serialization.json.JsonPrimitive(creator.toBase58()))
        })
      })
    }

    val accounts = rpc.getProgramAccounts(
      programId = MetaplexIds.TOKEN_METADATA_PROGRAM.toBase58(),
      commitment = "confirmed",
      encoding = "base64",
      filters = filters,
    )

    val out = ArrayList<NftMetadata>(accounts.size)
    for (acc in accounts) {
      val account = acc.jsonObject["account"]?.jsonObject ?: continue
      val dataArr = account["data"]?.jsonArray ?: continue
      if (dataArr.isEmpty()) continue
      val b64 = dataArr[0].jsonPrimitive.content
      val bytes = java.util.Base64.getDecoder().decode(b64)
      out.add(MetadataParser.parseFromAccount(bytes))
    }
    return out
  }

  suspend fun findAllByCreator(creatorBase58: String, position: Int = 1): List<NftMetadata> =
    findAllByCreator(Pubkey.fromBase58(creatorBase58), position)

  /**
   * Find NFTs minted by a Candy Machine v2 by filtering the creator slot.
   *
   * Candy Machine v2 commonly appears as a creator entry in the metadata creators array.
   * This method is best-effort and does not require an indexer.
   */
  suspend fun findAllByCandyMachineV2(candyMachine: Pubkey, creatorPosition: Int = 1): List<NftMetadata> {
    return findAllByCreator(candyMachine, position = creatorPosition)
  }

  suspend fun fetchMetadata(mintBase58: String): NftMetadata? = fetchMetadata(Pubkey.fromBase58(mintBase58))

  suspend fun fetchMetadata(mint: Pubkey): NftMetadata? {
    val meta = Pdas.metadataPda(mint)
    val bytes = fetchAccountBase64(meta) ?: return null
    return MetadataParser.parse(mint, bytes)
  }

  suspend fun fetchMasterEdition(mint: Pubkey): MasterEdition? {
    val edition = Pdas.masterEditionPda(mint)
    val bytes = fetchAccountBase64(edition) ?: return null
    return MasterEditionParser.parse(mint, bytes)
  }

  suspend fun fetchTokenRecord(mint: Pubkey, tokenAccount: Pubkey): TokenRecord? {
    val record = Pdas.tokenRecordPda(mint, tokenAccount)
    val bytes = fetchAccountBase64(record) ?: return null
    return TokenRecordParser.parse(mint, tokenAccount, bytes)
  }

  suspend fun fetchCollectionAuthorityRecord(collectionMint: Pubkey, authority: Pubkey): CollectionAuthorityRecord? {
    val record = Pdas.collectionAuthorityRecordPda(collectionMint, authority)
    val bytes = fetchAccountBase64(record) ?: return null
    return CollectionAuthorityRecordParser.parse(collectionMint, authority, bytes)
  }

  suspend fun listWalletNfts(ownerBase58: String): List<WalletNft> {
    val owner = Pubkey.fromBase58(ownerBase58)
    val tokenAccounts = rpc.getTokenAccountsByOwner(
      owner = owner.toBase58(),
      programId = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
      commitment = "confirmed"
    )

    val value = tokenAccounts["value"]?.jsonArray ?: return emptyList()
    val candidates = ArrayList<WalletNft>()

    for (item in value) {
      val obj = item.jsonObject
      val tokenAccount = Pubkey.fromBase58(obj["pubkey"]!!.jsonPrimitive.content)
      val parsed = obj["account"]!!.jsonObject["data"]!!.jsonObject["parsed"]!!.jsonObject
      val info = parsed["info"]!!.jsonObject
      val mint = Pubkey.fromBase58(info["mint"]!!.jsonPrimitive.content)
      val amountStr = info["tokenAmount"]!!.jsonObject["amount"]!!.jsonPrimitive.content
      val amount = amountStr.toLong()
      if (amount == 1L) candidates.add(WalletNft(mint, tokenAccount, amount, null))
    }

    if (candidates.isEmpty()) return emptyList()

    val metaKeys = candidates.map { Pdas.metadataPda(it.mint).toBase58() }
    val metas = getMultipleAccountsBase64(metaKeys)

    val out = ArrayList<WalletNft>(candidates.size)
    for (i in candidates.indices) {
      val c = candidates[i]
      val data = metas.getOrNull(i)
      val meta = if (data == null) null else MetadataParser.parse(c.mint, data)
      out.add(c.copy(metadata = meta))
    }
    return out
  }

  private suspend fun fetchAccountBase64(pubkey: Pubkey): ByteArray? {
    val rsp = rpc.getAccountInfo(pubkey.toBase58(), commitment = "confirmed", encoding = "base64")
    val value = rsp["value"] ?: return null
    if (value.toString() == "null") return null
    val dataArr = value.jsonObject["data"]?.jsonArray ?: return null
    if (dataArr.isEmpty()) return null
    val b64 = dataArr[0].jsonPrimitive.content
    return java.util.Base64.getDecoder().decode(b64)
  }

  private suspend fun getMultipleAccountsBase64(pubkeys: List<String>): List<ByteArray?> {
    val rsp = rpc.getMultipleAccounts(pubkeys, commitment = "confirmed", encoding = "base64")
    val value = rsp["value"]?.jsonArray ?: return emptyList()
    val out = ArrayList<ByteArray?>(value.size)
    for (item in value) {
      if (item.toString() == "null") {
        out.add(null); continue
      }
      val dataArr = item.jsonObject["data"]?.jsonArray
      if (dataArr == null || dataArr.isEmpty()) {
        out.add(null); continue
      }
      val b64 = dataArr[0].jsonPrimitive.content
      out.add(java.util.Base64.getDecoder().decode(b64))
    }
    return out
  }
}
