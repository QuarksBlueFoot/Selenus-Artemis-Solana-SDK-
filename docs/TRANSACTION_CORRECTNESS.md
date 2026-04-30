# Transaction correctness

Transaction correctness means Artemis builds the same canonical Solana wire bytes as a trusted reference encoder for the same inputs. This is required before claiming drop-in behavior for transaction builders or migration equivalence with another SDK.

## Required guarantees

| Area | Requirement |
| --- | --- |
| Legacy messages | Account ordering, signer flags, writable flags, recent blockhash, instruction account indexes, and signatures match canonical encoding |
| Versioned messages | v0 prefix, static keys, address table lookups, compiled instructions, and signatures match canonical encoding |
| Address lookup tables | Writable and readonly lookup indexes preserve order and de-duplicate only where the Solana message format allows |
| Multi-signer flows | Signatures are placed by signer pubkey index and missing signatures remain detectable |
| Program instructions | System, SPL Token, Token-2022, ATA, Compute Budget, Stake, Memo, Anchor discriminators, and custom instructions encode exact data bytes |
| Simulation pipeline | Simulation must not mutate transaction bytes except for explicit blockhash refresh or signer replacement chosen by the caller |

## Current in-repo coverage

The repository already has structural round-trip tests that should stay green:

- Legacy transaction model: [../foundation/artemis-tx/src/commonTest/kotlin/com/selenus/artemis/tx/TxModuleTest.kt](../foundation/artemis-tx/src/commonTest/kotlin/com/selenus/artemis/tx/TxModuleTest.kt)
- v0 transaction model: [../foundation/artemis-vtx/src/commonTest/kotlin/com/selenus/artemis/vtx/VtxModuleTest.kt](../foundation/artemis-vtx/src/commonTest/kotlin/com/selenus/artemis/vtx/VtxModuleTest.kt)
- Transaction engine behavior: [../foundation/artemis-vtx/src/commonTest/kotlin/com/selenus/artemis/vtx/TxEngineTest.kt](../foundation/artemis-vtx/src/commonTest/kotlin/com/selenus/artemis/vtx/TxEngineTest.kt)
- Signature ordering: [../foundation/artemis-vtx/src/commonTest/kotlin/com/selenus/artemis/vtx/SignatureOrderingTest.kt](../foundation/artemis-vtx/src/commonTest/kotlin/com/selenus/artemis/vtx/SignatureOrderingTest.kt)
- Program instruction builders: [../foundation/artemis-programs/src/commonTest/kotlin/](../foundation/artemis-programs/src/commonTest/kotlin/)

## Missing proof before stronger claims

The existing tests prove Artemis internal round trips and many builder invariants. They do not, by themselves, prove byte-for-byte equivalence with another SDK release.

Before claiming equivalence with `web3.js`, Solana Kit, solana-kmp, Sol4k, or any specific upstream artifact, add a fixture set with:

1. Reference SDK name and exact version.
2. Input JSON for accounts, instructions, recent blockhash, fee payer, address lookup tables, and signers.
3. Reference serialized message bytes.
4. Artemis serialized message bytes.
5. Reference signed transaction bytes.
6. Artemis signed transaction bytes.
7. A failing diff that prints the first mismatched byte and decoded message section.

## Fixture matrix

| Fixture | Legacy | v0 | ALT | Multi-signer | Expected status |
| --- | --- | --- | --- | --- | --- |
| System transfer | yes | yes | no | no | Required |
| SPL token transfer checked | yes | yes | no | no | Required |
| Token-2022 transfer checked | yes | yes | no | no | Required |
| ATA create + token transfer | yes | yes | no | no | Required |
| Compute budget + transfer | yes | yes | no | no | Required |
| Memo + transfer | yes | yes | no | no | Required |
| Address lookup table transfer | no | yes | yes | no | Required |
| Two-signer instruction | yes | yes | optional | yes | Required |
| Anchor instruction with 8-byte discriminator | yes | yes | optional | optional | Required |
| Durable nonce transaction | yes | no | no | optional | Required before durable-nonce equivalence claims |

## Suggested artifact layout

```text
testing/transaction-fixtures/
|-- web3js-1.98.4/
|   |-- system-transfer.json
|   |-- system-transfer.expected.bin
|   `-- system-transfer.signed.expected.bin
|-- sol4k-0.7.0/
`-- README.md
```

Do not commit private keys. Use deterministic test keypairs only.

## Claim policy

- Internal serialization support can be claimed when Artemis round-trip tests pass.
- Equivalence to a named external SDK can be claimed only for fixture categories that have byte-level passing tests against that SDK version.
- Broad migration claims must link to the passing fixture matrix and the release commit that generated it.
