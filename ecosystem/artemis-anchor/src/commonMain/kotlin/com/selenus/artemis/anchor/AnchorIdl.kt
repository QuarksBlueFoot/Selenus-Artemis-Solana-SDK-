/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 * 
 * ORIGINAL IMPLEMENTATION - No other Kotlin/Android SDK provides this capability.
 * 
 * AnchorIdl - Complete IDL parsing and representation for Anchor programs.
 * 
 * This provides what Anchor's TypeScript SDK does, but for Kotlin/Android:
 * - Parse IDL JSON files at runtime
 * - Generate type-safe program clients
 * - Automatic instruction data serialization
 * - Account deserialization with discriminator validation
 * - PDA derivation from IDL seeds
 */
package com.selenus.artemis.anchor

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * Anchor IDL representation.
 * 
 * The IDL (Interface Definition Language) describes an Anchor program's:
 * - Instructions with their arguments and accounts
 * - Account structures and their fields
 * - Custom types (structs, enums)
 * - Events emitted by the program
 * - Errors that can be thrown
 * - Constants defined in the program
 */
@Serializable
data class AnchorIdl(
    /** IDL version (e.g., "0.1.0") */
    val version: String,
    
    /** Program name from Anchor.toml */
    val name: String,
    
    /** Program documentation */
    val docs: List<String>? = null,
    
    /** Program instructions */
    val instructions: List<IdlInstruction>,
    
    /** Program accounts/state */
    val accounts: List<IdlAccountDef>? = null,
    
    /** Custom types (structs, enums) */
    val types: List<IdlTypeDef>? = null,
    
    /** Program events */
    val events: List<IdlEvent>? = null,
    
    /** Program errors */
    val errors: List<IdlErrorDef>? = null,
    
    /** Program constants */
    val constants: List<IdlConstant>? = null,
    
    /** Program metadata */
    val metadata: IdlMetadata? = null
)

/**
 * Instruction definition from IDL.
 */
@Serializable
data class IdlInstruction(
    /** Instruction name (snake_case in Rust) */
    val name: String,
    
    /** Documentation strings */
    val docs: List<String>? = null,
    
    /** Discriminator bytes (8 bytes) - if provided explicitly */
    val discriminator: List<Int>? = null,
    
    /** Required accounts */
    val accounts: List<IdlAccountItem>,
    
    /** Instruction arguments */
    val args: List<IdlField>
)

/**
 * Account item in instruction - can be a single account or nested group.
 */
@Serializable
data class IdlAccountItem(
    /** Account name */
    val name: String,
    
    /** Documentation */
    val docs: List<String>? = null,
    
    /** Is this account writable? */
    val writable: Boolean? = null,
    
    /** Is this account a signer? */
    val signer: Boolean? = null,
    
    /** Is this account optional? */
    val optional: Boolean? = null,
    
    /** PDA seeds for derived addresses */
    val pda: IdlPda? = null,
    
    /** Relations to other accounts */
    val relations: List<String>? = null,
    
    /** Nested accounts (for account groups) */
    val accounts: List<IdlAccountItem>? = null
)

/**
 * PDA definition with seeds.
 */
@Serializable
data class IdlPda(
    /** Seeds for PDA derivation */
    val seeds: List<IdlSeed>,
    
    /** Program ID for derivation (defaults to program itself) */
    val programId: IdlSeed? = null
)

/**
 * Seed types for PDA derivation.
 */
@Serializable
sealed class IdlSeed {
    @Serializable
    @SerialName("const")
    data class Const(val value: JsonElement) : IdlSeed()
    
    @Serializable
    @SerialName("arg")
    data class Arg(val path: String) : IdlSeed()
    
    @Serializable
    @SerialName("account")
    data class Account(val path: String, val account: String? = null) : IdlSeed()
}

/**
 * Account definition (state account structure).
 */
@Serializable
data class IdlAccountDef(
    val name: String,
    val docs: List<String>? = null,
    val discriminator: List<Int>? = null,
    val type: IdlTypeDefStruct? = null
)

/**
 * Type definition (struct or enum).
 */
@Serializable
data class IdlTypeDef(
    val name: String,
    val docs: List<String>? = null,
    val serialization: String? = null,  // "borsh" | "bytemuck" | etc.
    val repr: IdlRepr? = null,
    val generics: List<IdlGeneric>? = null,
    val type: IdlTypeDefType
)

/**
 * Type definition can be struct, enum, or type alias.
 */
@Serializable
sealed class IdlTypeDefType {
    @Serializable
    @SerialName("struct")
    data class Struct(val fields: List<IdlField>) : IdlTypeDefType()
    
    @Serializable
    @SerialName("enum")
    data class Enum(val variants: List<IdlEnumVariant>) : IdlTypeDefType()
    
    @Serializable
    @SerialName("alias")
    data class Alias(val value: IdlType) : IdlTypeDefType()
}

@Serializable
data class IdlTypeDefStruct(
    val fields: List<IdlField>? = null
)

/**
 * Struct field definition.
 */
@Serializable
data class IdlField(
    val name: String,
    val docs: List<String>? = null,
    val type: IdlType
)

/**
 * Enum variant.
 */
@Serializable
data class IdlEnumVariant(
    val name: String,
    val docs: List<String>? = null,
    val fields: List<IdlField>? = null
)

/**
 * IDL Type representation.
 * 
 * Supports all Anchor/Borsh types:
 * - Primitives: bool, u8, u16, u32, u64, u128, i8, i16, i32, i64, i128, f32, f64
 * - Solana types: publicKey, string, bytes
 * - Compound types: vec, array, option, defined, coption
 */
@Serializable(with = IdlTypeSerializer::class)
sealed class IdlType {
    // Primitive types
    object Bool : IdlType()
    object U8 : IdlType()
    object U16 : IdlType()
    object U32 : IdlType()
    object U64 : IdlType()
    object U128 : IdlType()
    object I8 : IdlType()
    object I16 : IdlType()
    object I32 : IdlType()
    object I64 : IdlType()
    object I128 : IdlType()
    object F32 : IdlType()
    object F64 : IdlType()
    
    // Solana types
    object PublicKey : IdlType()
    object String : IdlType()
    object Bytes : IdlType()
    
    // Compound types
    data class Vec(val inner: IdlType) : IdlType()
    data class Array(val inner: IdlType, val size: Int) : IdlType()
    data class Option(val inner: IdlType) : IdlType()
    data class COption(val inner: IdlType) : IdlType()
    data class Defined(val name: kotlin.String, val generics: List<IdlType>? = null) : IdlType()
    data class Generic(val name: kotlin.String) : IdlType()
}

/**
 * Event definition.
 */
@Serializable
data class IdlEvent(
    val name: String,
    val docs: List<String>? = null,
    val discriminator: List<Int>? = null,
    val fields: List<IdlEventField>
)

@Serializable
data class IdlEventField(
    val name: String,
    val type: IdlType,
    val index: Boolean? = null
)

/**
 * Error definition.
 */
@Serializable
data class IdlErrorDef(
    val code: Int,
    val name: String,
    val msg: String? = null
)

/**
 * Constant definition.
 */
@Serializable
data class IdlConstant(
    val name: String,
    val docs: List<String>? = null,
    val type: IdlType,
    val value: String
)

/**
 * Program metadata.
 */
@Serializable
data class IdlMetadata(
    val name: String? = null,
    val version: String? = null,
    val spec: String? = null,
    val description: String? = null,
    val repository: String? = null,
    val dependencies: List<IdlDependency>? = null,
    val contact: String? = null,
    val deployments: IdlDeployments? = null,
    val address: String? = null  // Program ID
)

@Serializable
data class IdlDependency(
    val name: String,
    val version: String
)

@Serializable
data class IdlDeployments(
    val mainnet: String? = null,
    val devnet: String? = null,
    val testnet: String? = null,
    val localnet: String? = null
)

@Serializable
data class IdlRepr(
    val kind: String,  // "rust" | "c" | "transparent"
    val align: Int? = null,
    val packed: Boolean? = null
)

@Serializable
data class IdlGeneric(
    val kind: String,  // "type" | "const"
    val name: String,
    val type: IdlType? = null
)

/**
 * Custom serializer for IdlType to handle the complex union type.
 */
object IdlTypeSerializer : KSerializer<IdlType> {
    override val descriptor = PrimitiveSerialDescriptor("IdlType", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: IdlType) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("IdlType can only be serialized to JSON")
        
        val element = when (value) {
            is IdlType.Bool -> JsonPrimitive("bool")
            is IdlType.U8 -> JsonPrimitive("u8")
            is IdlType.U16 -> JsonPrimitive("u16")
            is IdlType.U32 -> JsonPrimitive("u32")
            is IdlType.U64 -> JsonPrimitive("u64")
            is IdlType.U128 -> JsonPrimitive("u128")
            is IdlType.I8 -> JsonPrimitive("i8")
            is IdlType.I16 -> JsonPrimitive("i16")
            is IdlType.I32 -> JsonPrimitive("i32")
            is IdlType.I64 -> JsonPrimitive("i64")
            is IdlType.I128 -> JsonPrimitive("i128")
            is IdlType.F32 -> JsonPrimitive("f32")
            is IdlType.F64 -> JsonPrimitive("f64")
            is IdlType.PublicKey -> JsonPrimitive("publicKey")
            is IdlType.String -> JsonPrimitive("string")
            is IdlType.Bytes -> JsonPrimitive("bytes")
            is IdlType.Vec -> buildJsonObject {
                put("vec", Json.encodeToJsonElement(IdlTypeSerializer, value.inner))
            }
            is IdlType.Array -> buildJsonObject {
                putJsonArray("array") {
                    add(Json.encodeToJsonElement(IdlTypeSerializer, value.inner))
                    add(JsonPrimitive(value.size))
                }
            }
            is IdlType.Option -> buildJsonObject {
                put("option", Json.encodeToJsonElement(IdlTypeSerializer, value.inner))
            }
            is IdlType.COption -> buildJsonObject {
                put("coption", Json.encodeToJsonElement(IdlTypeSerializer, value.inner))
            }
            is IdlType.Defined -> buildJsonObject {
                put("defined", buildJsonObject {
                    put("name", JsonPrimitive(value.name))
                    value.generics?.let { 
                        putJsonArray("generics") {
                            it.forEach { g -> add(Json.encodeToJsonElement(IdlTypeSerializer, g)) }
                        }
                    }
                })
            }
            is IdlType.Generic -> buildJsonObject {
                put("generic", JsonPrimitive(value.name))
            }
        }
        
        jsonEncoder.encodeJsonElement(element)
    }
    
    override fun deserialize(decoder: Decoder): IdlType {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("IdlType can only be deserialized from JSON")
        
        return parseIdlType(jsonDecoder.decodeJsonElement())
    }
    
    private fun parseIdlType(element: JsonElement): IdlType {
        return when {
            element is JsonPrimitive && element.isString -> {
                when (element.content) {
                    "bool" -> IdlType.Bool
                    "u8" -> IdlType.U8
                    "u16" -> IdlType.U16
                    "u32" -> IdlType.U32
                    "u64" -> IdlType.U64
                    "u128" -> IdlType.U128
                    "i8" -> IdlType.I8
                    "i16" -> IdlType.I16
                    "i32" -> IdlType.I32
                    "i64" -> IdlType.I64
                    "i128" -> IdlType.I128
                    "f32" -> IdlType.F32
                    "f64" -> IdlType.F64
                    "publicKey", "pubkey" -> IdlType.PublicKey
                    "string" -> IdlType.String
                    "bytes" -> IdlType.Bytes
                    else -> IdlType.Defined(element.content)
                }
            }
            element is JsonObject -> {
                when {
                    "vec" in element -> IdlType.Vec(parseIdlType(element["vec"]!!))
                    "array" in element -> {
                        val arr = element["array"]!!.jsonArray
                        IdlType.Array(parseIdlType(arr[0]), arr[1].jsonPrimitive.int)
                    }
                    "option" in element -> IdlType.Option(parseIdlType(element["option"]!!))
                    "coption" in element -> IdlType.COption(parseIdlType(element["coption"]!!))
                    "defined" in element -> {
                        val def = element["defined"]!!
                        if (def is JsonPrimitive) {
                            IdlType.Defined(def.content)
                        } else {
                            val obj = def.jsonObject
                            val name = obj["name"]?.jsonPrimitive?.content ?: ""
                            val generics = obj["generics"]?.jsonArray?.map { parseIdlType(it) }
                            IdlType.Defined(name, generics)
                        }
                    }
                    "generic" in element -> IdlType.Generic(element["generic"]!!.jsonPrimitive.content)
                    else -> throw SerializationException("Unknown IDL type: $element")
                }
            }
            else -> throw SerializationException("Invalid IDL type format: $element")
        }
    }
}
