/*
 * Drop-in source compatibility: upstream sol4k places every concrete
 * instruction implementation under `org.sol4k.instruction.*`. Artemis keeps
 * them flat in `org.sol4k.*` for cheaper imports and zero-copy access. These
 * typealiases let downstream apps keep their `import org.sol4k.instruction.X`
 * lines unchanged.
 */
package org.sol4k.instruction

typealias BaseInstruction = org.sol4k.BaseInstruction
typealias TransferInstruction = org.sol4k.TransferInstruction
typealias SplTransferInstruction = org.sol4k.SplTransferInstruction
typealias CreateAssociatedTokenAccountInstruction =
    org.sol4k.CreateAssociatedTokenAccountInstruction
typealias SetComputeUnitLimitInstruction = org.sol4k.SetComputeUnitLimitInstruction
typealias SetComputeUnitPriceInstruction = org.sol4k.SetComputeUnitPriceInstruction
typealias Instruction = org.sol4k.Instruction
