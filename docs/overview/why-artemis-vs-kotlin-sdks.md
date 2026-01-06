# Why Artemis vs other Solana Kotlin SDKs

Artemis is designed to be the comprehensive, **mobile-first** standard for Solana development on the JVM (Android & server). While several Kotlin SDKs exist, the landscape has been fragmented, with most libraries either unmaintained (`solana-kt`) or too lightweight for complex dApps (`sol4k`).

Artemis unifies these approaches, offering a modular, fully-featured SDK that supports modern Solana standards like **Token 2022**, **Versioned Transactions**, and **MPL Core** out of the box.

## SDK Landscape Comparison

| Feature | **solana-kt** (Legacy/Metaplex) | **metaplex-kmm** (KMP) | **sol4k** (Lightweight) | **Artemis** |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Focus** | General Purpose (Legacy) | NFT Metadata / Standard | Lightweight / Scripts | **Modern Mobile Apps** |
| **Maintenance** | ⚠️ Maintenance Mode | ⚠️ Sporadic / Verticals | ✅ Active Community | 🚀 **Active Development** |
| **Token 2022** | ❌ Basic Support | ❌ No native TLV support | ⚠️ Manual Parsing | ✅ **First-Class Support** |
| **Transactions** | Legacy (v1) | Abstracted | ✅ **v0 + ALTs** | ✅ **v0 + ALTs** |
| **Metaplex** | Candy Machine v2 | Metadata Decoding | Manual Encoding | ✅ **CM v3, Core, cNFTs** |
| **Mobile Wallet Adapter** | Manual Integration | Manual Integration | Manual Integration | ✅ **Built-in Helpers** |
| **Async Model** | Coroutines | Flow / Coroutines | Coroutines | **Coroutines + Flow** |

---

## Detailed Feature Breakdown

### 1. Token 2022 (Token Extensions)
Modern Solana development relies heavily on Token Extensions (transfer hooks, confidential transfers, metadata pointer).
*   **Others**: Most SDKs treat Token 2022 as "just another program call," leaving you to manually parse complex Type-Length-Value (TLV) byte structures.
*   **Artemis**: Provides a native, type-safe decoder for Token 2022 state. You can read Transfer Fees, Interest Bearing configs, and more without dealing with raw bytes.

### 2. Metaplex & NFT Standards
Artemis offers the most complete implementation of modern Metaplex standards on Android.
*   **MPL Core**: Full support for the new low-cost asset standard.
*   **Candy Machine v3**: Includes sophisticated "Guard" validation—Artemis can dry-run a mint transaction locally to tell the user *exactly* why a mint might fail (e.g., "Wrong Date", "Sol Balance Low") before sending it.
*   **Compressed NFTs (cNFT)**: Read and transfer Bubblegum assets natively.

### 3. Versioned Transactions & Address Lookup Tables (ALT)
To fit complex protocols into a single transaction, Versioned Transactions (v0) are essential.
*   **Legacy SDKs**: `solana-kt` often defaults to "Legacy" transactions, which fail when logic becomes complex.
*   **Artemis**: Deep support for v0 transaction compilation and Address Lookup Table resolution, ensuring your complex DeFi or Gaming transactions land successfully.

### 4. Drop-in Compatibility
Artemis 1.0.4+ introduces API compatibility layers for `solana-kt` users.
*   **Classes**: `Pubkey`, `Transaction`, and `SystemProgram` expose methods familiar to legacy users (e.g., `Pubkey("base58String")`, `createProgramAddress`).
*   **No Wrappers**: We don't just wrap the old library; we implemented the API surface on top of our optimized Core, so you get the performance of Artemis with the familiarity of the existing ecosystem.

## When to use what?

*   **Use `sol4k`**: If you are writing a tiny server-side bot or script, don't need Token 2022, and want the absolute smallest JAR size possible.
*   **Use `metaplex-kmm`**: If you are building a pure Kotlin Multiplatform project and share 100% of your business logic with iOS (though feature parity may be lower).
*   **Use `Artemis`**: If you are building a production **Android Application**. It is the only choice that combines Mobile Wallet Adapter integration, reliable Transaction construction, and full support for the modern Solana program ecosystem.
