package com.selenus.artemis.conformance

import android.net.Uri
import com.funkatronics.encoders.Base58 as MultimultBase58
import com.funkatronics.encoders.error.InvalidInputException
import com.metaplex.lib.Connection as MetaplexConnection
import com.metaplex.lib.Metaplex
import com.selenus.artemis.runtime.Base58 as ArtemisBase58
import com.selenus.artemis.runtime.Pubkey
import com.solana.mobilewalletadapter.clientlib.AuthorizationResult
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.networking.HttpNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.networking.HttpRequest
import com.solana.programs.SystemProgram
import com.solana.publickey.SolanaPublicKey
import com.solana.publickey.asWeb3Solana
import com.solana.rpccore.JsonRpc20Request
import com.solanamobile.seedvault.SeedVault
import com.solanamobile.seedvault.WalletContractV1
import foundation.metaplex.amount.Lamports
import foundation.metaplex.base58.encodeToBase58String
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sol4k.PublicKey as Sol4kPublicKey

class CompatImportConformanceTest {

  @Test
  fun sourceCompatibleImportsResolveAcrossReplacementShims() {
    val hello = "Hello World".encodeToByteArray()
    val encoded = MultimultBase58.encodeToString(hello)
    assertEquals("JxF12TrwUP45BMd", encoded)
    assertArrayEquals(hello, MultimultBase58.decode(encoded))
    assertThrows(InvalidInputException.InvalidCharacter::class.java) {
      MultimultBase58.decode("0")
    }

    val zeroBytes = ByteArray(32)
    val artemisPubkey = Pubkey(zeroBytes)
    val sol4kPubkey = Sol4kPublicKey(zeroBytes)
    val web3Pubkey = artemisPubkey.asWeb3Solana()

    assertEquals("11111111111111111111111111111111", sol4kPubkey.toBase58())
    assertEquals("11111111111111111111111111111111", web3Pubkey.base58())
    assertEquals("11111111111111111111111111111111", zeroBytes.encodeToBase58String())

    val recipient = SolanaPublicKey(ByteArray(32) { if (it == 31) 1 else 0 })
    val transfer = SystemProgram.transfer(web3Pubkey, recipient, lamports = 1L)
    assertEquals(SystemProgram.PROGRAM_ID, transfer.programId)
    assertEquals(2, transfer.accounts.size)

    val lamports = Lamports(1_000_000_000)
    assertEquals("SOL", lamports.identifier)
    assertEquals(9, lamports.decimals)

    assertEquals(ArtemisBase58.encode(zeroBytes), MultimultBase58.encodeToString(zeroBytes))
  }

  @Test
  fun mobileCompatTypesKeepMwaAndSeedVaultShapes() {
    val identity = ConnectionIdentity(
      identityUri = mockk<Uri>(relaxed = true),
      iconUri = mockk<Uri>(relaxed = true),
      identityName = "Artemis Conformance"
    )
    val adapter = MobileWalletAdapter(identity, Solana.Devnet)

    assertEquals(90_000, MobileWalletAdapter.DEFAULT_CLIENT_TIMEOUT_MS)
    assertEquals(null, adapter.authToken)
    assertEquals("solana:devnet", Solana.Devnet.fullName)

    val auth = AuthorizationResult(
      authToken = "token",
      publicKey = ByteArray(32),
      accountLabel = "primary",
      walletUriBase = null,
      walletIcon = null,
      accounts = listOf(
        AuthorizationResult.Account(
          publicKey = ByteArray(32),
          displayAddress = "1111...1111",
          displayAddressFormat = "base58",
          label = "primary",
          chains = listOf("solana:devnet"),
          features = listOf("solana:signTransactions")
        )
      )
    )

    assertEquals("token", auth.authToken)
    assertEquals("primary", auth.accounts.single().label)
    assertFalse(SeedVault.AccessType.NONE.isGranted())
    assertEquals(0, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
    assertTrue(WalletContractV1.ACTION_SIGN_TRANSACTION.endsWith("ACTION_SIGN_TRANSACTION"))
  }

  @Test
  fun rpcCoreAndMetaplexCompatSurfacesAreUsableWithoutNetwork() = runBlocking {
    val driver = Rpc20Driver(
      url = "https://rpc.example",
      networkDriver = StaticNetworkDriver("""{"jsonrpc":"2.0","id":"1","result":"ok"}""")
    )
    val response = driver.makeRequest(JsonRpc20Request("getHealth", id = "1"), String.serializer())
    assertEquals("ok", response.result)
    assertEquals(null, response.error)

    val metaplex = Metaplex(MetaplexConnection("https://api.devnet.solana.com"))
    val unsupportedMint = metaplex.candyMachines.mint("11111111111111111111111111111111")
    assertTrue(unsupportedMint.message.contains("not implemented"))
    assertEquals(null, metaplex.identityDriver.publicKey)
  }

  private class StaticNetworkDriver(private val response: String) : HttpNetworkDriver {
    var lastRequest: HttpRequest? = null

    override suspend fun makeHttpRequest(request: HttpRequest): String {
      lastRequest = request
      return response
    }
  }
}