/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 */

package com.selenus.artemis.intent

import java.nio.charset.StandardCharsets

/**
 * Decoder for Memo Program instructions.
 * 
 * Extracts memo text from transactions and identifies potential
 * phishing or suspicious content.
 */
object MemoProgramDecoder : InstructionDecoder {
    
    // Suspicious patterns that might indicate phishing
    private val SUSPICIOUS_PATTERNS = listOf(
        "claim" to "May be a phishing attempt",
        "airdrop" to "Unsolicited airdrop claim - verify legitimacy",
        "reward" to "Potential reward scam",
        "winner" to "Potential prize scam",
        "urgent" to "Urgency tactics often used in scams",
        "verify" to "Verification request - may be phishing",
        "wallet" to "References wallet - verify source",
        "connect" to "Connection request - verify legitimacy",
        "http://" to "Insecure HTTP link",
        "https://" to "Contains external link - verify before clicking",
        ".xyz" to "Contains .xyz domain - often used in scams",
        ".tk" to "Contains .tk domain - often used in scams",
        ".ml" to "Contains .ml domain - often used in scams"
    )
    
    // Maximum safe memo length before truncation
    private const val MAX_DISPLAY_LENGTH = 200
    
    override fun decode(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        // Memo program data is just raw UTF-8 text
        val memoText = try {
            String(data, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // If not valid UTF-8, show as hex
            data.joinToString("") { "%02x".format(it) }
        }
        
        // Analyze memo for suspicious content
        val warnings = mutableListOf<String>()
        var riskLevel = RiskLevel.INFO
        
        val lowerMemo = memoText.lowercase()
        
        for ((pattern, warning) in SUSPICIOUS_PATTERNS) {
            if (lowerMemo.contains(pattern)) {
                warnings.add("⚠️ $warning")
                riskLevel = maxOf(riskLevel, RiskLevel.MEDIUM)
            }
        }
        
        // Check for executable content or scripts
        if (lowerMemo.contains("<script") || lowerMemo.contains("javascript:")) {
            warnings.add("🚨 Contains potentially malicious script content")
            riskLevel = RiskLevel.HIGH
        }
        
        // Check for base64-encoded content (often used to hide malicious payloads)
        val base64Pattern = Regex("^[A-Za-z0-9+/]{50,}={0,2}$")
        if (base64Pattern.containsMatchIn(memoText)) {
            warnings.add("Contains base64-encoded data")
            riskLevel = maxOf(riskLevel, RiskLevel.LOW)
        }
        
        // Truncate long memos for display
        val displayMemo = if (memoText.length > MAX_DISPLAY_LENGTH) {
            "${memoText.take(MAX_DISPLAY_LENGTH)}... (${memoText.length} chars)"
        } else {
            memoText
        }
        
        // Check which signers are required
        val signers = accounts.filter { it.isNotEmpty() }
        val requiresSigners = signers.isNotEmpty()
        
        return TransactionIntent(
            instructionIndex = instructionIndex,
            programName = "Memo",
            programId = ProgramRegistry.MEMO_PROGRAM,
            method = "memo",
            summary = "📝 Memo: \"$displayMemo\"",
            accounts = signers.mapIndexed { i, signer ->
                AccountRole(
                    pubkey = signer,
                    role = "Signer ${i + 1}",
                    isSigner = true,
                    isWritable = false
                )
            },
            args = mapOf(
                "memo" to memoText,
                "length" to memoText.length,
                "requiresSigners" to requiresSigners,
                "signerCount" to signers.size
            ),
            riskLevel = riskLevel,
            warnings = warnings.ifEmpty { 
                listOf("Memo attached to transaction - informational only") 
            }
        )
    }
}

/**
 * Decoder for Memo Program v2.
 * Same as v1 but supports requiring specific signers.
 */
object MemoV2ProgramDecoder : InstructionDecoder {
    override fun decode(
        programId: String,
        accounts: List<String>,
        data: ByteArray,
        instructionIndex: Int
    ): TransactionIntent? {
        // Memo v2 uses same format, just delegate with updated program name
        val intent = MemoProgramDecoder.decode(programId, accounts, data, instructionIndex)
        return intent?.copy(
            programName = "Memo v2",
            programId = ProgramRegistry.MEMO_V2_PROGRAM
        )
    }
}
