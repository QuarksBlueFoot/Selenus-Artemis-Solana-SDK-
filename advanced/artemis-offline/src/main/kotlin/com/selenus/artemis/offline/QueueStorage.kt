/*
 * Copyright (c) 2024-2025 Selenus Technologies. All rights reserved.
 * Licensed under the Apache License, Version 2.0.
 *
 * Offline Transaction Queue - Storage Interface
 */

package com.selenus.artemis.offline

import java.util.concurrent.ConcurrentHashMap

/**
 * Interface for persisting queued transactions.
 * 
 * Implementations can use various storage backends:
 * - In-memory (for testing)
 * - SharedPreferences (Android)
 * - Room Database (Android)
 * - File-based (any platform)
 */
interface QueueStorage {
    
    /**
     * Save or update a queued transaction.
     */
    suspend fun save(transaction: QueuedTransaction)
    
    /**
     * Get a queued transaction by ID.
     */
    suspend fun get(id: String): QueuedTransaction?
    
    /**
     * Get all queued transactions.
     */
    suspend fun getAll(): List<QueuedTransaction>
    
    /**
     * Remove a transaction by ID.
     * 
     * @return true if the transaction was removed
     */
    suspend fun remove(id: String): Boolean
    
    /**
     * Clear all transactions.
     */
    suspend fun clear()
}

/**
 * In-memory implementation of QueueStorage.
 * 
 * Useful for testing or when persistence isn't needed.
 */
class InMemoryQueueStorage : QueueStorage {
    private val transactions = ConcurrentHashMap<String, QueuedTransaction>()
    
    override suspend fun save(transaction: QueuedTransaction) {
        transactions[transaction.id] = transaction
    }
    
    override suspend fun get(id: String): QueuedTransaction? {
        return transactions[id]
    }
    
    override suspend fun getAll(): List<QueuedTransaction> {
        return transactions.values.toList()
    }
    
    override suspend fun remove(id: String): Boolean {
        return transactions.remove(id) != null
    }
    
    override suspend fun clear() {
        transactions.clear()
    }
}
