/*
 * solana-kmp compatible `Cluster` sealed class and helpers.
 *
 * Upstream exposes `MainnetBeta`, `Devnet`, `Testnet`, `Localnet`, `Custom` as
 * singleton cases plus `resolveClusterFromEndpoint(url)` for inference from a
 * live URL. Port the same shape so call-sites that switch on the sealed class
 * continue to compile.
 */
package foundation.metaplex.rpc

sealed class Cluster {
    object MainnetBeta : Cluster()
    object Devnet : Cluster()
    object Testnet : Cluster()
    object Localnet : Cluster()
    object Custom : Cluster()

    companion object {
        /** Parse a friendly string form back into the sealed variant. */
        fun fromString(clusterName: String): Cluster = when (clusterName.lowercase()) {
            "mainnet-beta", "mainnetbeta", "mainnet" -> MainnetBeta
            "devnet" -> Devnet
            "testnet" -> Testnet
            "localnet", "localhost", "local" -> Localnet
            else -> Custom
        }
    }
}

/** Hostname patterns associated with each cluster. Matches upstream constants. */
val MAINNET_BETA_DOMAINS: List<String> = listOf(
    "api.mainnet-beta.solana.com",
    "solana-api.projectserum.com",
    "mainnet-beta.solana.com",
)
val DEVNET_DOMAINS: List<String> = listOf(
    "api.devnet.solana.com",
    "devnet.solana.com",
)
val TESTNET_DOMAINS: List<String> = listOf(
    "api.testnet.solana.com",
    "testnet.solana.com",
)
val LOCALNET_DOMAINS: List<String> = listOf(
    "localhost",
    "127.0.0.1",
)

/**
 * Best-effort inference of the cluster from an RPC endpoint URL. Returns
 * [Cluster.Custom] when no pattern matches; never throws.
 */
fun resolveClusterFromEndpoint(endpoint: String): Cluster {
    val host = extractHost(endpoint)?.lowercase() ?: return Cluster.Custom
    return when {
        MAINNET_BETA_DOMAINS.any { host.contains(it) } -> Cluster.MainnetBeta
        DEVNET_DOMAINS.any { host.contains(it) } -> Cluster.Devnet
        TESTNET_DOMAINS.any { host.contains(it) } -> Cluster.Testnet
        LOCALNET_DOMAINS.any { host == it || host.startsWith(it) } -> Cluster.Localnet
        else -> Cluster.Custom
    }
}

// Cheap URL host extractor. `java.net.URI` is not available in commonMain, so
// do the minimum string parsing we need.
private fun extractHost(url: String): String? {
    val schemeIdx = url.indexOf("://")
    val afterScheme = if (schemeIdx >= 0) url.substring(schemeIdx + 3) else url
    val pathIdx = afterScheme.indexOf('/').let { if (it < 0) afterScheme.length else it }
    val authority = afterScheme.substring(0, pathIdx)
    // Strip user-info and port
    val atIdx = authority.indexOf('@')
    val hostAndPort = if (atIdx >= 0) authority.substring(atIdx + 1) else authority
    val colonIdx = hostAndPort.indexOf(':')
    return if (colonIdx >= 0) hostAndPort.substring(0, colonIdx) else hostAndPort
}
