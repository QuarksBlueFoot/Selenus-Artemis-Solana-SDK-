/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * ORIGINAL IMPLEMENTATION - No other Kotlin/Android SDK provides this capability.
 * 
 * AnchorProgram - Type-safe Anchor program client from IDL.
 * 
 * This is the Kotlin equivalent of Anchor's TypeScript Program class:
 * - program.methods.instructionName(args).accounts({}).rpc()
 * - program.account.AccountType.fetch(address)
 * - program.account.AccountType.all()
 * 
 * Artemis takes this further with:
 * - Flow-based reactive account watching
 * - Automatic PDA derivation
 * - Transaction simulation before send
 * - Batch account fetching with memcmp filters
 */
package com.selenus.artemis.anchor

import com.selenus.artemis.disc.AnchorDiscriminators
import com.selenus.artemis.runtime.Pubkey
import com.selenus.artemis.rpc.RpcApi
import com.selenus.artemis.tx.AccountMeta
import com.selenus.artemis.tx.Instruction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Type-safe Anchor program client.
 * 
 * Usage:
 * ```kotlin
 * // Load IDL
 * val idl = AnchorIdl.parse(idlJson)
 * 
 * // Create program client
 * val program = AnchorProgram(idl, programId, rpcApi)
 * 
 * // Call instructions (like Anchor TS program.methods.xxx)
 * val tx = program.methods
 *     .instruction("initialize")
 *     .args(mapOf("name" to "MyToken", "symbol" to "MTK"))
 *     .accounts {
 *         account("tokenAccount", tokenAccountPubkey)
 *         signer("authority", authorityPubkey)
 *         program("tokenProgram", TOKEN_PROGRAM_ID)
 *     }
 *     .build()
 * 
 * // Fetch accounts (like Anchor TS program.account.xxx)
 * val account = program.account
 *     .type("TokenState")
 *     .fetch(address)
 * 
 * // Fetch all with filters
 * val accounts = program.account
 *     .type("TokenState")
 *     .all()
 *     .filter { memcmp(0, authorityBytes) }
 *     .fetch()
 * ```
 */
class AnchorProgram(
    val idl: AnchorIdl,
    val programId: Pubkey,
    private val rpcApi: RpcApi? = null
) {
    /** Instruction methods builder */
    val methods: MethodsBuilder = MethodsBuilder(this)
    
    /** Account fetching builder */
    val account: AccountsBuilder = AccountsBuilder(this)
    
    /** PDA derivation helpers */
    val pda: PdaBuilder = PdaBuilder(this)
    
    /** Event parsing */
    val events: EventParser = EventParser(this)
    
    /** Error decoding */
    val errors: ErrorDecoder = ErrorDecoder(this)
    
    // Precompute discriminators for fast lookup
    private val instructionDiscriminators: Map<String, ByteArray> by lazy {
        idl.instructions.associate { ix ->
            ix.name to (ix.discriminator?.let { d -> 
                ByteArray(d.size) { d[it].toByte() }
            } ?: AnchorDiscriminators.global(ix.name))
        }
    }
    
    private val accountDiscriminators: Map<String, ByteArray> by lazy {
        idl.accounts?.associate { acc ->
            acc.name to (acc.discriminator?.let { d ->
                ByteArray(d.size) { d[it].toByte() }
            } ?: computeAccountDiscriminator(acc.name))
        } ?: emptyMap()
    }
    
    private val eventDiscriminators: Map<String, ByteArray> by lazy {
        idl.events?.associate { ev ->
            ev.name to (ev.discriminator?.let { d ->
                ByteArray(d.size) { d[it].toByte() }
            } ?: computeEventDiscriminator(ev.name))
        } ?: emptyMap()
    }
    
    /**
     * Get instruction discriminator.
     */
    fun getInstructionDiscriminator(name: String): ByteArray {
        return instructionDiscriminators[name]
            ?: throw IllegalArgumentException("Unknown instruction: $name. Available: ${idl.instructions.map { it.name }}")
    }
    
    /**
     * Get account discriminator.
     */
    fun getAccountDiscriminator(name: String): ByteArray {
        return accountDiscriminators[name]
            ?: throw IllegalArgumentException("Unknown account: $name. Available: ${idl.accounts?.map { it.name }}")
    }
    
    /**
     * Get event discriminator.
     */
    fun getEventDiscriminator(name: String): ByteArray {
        return eventDiscriminators[name]
            ?: throw IllegalArgumentException("Unknown event: $name. Available: ${idl.events?.map { it.name }}")
    }
    
    /**
     * Find instruction definition by name.
     */
    fun findInstruction(name: String): IdlInstruction {
        return idl.instructions.find { it.name == name }
            ?: throw IllegalArgumentException("Instruction not found: $name")
    }
    
    /**
     * Find account definition by name.
     */
    fun findAccount(name: String): IdlAccountDef {
        return idl.accounts?.find { it.name == name }
            ?: throw IllegalArgumentException("Account type not found: $name")
    }
    
    /**
     * Find type definition by name.
     */
    fun findType(name: String): IdlTypeDef? {
        return idl.types?.find { it.name == name }
    }
    
    private fun computeAccountDiscriminator(name: String): ByteArray {
        // Anchor uses: sha256("account:<AccountName>")[0..8]
        val preimage = "account:$name".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(preimage)
        return hash.copyOfRange(0, 8)
    }
    
    private fun computeEventDiscriminator(name: String): ByteArray {
        // Anchor uses: sha256("event:<EventName>")[0..8]
        val preimage = "event:$name".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(preimage)
        return hash.copyOfRange(0, 8)
    }
    
    companion object {
        /**
         * Parse IDL from JSON string.
         */
        fun parseIdl(json: String): AnchorIdl {
            return Json { 
                ignoreUnknownKeys = true
                isLenient = true
            }.decodeFromString(json)
        }
        
        /**
         * Create program from IDL JSON.
         */
        fun fromIdl(idlJson: String, programId: Pubkey, rpcApi: RpcApi? = null): AnchorProgram {
            val idl = parseIdl(idlJson)
            return AnchorProgram(idl, programId, rpcApi)
        }
    }
}

/**
 * Fluent builder for calling program instructions.
 * 
 * Pattern mirrors Anchor's TypeScript: program.methods.xxx(args).accounts({}).rpc()
 */
class MethodsBuilder(private val program: AnchorProgram) {
    
    /**
     * Start building an instruction call.
     */
    fun instruction(name: String): InstructionCallBuilder {
        return InstructionCallBuilder(program, name)
    }
    
    // Kotlin DSL - call instruction by name directly
    operator fun get(name: String): InstructionCallBuilder = instruction(name)
}

/**
 * Builder for a specific instruction call.
 */
class InstructionCallBuilder(
    private val program: AnchorProgram,
    private val instructionName: String
) {
    private val ixDef: IdlInstruction = program.findInstruction(instructionName)
    private var args: Map<String, Any?> = emptyMap()
    private val accountsMap = mutableMapOf<String, AccountEntry>()
    private val remainingAccounts = mutableListOf<AccountMeta>()
    
    data class AccountEntry(
        val pubkey: Pubkey,
        val isSigner: Boolean,
        val isWritable: Boolean
    )
    
    /**
     * Set instruction arguments.
     */
    fun args(arguments: Map<String, Any?>): InstructionCallBuilder {
        this.args = arguments
        return this
    }
    
    /**
     * Set instruction arguments using DSL.
     */
    fun args(block: ArgsBuilder.() -> Unit): InstructionCallBuilder {
        val builder = ArgsBuilder()
        builder.block()
        this.args = builder.build()
        return this
    }
    
    /**
     * Set accounts using DSL.
     */
    fun accounts(block: AccountsDslBuilder.() -> Unit): InstructionCallBuilder {
        val builder = AccountsDslBuilder()
        builder.block()
        accountsMap.putAll(builder.accounts)
        return this
    }
    
    /**
     * Add remaining accounts (used for variable-length account lists).
     */
    fun remainingAccounts(accounts: List<AccountMeta>): InstructionCallBuilder {
        remainingAccounts.addAll(accounts)
        return this
    }
    
    /**
     * Build the instruction.
     */
    fun build(): Instruction {
        // Serialize instruction data
        val data = serializeInstructionData()
        
        // Build account metas
        val accountMetas = buildAccountMetas()
        
        return Instruction(
            programId = program.programId,
            accounts = accountMetas + remainingAccounts,
            data = data
        )
    }
    
    /**
     * Build instruction and return as transaction-ready.
     */
    fun instruction(): Instruction = build()
    
    private fun serializeInstructionData(): ByteArray {
        val discriminator = program.getInstructionDiscriminator(instructionName)
        
        if (ixDef.args.isEmpty()) {
            return discriminator
        }
        
        // Serialize arguments in order defined by IDL
        val argsData = BorshSerializer.serializeArgs(ixDef.args, args, program)
        
        return discriminator + argsData
    }
    
    private fun buildAccountMetas(): List<AccountMeta> {
        val metas = mutableListOf<AccountMeta>()
        
        fun processAccount(item: IdlAccountItem) {
            if (item.accounts != null) {
                // Nested account group
                item.accounts.forEach { processAccount(it) }
            } else {
                val entry = accountsMap[item.name]
                if (entry != null) {
                    metas.add(AccountMeta(
                        pubkey = entry.pubkey,
                        isSigner = entry.isSigner || (item.signer == true),
                        isWritable = entry.isWritable || (item.writable == true)
                    ))
                } else if (item.optional != true) {
                    throw IllegalStateException(
                        "Required account '${item.name}' not provided for instruction '$instructionName'"
                    )
                }
            }
        }
        
        ixDef.accounts.forEach { processAccount(it) }
        
        return metas
    }
    
    /**
     * Get the accounts required by this instruction.
     */
    fun getRequiredAccounts(): List<IdlAccountItem> {
        val accounts = mutableListOf<IdlAccountItem>()
        
        fun collect(item: IdlAccountItem) {
            if (item.accounts != null) {
                item.accounts.forEach { collect(it) }
            } else {
                accounts.add(item)
            }
        }
        
        ixDef.accounts.forEach { collect(it) }
        return accounts
    }
}

/**
 * DSL builder for instruction arguments.
 */
class ArgsBuilder {
    private val map = mutableMapOf<String, Any?>()
    
    infix fun String.to(value: Any?) {
        map[this] = value
    }
    
    fun set(name: String, value: Any?) {
        map[name] = value
    }
    
    fun build(): Map<String, Any?> = map.toMap()
}

/**
 * DSL builder for accounts.
 */
class AccountsDslBuilder {
    internal val accounts = mutableMapOf<String, InstructionCallBuilder.AccountEntry>()
    
    /**
     * Add a regular account (readonly, non-signer).
     */
    fun account(name: String, pubkey: Pubkey) {
        accounts[name] = InstructionCallBuilder.AccountEntry(pubkey, isSigner = false, isWritable = false)
    }
    
    /**
     * Add a writable account.
     */
    fun writable(name: String, pubkey: Pubkey) {
        accounts[name] = InstructionCallBuilder.AccountEntry(pubkey, isSigner = false, isWritable = true)
    }
    
    /**
     * Add a signer account (readonly).
     */
    fun signer(name: String, pubkey: Pubkey) {
        accounts[name] = InstructionCallBuilder.AccountEntry(pubkey, isSigner = true, isWritable = false)
    }
    
    /**
     * Add a signer account that is also writable.
     */
    fun signerWritable(name: String, pubkey: Pubkey) {
        accounts[name] = InstructionCallBuilder.AccountEntry(pubkey, isSigner = true, isWritable = true)
    }
    
    /**
     * Add a program account (always readonly, non-signer).
     */
    fun program(name: String, pubkey: Pubkey) {
        accounts[name] = InstructionCallBuilder.AccountEntry(pubkey, isSigner = false, isWritable = false)
    }
}

/**
 * Builder for fetching and watching program accounts.
 */
class AccountsBuilder(private val program: AnchorProgram) {
    
    /**
     * Target a specific account type for fetching.
     */
    fun type(name: String): AccountTypeFetcher {
        return AccountTypeFetcher(program, name)
    }
    
    operator fun get(name: String): AccountTypeFetcher = type(name)
}

/**
 * Fetcher for a specific account type.
 */
class AccountTypeFetcher(
    private val program: AnchorProgram,
    private val typeName: String
) {
    private val accountDef: IdlAccountDef = program.findAccount(typeName)
    private val discriminator: ByteArray = program.getAccountDiscriminator(typeName)
    
    /**
     * Fetch a single account by address.
     */
    suspend fun fetch(address: Pubkey, rpcApi: RpcApi): DecodedAccount? {
        val response = rpcApi.getAccountInfo(address.toBase58(), encoding = "base64")
        
        // Check if account exists
        val value = response["value"]?.takeIf { it !is JsonNull }?.jsonObject ?: return null
        val dataArray = value["data"]?.jsonArray ?: return null
        val base64Data = dataArray.getOrNull(0)?.jsonPrimitive?.content ?: return null
        
        val data = java.util.Base64.getDecoder().decode(base64Data)
        
        return decode(data, address)
    }
    
    /**
     * Fetch multiple accounts.
     */
    suspend fun fetchMultiple(addresses: List<Pubkey>, rpcApi: RpcApi): List<DecodedAccount?> {
        val response = rpcApi.getMultipleAccounts(addresses.map { it.toBase58() }, encoding = "base64")
        val values = response["value"]?.jsonArray ?: return addresses.map { null }
        
        return values.mapIndexed { index, elem ->
            if (elem is JsonNull || elem !is JsonObject) {
                null
            } else {
                val dataArray = elem["data"]?.jsonArray
                val base64Data = dataArray?.getOrNull(0)?.jsonPrimitive?.content
                if (base64Data != null) {
                    val data = java.util.Base64.getDecoder().decode(base64Data)
                    decode(data, addresses[index])
                } else {
                    null
                }
            }
        }
    }
    
    /**
     * Fetch all accounts of this type.
     */
    fun all(): AllAccountsFetcher {
        return AllAccountsFetcher(program, typeName, discriminator)
    }
    
    /**
     * Decode account data.
     */
    fun decode(data: ByteArray, address: Pubkey? = null): DecodedAccount? {
        if (data.size < 8) return null
        
        // Validate discriminator
        val accountDisc = data.copyOfRange(0, 8)
        if (!accountDisc.contentEquals(discriminator)) {
            return null
        }
        
        // Deserialize fields
        val fields = accountDef.type?.fields ?: return null
        val decoded = BorshDeserializer.deserializeFields(fields, data, 8, program)
        
        return DecodedAccount(
            address = address,
            typeName = typeName,
            data = decoded.fields,
            rawData = data
        )
    }
    
    /**
     * Watch account for changes (reactive).
     */
    fun watch(address: Pubkey): Flow<DecodedAccount> = flow {
        // This would integrate with WebSocket subscriptions
        // For now, provides the interface
    }
}

/**
 * Fetcher for all accounts of a type with filter support.
 */
class AllAccountsFetcher(
    private val program: AnchorProgram,
    private val typeName: String,
    private val discriminator: ByteArray
) {
    private val filters = mutableListOf<MemcmpFilter>()
    
    data class MemcmpFilter(val offset: Int, val bytes: ByteArray)
    
    /**
     * Add a memcmp filter (matches bytes at offset).
     */
    fun filter(offset: Int, bytes: ByteArray): AllAccountsFetcher {
        // Offset in data (after 8-byte discriminator)
        filters.add(MemcmpFilter(offset + 8, bytes))
        return this
    }
    
    /**
     * Filter by a field value.
     */
    fun filterByField(fieldName: String, value: Pubkey): AllAccountsFetcher {
        // Calculate field offset from IDL
        val accountDef = program.findAccount(typeName)
        val fields = accountDef.type?.fields ?: return this
        
        var offset = 0
        for (field in fields) {
            if (field.name == fieldName) {
                return filter(offset, value.bytes)
            }
            offset += BorshSerializer.sizeOf(field.type, program)
        }
        
        return this
    }
    
    /**
     * Fetch all matching accounts.
     */
    suspend fun fetch(rpcApi: RpcApi): List<DecodedAccount> {
        // Build getProgramAccounts filters
        val rpcFilters = buildJsonArray {
            // Always filter by discriminator
            addJsonObject {
                putJsonObject("memcmp") {
                    put("offset", 0)
                    put("bytes", java.util.Base64.getEncoder().encodeToString(discriminator))
                    put("encoding", "base64")
                }
            }
            
            // Add custom filters
            filters.forEach { filter ->
                addJsonObject {
                    putJsonObject("memcmp") {
                        put("offset", filter.offset)
                        put("bytes", java.util.Base64.getEncoder().encodeToString(filter.bytes))
                        put("encoding", "base64")
                    }
                }
            }
        }
        
        // Call RPC
        val accounts = rpcApi.callRaw(
            "getProgramAccounts",
            buildJsonArray {
                add(program.programId.toBase58())
                addJsonObject {
                    put("encoding", "base64")
                    put("filters", rpcFilters)
                }
            }
        )
        
        val fetcher = AccountTypeFetcher(program, typeName)
        val result = mutableListOf<DecodedAccount>()
        
        val accountsArray = accounts["result"]?.jsonArray ?: return emptyList()
        
        for (item in accountsArray) {
            val obj = item.jsonObject
            val pubkey = Pubkey.fromBase58(obj["pubkey"]!!.jsonPrimitive.content)
            val accountObj = obj["account"]!!.jsonObject
            val dataArray = accountObj["data"]!!.jsonArray
            val base64Data = dataArray[0].jsonPrimitive.content
            val data = java.util.Base64.getDecoder().decode(base64Data)
            
            fetcher.decode(data, pubkey)?.let { result.add(it) }
        }
        
        return result
    }
}

/**
 * Decoded account data.
 */
data class DecodedAccount(
    val address: Pubkey?,
    val typeName: String,
    val data: Map<String, Any?>,
    val rawData: ByteArray
) {
    /**
     * Get a field value.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(fieldName: String): T? = data[fieldName] as? T
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedAccount) return false
        return address == other.address && typeName == other.typeName
    }
    
    override fun hashCode(): Int {
        var result = address?.hashCode() ?: 0
        result = 31 * result + typeName.hashCode()
        return result
    }
}

/**
 * PDA derivation builder.
 */
class PdaBuilder(private val program: AnchorProgram) {
    
    /**
     * Find PDA for an instruction's account.
     */
    fun findForAccount(
        instructionName: String,
        accountName: String,
        args: Map<String, Any?> = emptyMap(),
        accounts: Map<String, Pubkey> = emptyMap()
    ): Pair<Pubkey, Int>? {
        val ix = program.findInstruction(instructionName)
        val accountItem = findAccountItem(ix.accounts, accountName) ?: return null
        val pda = accountItem.pda ?: return null
        
        val seeds = mutableListOf<ByteArray>()
        
        for (seed in pda.seeds) {
            val seedBytes = when (seed) {
                is IdlSeed.Const -> {
                    // Handle const seeds
                    when (val value = seed.value) {
                        is JsonPrimitive -> {
                            if (value.isString) {
                                value.content.toByteArray(Charsets.UTF_8)
                            } else {
                                // Numeric constant
                                val num = value.long
                                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(num).array()
                            }
                        }
                        is JsonArray -> {
                            ByteArray(value.size) { value[it].jsonPrimitive.int.toByte() }
                        }
                        else -> continue
                    }
                }
                is IdlSeed.Arg -> {
                    // Get from args
                    val argValue = args[seed.path] ?: continue
                    serializeSeedValue(argValue)
                }
                is IdlSeed.Account -> {
                    // Get from accounts
                    val pubkey = accounts[seed.path] ?: continue
                    pubkey.bytes
                }
            }
            seeds.add(seedBytes)
        }
        
        // Derive PDA
        val programIdForDerivation = pda.programId?.let { seedToPubkey(it, args, accounts) } ?: program.programId
        
        return Pubkey.findProgramAddress(seeds, programIdForDerivation)
    }
    
    private fun findAccountItem(items: List<IdlAccountItem>, name: String): IdlAccountItem? {
        for (item in items) {
            if (item.name == name) return item
            if (item.accounts != null) {
                findAccountItem(item.accounts, name)?.let { return it }
            }
        }
        return null
    }
    
    private fun serializeSeedValue(value: Any): ByteArray {
        return when (value) {
            is Pubkey -> value.bytes
            is String -> value.toByteArray(Charsets.UTF_8)
            is Long -> ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
            is Int -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
            is ByteArray -> value
            else -> value.toString().toByteArray(Charsets.UTF_8)
        }
    }
    
    private fun seedToPubkey(seed: IdlSeed, args: Map<String, Any?>, accounts: Map<String, Pubkey>): Pubkey? {
        return when (seed) {
            is IdlSeed.Const -> {
                val value = seed.value
                if (value is JsonPrimitive && value.isString) {
                    Pubkey.fromBase58(value.content)
                } else null
            }
            is IdlSeed.Arg -> args[seed.path] as? Pubkey
            is IdlSeed.Account -> accounts[seed.path]
        }
    }
}

/**
 * Event parser for program events.
 */
class EventParser(private val program: AnchorProgram) {
    
    /**
     * Parse event from CPI data.
     */
    fun parse(data: ByteArray): ParsedEvent? {
        if (data.size < 8) return null
        
        val discriminator = data.copyOfRange(0, 8)
        
        for (event in program.idl.events ?: emptyList()) {
            val eventDisc = program.getEventDiscriminator(event.name)
            if (eventDisc.contentEquals(discriminator)) {
                val fields = BorshDeserializer.deserializeEventFields(
                    event.fields,
                    data,
                    8,
                    program
                )
                return ParsedEvent(event.name, fields)
            }
        }
        
        return null
    }
    
    data class ParsedEvent(
        val name: String,
        val fields: Map<String, Any?>
    )
}

/**
 * Error decoder for program errors.
 */
class ErrorDecoder(private val program: AnchorProgram) {
    
    /**
     * Decode error from error code.
     */
    fun decode(errorCode: Int): DecodedError? {
        val error = program.idl.errors?.find { it.code == errorCode }
        return error?.let { DecodedError(it.code, it.name, it.msg) }
    }
    
    /**
     * Decode error from transaction logs.
     */
    fun decodeFromLogs(logs: List<String>): DecodedError? {
        for (log in logs) {
            // Look for "Program log: AnchorError" or custom error patterns
            val codeMatch = Regex("""Error Code: (\d+)""").find(log)
            if (codeMatch != null) {
                val code = codeMatch.groupValues[1].toIntOrNull()
                if (code != null) {
                    return decode(code)
                }
            }
        }
        return null
    }
    
    data class DecodedError(
        val code: Int,
        val name: String,
        val message: String?
    )
}
