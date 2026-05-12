package com.selenus.artemis.errors

enum class ArtemisErrorCategory {
  NETWORK,
  RATE_LIMIT,
  NODE_HEALTH,
  BLOCKHASH,
  SIMULATION,
  PROGRAM,
  FUNDS,
  TRANSACTION,
  WALLET,
  UNKNOWN
}

data class DecodedSolanaError(
  val category: ArtemisErrorCategory,
  val summary: String,
  val message: String,
  val rpcCode: Int? = null,
  val instructionIndex: Int? = null,
  val programId: String? = null,
  val customProgramErrorCode: Long? = null,
  val customProgramErrorCodeHex: String? = null,
  val logs: List<String> = emptyList(),
  val retryable: Boolean = false
)

object SolanaErrorDecoder {
  fun decodeThrowable(
    throwable: Throwable,
    logs: List<String> = emptyList(),
    rpcCode: Int? = null,
    data: String? = null
  ): DecodedSolanaError = decodeRpc(
    rpcCode = rpcCode,
    message = throwable.message ?: throwable::class.simpleName.orEmpty(),
    logs = logs,
    data = data
  )

  fun decodeRpc(
    rpcCode: Int? = null,
    message: String,
    logs: List<String> = emptyList(),
    data: String? = null
  ): DecodedSolanaError {
    val text = buildString {
      append(message)
      if (!data.isNullOrBlank()) {
        append('\n')
        append(data)
      }
      logs.forEach {
        append('\n')
        append(it)
      }
    }
    val lower = text.lowercase()
    val indexedCustom = instructionErrorCustomRegex.find(text)
    val jsonIndexedCustom = jsonInstructionErrorCustomRegex.find(text)
    val customToken = customProgramErrorRegex.find(text)?.groupValues?.getOrNull(1)
    val customCode = indexedCustom?.groupValues?.getOrNull(2)?.toLongOrNull()
      ?: jsonIndexedCustom?.groupValues?.getOrNull(2)?.toLongOrNull()
      ?: customToken?.let(::parseCustomCode)
    val instructionIndex = indexedCustom?.groupValues?.getOrNull(1)?.toIntOrNull()
      ?: jsonIndexedCustom?.groupValues?.getOrNull(1)?.toIntOrNull()
      ?: instructionIndexRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val programId = extractProgramId(logs)
    val customHex = customCode?.let { "0x${it.toString(16)}" }

    val category = when {
      customCode != null || (lower.contains("instructionerror") && lower.contains("custom")) ->
        ArtemisErrorCategory.PROGRAM
      lower.contains("insufficient funds") || lower.contains("insufficient lamports") ->
        ArtemisErrorCategory.FUNDS
      lower.contains("blockhash not found") || (lower.contains("blockhash") && lower.contains("expired")) ->
        ArtemisErrorCategory.BLOCKHASH
      rpcCode == 429 || lower.contains("429") || lower.contains("rate limit") || lower.contains("too many requests") ->
        ArtemisErrorCategory.RATE_LIMIT
      lower.contains("timeout") || lower.contains("timed out") ->
        ArtemisErrorCategory.NETWORK
      lower.contains("unhealthy") || lower.contains("gethealth") || lower.contains("node is behind") ->
        ArtemisErrorCategory.NODE_HEALTH
      rpcCode == -32002 || lower.contains("simulation failed") || lower.contains("transaction simulation failed") ->
        ArtemisErrorCategory.SIMULATION
      lower.contains("transaction was not confirmed") || lower.contains("signature verification failed") ||
        lower.contains("already processed") || lower.contains("rejected") ->
        ArtemisErrorCategory.TRANSACTION
      else -> ArtemisErrorCategory.UNKNOWN
    }

    return DecodedSolanaError(
      category = category,
      summary = summarize(category, instructionIndex, programId, customHex, message),
      message = message,
      rpcCode = rpcCode,
      instructionIndex = instructionIndex,
      programId = programId,
      customProgramErrorCode = customCode,
      customProgramErrorCodeHex = customHex,
      logs = logs,
      retryable = category in retryableCategories
    )
  }

  fun decodeSimulation(message: String, logs: List<String>): DecodedSolanaError =
    decodeRpc(rpcCode = -32002, message = message, logs = logs)

  private fun parseCustomCode(token: String): Long? = when {
    token.startsWith("0x", ignoreCase = true) -> token.drop(2).toLongOrNull(16)
    else -> token.toLongOrNull()
  }

  private fun extractProgramId(logs: List<String>): String? {
    logs.asReversed().forEach { line ->
      programFailureRegex.find(line)?.groupValues?.getOrNull(1)?.let { return it }
    }
    val errorIndex = logs.indexOfLast { line ->
      customProgramErrorRegex.containsMatchIn(line) || line.contains("failed", ignoreCase = true)
    }
    val candidates = if (errorIndex >= 0) logs.take(errorIndex + 1) else logs
    candidates.asReversed().forEach { line ->
      programInvokeRegex.find(line)?.groupValues?.getOrNull(1)?.let { return it }
    }
    return null
  }

  private fun summarize(
    category: ArtemisErrorCategory,
    instructionIndex: Int?,
    programId: String?,
    customHex: String?,
    fallback: String
  ): String = when (category) {
    ArtemisErrorCategory.PROGRAM -> buildString {
      append("program_error")
      if (instructionIndex != null) append(" instruction=").append(instructionIndex)
      if (programId != null) append(" program=").append(programId)
      if (customHex != null) append(" code=").append(customHex)
    }
    ArtemisErrorCategory.FUNDS -> "insufficient_funds"
    ArtemisErrorCategory.BLOCKHASH -> "blockhash_error"
    ArtemisErrorCategory.RATE_LIMIT -> "rate_limited"
    ArtemisErrorCategory.NETWORK -> "network_timeout"
    ArtemisErrorCategory.NODE_HEALTH -> "node_unhealthy"
    ArtemisErrorCategory.SIMULATION -> "simulation_failed"
    ArtemisErrorCategory.TRANSACTION -> "transaction_rejected"
    ArtemisErrorCategory.WALLET -> "wallet_error"
    ArtemisErrorCategory.UNKNOWN -> fallback.ifBlank { "unknown_error" }
  }

  private val retryableCategories = setOf(
    ArtemisErrorCategory.NETWORK,
    ArtemisErrorCategory.RATE_LIMIT,
    ArtemisErrorCategory.NODE_HEALTH,
    ArtemisErrorCategory.BLOCKHASH
  )

  private val customProgramErrorRegex = Regex(
    pattern = "custom program error:\\s*(0x[0-9a-f]+|[0-9]+)",
    option = RegexOption.IGNORE_CASE
  )
  private val instructionErrorCustomRegex = Regex(
    pattern = "InstructionError\\((\\d+),\\s*Custom\\((\\d+)\\)\\)",
    option = RegexOption.IGNORE_CASE
  )
  private val jsonInstructionErrorCustomRegex = Regex(
    pattern = "[\\\"']?InstructionError[\\\"']?\\s*[:=]\\s*\\[\\s*(\\d+)\\s*,\\s*\\{?\\s*[\\\"']?Custom[\\\"']?\\s*[:=]\\s*(\\d+)",
    option = RegexOption.IGNORE_CASE
  )
  private val instructionIndexRegex = Regex(
    pattern = "(?:Error processing )?Instruction\\s+(\\d+)",
    option = RegexOption.IGNORE_CASE
  )
  private val programFailureRegex = Regex(
    pattern = "Program\\s+([1-9A-HJ-NP-Za-km-z]{32,44})\\s+failed",
    option = RegexOption.IGNORE_CASE
  )
  private val programInvokeRegex = Regex(
    pattern = "Program\\s+([1-9A-HJ-NP-Za-km-z]{32,44})\\s+invoke",
    option = RegexOption.IGNORE_CASE
  )
}