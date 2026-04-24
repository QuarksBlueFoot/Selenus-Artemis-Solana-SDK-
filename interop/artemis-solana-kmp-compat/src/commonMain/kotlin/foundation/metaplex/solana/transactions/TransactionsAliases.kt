/*
 * Upstream solana-kmp puts `AccountMeta`, `TransactionInstruction`, and the
 * `SolanaTransactionBuilder` under `foundation.metaplex.solana.transactions`.
 * Artemis's initial compat layer put them in `foundation.metaplex.solana`.
 *
 * These typealiases republish the types in the upstream-preferred package so
 * existing `import foundation.metaplex.solana.transactions.AccountMeta` call
 * sites resolve without edits. Callers migrating off the older layout can
 * still use the shorter import; both point at the same underlying type.
 */
package foundation.metaplex.solana.transactions

typealias AccountMeta = foundation.metaplex.solana.AccountMeta
typealias TransactionInstruction = foundation.metaplex.solana.TransactionInstruction
typealias Transaction = foundation.metaplex.solana.Transaction
typealias SolanaTransactionBuilder = foundation.metaplex.solana.SolanaTransactionBuilder
