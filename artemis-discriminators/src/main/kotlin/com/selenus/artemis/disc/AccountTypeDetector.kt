package com.selenus.artemis.disc

import com.selenus.artemis.runtime.Pubkey
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * AccountTypeDetector - Comprehensive account type detection via discriminators
 * 
 * Features:
 * - Anchor discriminator detection
 * - SPL program account detection
 * - Metaplex account detection
 * - Custom program registration
 * - Account decoding helpers
 */
object AccountTypeDetector {

    /**
     * Detected account type with metadata
     */
    data class AccountType(
        val programId: Pubkey?,
        val accountName: String,
        val discriminator: ByteArray?,
        val category: AccountCategory,
        val confidence: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AccountType) return false
            return programId == other.programId && accountName == other.accountName
        }

        override fun hashCode(): Int {
            var result = programId?.hashCode() ?: 0
            result = 31 * result + accountName.hashCode()
            return result
        }
    }

    enum class AccountCategory {
        SYSTEM,         // System program accounts
        TOKEN,          // SPL Token accounts
        TOKEN_2022,     // Token 2022 accounts
        NFT,            // NFT-related accounts
        METADATA,       // Metaplex metadata
        DEFI,           // DeFi protocol accounts
        GAMING,         // Gaming-related accounts
        CUSTOM,         // Custom program accounts
        UNKNOWN
    }

    // Known discriminator registry
    private val knownDiscriminators = ConcurrentHashMap<String, AccountType>()
    private val programAccounts = ConcurrentHashMap<Pubkey, List<RegisteredAccount>>()

    data class RegisteredAccount(
        val name: String,
        val discriminator: ByteArray,
        val category: AccountCategory
    )

    init {
        // Register known Anchor discriminators
        registerKnownAccounts()
    }

    /**
     * Detect account type from account data
     */
    fun detect(data: ByteArray, programId: Pubkey? = null): AccountType {
        if (data.isEmpty()) {
            return AccountType(
                programId = programId,
                accountName = "Empty",
                discriminator = null,
                category = AccountCategory.UNKNOWN,
                confidence = 1.0f
            )
        }

        // Try to detect by discriminator (first 8 bytes)
        if (data.size >= 8) {
            val discriminator = data.copyOfRange(0, 8)
            val hexKey = discriminator.toHexString()

            // Check registered discriminators
            knownDiscriminators[hexKey]?.let { return it }

            // Check program-specific accounts
            programId?.let { pid ->
                programAccounts[pid]?.find { it.discriminator.contentEquals(discriminator) }?.let {
                    return AccountType(
                        programId = pid,
                        accountName = it.name,
                        discriminator = discriminator,
                        category = it.category,
                        confidence = 0.95f
                    )
                }
            }
        }

        // Try to detect by data layout patterns
        return detectByLayout(data, programId)
    }

    /**
     * Detect multiple accounts
     */
    fun detectBatch(accounts: List<Pair<ByteArray, Pubkey?>>): List<AccountType> {
        return accounts.map { (data, programId) -> detect(data, programId) }
    }

    /**
     * Register a custom program's account types
     */
    fun registerProgram(
        programId: Pubkey,
        accounts: List<RegisteredAccount>
    ) {
        programAccounts[programId] = accounts
        for (account in accounts) {
            val hexKey = account.discriminator.toHexString()
            knownDiscriminators[hexKey] = AccountType(
                programId = programId,
                accountName = account.name,
                discriminator = account.discriminator,
                category = account.category,
                confidence = 1.0f
            )
        }
    }

    /**
     * Register an Anchor program's accounts
     */
    fun registerAnchorProgram(
        programId: Pubkey,
        accountNames: List<String>,
        category: AccountCategory = AccountCategory.CUSTOM
    ) {
        val accounts = accountNames.map { name ->
            RegisteredAccount(
                name = name,
                discriminator = AnchorDiscriminators.account(name),
                category = category
            )
        }
        registerProgram(programId, accounts)
    }

    /**
     * Check if data matches a specific account type
     */
    fun matches(data: ByteArray, expectedAccountName: String): Boolean {
        if (data.size < 8) return false
        val discriminator = data.copyOfRange(0, 8)
        val expected = AnchorDiscriminators.account(expectedAccountName)
        return discriminator.contentEquals(expected)
    }

    /**
     * Get discriminator for an account name
     */
    fun getDiscriminator(accountName: String): ByteArray {
        return AnchorDiscriminators.account(accountName)
    }

    /**
     * Verify discriminator matches expected
     */
    fun verifyDiscriminator(data: ByteArray, expected: ByteArray): Boolean {
        if (data.size < expected.size) return false
        return data.copyOfRange(0, expected.size).contentEquals(expected)
    }

    private fun detectByLayout(data: ByteArray, programId: Pubkey?): AccountType {
        val size = data.size

        // SPL Token Account (165 bytes for v1)
        if (size == 165) {
            return AccountType(
                programId = SPL_TOKEN_PROGRAM,
                accountName = "TokenAccount",
                discriminator = null,
                category = AccountCategory.TOKEN,
                confidence = 0.9f
            )
        }

        // SPL Token Mint (82 bytes for v1)
        if (size == 82) {
            return AccountType(
                programId = SPL_TOKEN_PROGRAM,
                accountName = "Mint",
                discriminator = null,
                category = AccountCategory.TOKEN,
                confidence = 0.8f
            )
        }

        // Metaplex Metadata (variable, but has known discriminator pattern)
        if (size > 100 && data[0].toInt() == 4) {
            return AccountType(
                programId = METAPLEX_TOKEN_METADATA,
                accountName = "Metadata",
                discriminator = byteArrayOf(4),
                category = AccountCategory.METADATA,
                confidence = 0.7f
            )
        }

        // Unknown
        return AccountType(
            programId = programId,
            accountName = "Unknown",
            discriminator = if (data.size >= 8) data.copyOfRange(0, 8) else null,
            category = AccountCategory.UNKNOWN,
            confidence = 0.0f
        )
    }

    private fun registerKnownAccounts() {
        // System Program accounts
        knownDiscriminators["0000000000000000"] = AccountType(
            programId = SYSTEM_PROGRAM,
            accountName = "SystemAccount",
            discriminator = ByteArray(8),
            category = AccountCategory.SYSTEM,
            confidence = 0.9f
        )
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private val SYSTEM_PROGRAM = Pubkey.fromBase58("11111111111111111111111111111111")
    private val SPL_TOKEN_PROGRAM = Pubkey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    private val METAPLEX_TOKEN_METADATA = Pubkey.fromBase58("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s")
}

// Extension functions for AnchorDiscriminators
/**
 * Compute discriminator for an account type
 */
fun AnchorDiscriminators.account(accountName: String): ByteArray {
    val pre = "account:$accountName".encodeToByteArray()
    val hash = MessageDigest.getInstance("SHA-256").digest(pre)
    return hash.copyOfRange(0, 8)
}

/**
 * Compute discriminator for an event
 */
fun AnchorDiscriminators.event(eventName: String): ByteArray {
    val pre = "event:$eventName".encodeToByteArray()
    val hash = MessageDigest.getInstance("SHA-256").digest(pre)
    return hash.copyOfRange(0, 8)
}

/**
 * Compute SPL discriminator (using SHA256 of full name)
 */
fun AnchorDiscriminators.spl(namespace: String, name: String): ByteArray {
    val pre = "$namespace:$name".encodeToByteArray()
    val hash = MessageDigest.getInstance("SHA-256").digest(pre)
    return hash.copyOfRange(0, 8)
}

/**
 * Compare discriminators
 */
fun AnchorDiscriminators.matches(data: ByteArray, discriminator: ByteArray): Boolean {
    if (data.size < discriminator.size) return false
    return data.copyOfRange(0, discriminator.size).contentEquals(discriminator)
}

/**
 * InstructionDecoder - Decode instruction data using discriminators
 */
class InstructionDecoder(
    private val programId: Pubkey
) {
    private val instructions = ConcurrentHashMap<String, InstructionInfo>()

    data class InstructionInfo(
        val name: String,
        val discriminator: ByteArray,
        val argParser: ((ByteArray) -> Map<String, Any>)?
    )

    data class DecodedInstruction(
        val name: String,
        val programId: Pubkey,
        val args: Map<String, Any>,
        val rawData: ByteArray
    )

    /**
     * Register an instruction
     */
    fun register(
        name: String,
        discriminator: ByteArray? = null,
        argParser: ((ByteArray) -> Map<String, Any>)? = null
    ): InstructionDecoder {
        val disc = discriminator ?: AnchorDiscriminators.global(name)
        val hexKey = disc.joinToString("") { "%02x".format(it) }
        instructions[hexKey] = InstructionInfo(name, disc, argParser)
        return this
    }

    /**
     * Register an Anchor instruction by name
     */
    fun registerAnchor(
        name: String,
        argParser: ((ByteArray) -> Map<String, Any>)? = null
    ): InstructionDecoder {
        return register(name, AnchorDiscriminators.global(name), argParser)
    }

    /**
     * Decode instruction data
     */
    fun decode(data: ByteArray): DecodedInstruction? {
        if (data.size < 8) return null

        val discriminator = data.copyOfRange(0, 8)
        val hexKey = discriminator.joinToString("") { "%02x".format(it) }

        val info = instructions[hexKey] ?: return null

        val args = info.argParser?.invoke(data.copyOfRange(8, data.size)) ?: emptyMap()

        return DecodedInstruction(
            name = info.name,
            programId = programId,
            args = args,
            rawData = data
        )
    }

    /**
     * Check if instruction matches a known type
     */
    fun isKnown(data: ByteArray): Boolean {
        if (data.size < 8) return false
        val hexKey = data.copyOfRange(0, 8).joinToString("") { "%02x".format(it) }
        return instructions.containsKey(hexKey)
    }
}

/**
 * EventDecoder - Decode Anchor program events
 */
class EventDecoder(
    private val programId: Pubkey
) {
    private val events = ConcurrentHashMap<String, EventInfo>()

    data class EventInfo(
        val name: String,
        val discriminator: ByteArray,
        val parser: ((ByteArray) -> Map<String, Any>)?
    )

    data class DecodedEvent(
        val name: String,
        val programId: Pubkey,
        val data: Map<String, Any>,
        val rawData: ByteArray
    )

    /**
     * Register an event
     */
    fun register(
        name: String,
        parser: ((ByteArray) -> Map<String, Any>)? = null
    ): EventDecoder {
        val disc = AnchorDiscriminators.event(name)
        val hexKey = disc.joinToString("") { "%02x".format(it) }
        events[hexKey] = EventInfo(name, disc, parser)
        return this
    }

    /**
     * Decode event from log data
     */
    fun decode(data: ByteArray): DecodedEvent? {
        if (data.size < 8) return null

        val discriminator = data.copyOfRange(0, 8)
        val hexKey = discriminator.joinToString("") { "%02x".format(it) }

        val info = events[hexKey] ?: return null

        val eventData = info.parser?.invoke(data.copyOfRange(8, data.size)) ?: emptyMap()

        return DecodedEvent(
            name = info.name,
            programId = programId,
            data = eventData,
            rawData = data
        )
    }

    /**
     * Parse events from transaction logs
     */
    fun parseFromLogs(logs: List<String>): List<DecodedEvent> {
        val events = mutableListOf<DecodedEvent>()
        val programDataPrefix = "Program data: "

        for (log in logs) {
            if (log.contains(programDataPrefix)) {
                val base64Data = log.substringAfter(programDataPrefix)
                try {
                    val data = java.util.Base64.getDecoder().decode(base64Data)
                    decode(data)?.let { events.add(it) }
                } catch (e: Exception) {
                    // Invalid base64, skip
                }
            }
        }

        return events
    }
}

/**
 * DSL for building program decoders
 */
fun programDecoder(
    programId: Pubkey,
    block: ProgramDecoderBuilder.() -> Unit
): ProgramDecoderBuilder {
    return ProgramDecoderBuilder(programId).apply(block)
}

class ProgramDecoderBuilder(val programId: Pubkey) {
    val instructionDecoder = InstructionDecoder(programId)
    val eventDecoder = EventDecoder(programId)

    fun instruction(name: String, parser: ((ByteArray) -> Map<String, Any>)? = null) {
        instructionDecoder.registerAnchor(name, parser)
    }

    fun event(name: String, parser: ((ByteArray) -> Map<String, Any>)? = null) {
        eventDecoder.register(name, parser)
    }

    fun account(name: String, category: AccountTypeDetector.AccountCategory = AccountTypeDetector.AccountCategory.CUSTOM) {
        AccountTypeDetector.registerProgram(
            programId,
            listOf(
                AccountTypeDetector.RegisteredAccount(
                    name = name,
                    discriminator = AnchorDiscriminators.account(name),
                    category = category
                )
            )
        )
    }
}
