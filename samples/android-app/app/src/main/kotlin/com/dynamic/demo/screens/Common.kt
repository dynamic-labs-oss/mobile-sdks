// Shared helpers used by more than one screen: unit conversions, the chain
// picker's option list (kept in ONE place so DESIGN.md/CAPABILITIES.md's
// "BTC/EVM/Solana/Sui everywhere" rule can't drift between Home and Create
// Wallet), and the balance-line composables. Split out of the former
// single-file MainActivity.kt (task #8 — keep individual screen files small).
package com.dynamic.demo.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dynamic.demo.Sdk
import com.dynamic.sdk.EvmNetwork
import com.dynamic.sdk.SolanaNetwork
import com.dynamic.sdk.Wallet
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * Every Tier-1-supported chain (full read/build/sign/broadcast on every
 * platform per DESIGN.md §9 / CAPABILITIES.md's consistency-fix section) —
 * the single list every chain picker (Home, Create Wallet, Import Private
 * Key) must offer, so they can never drift apart again.
 */
val CHAIN_OPTIONS: List<String> = listOf("EVM", "SOL", "BTC", "SUI")

// 1 ETH == 10^18 wei, 1 SOL == 10^9 lamports, 1 BTC == 10^8 satoshis,
// 1 SUI == 10^9 MIST; the demo shows/accepts human-readable token amounts
// and converts to/from the on-wire integer unit.
val WEI_PER_ETH: BigDecimal = BigDecimal.TEN.pow(18)
val LAMPORTS_PER_SOL: BigDecimal = BigDecimal.TEN.pow(9)
val SATOSHIS_PER_BTC: BigDecimal = BigDecimal.TEN.pow(8)
val MIST_PER_SUI: BigDecimal = BigDecimal.TEN.pow(9)

fun unitsToToken(units: BigInteger, unitsPerToken: BigDecimal): String =
    BigDecimal(units).divide(unitsPerToken).stripTrailingZeros().toPlainString()

fun tokenToUnits(token: String, unitsPerToken: BigDecimal): BigInteger =
    BigDecimal(token).multiply(unitsPerToken).toBigInteger()

fun shortAddr(address: String): String =
    if (address.length > 12) "${address.take(6)}…${address.takeLast(6)}" else address

fun unitNameFor(chain: String): String = when (chain) {
    "SOL" -> "SOL"
    "BTC" -> "BTC"
    "SUI" -> "SUI"
    else -> "ETH"
}

fun unitsPerTokenFor(chain: String): BigDecimal = when (chain) {
    "SOL" -> LAMPORTS_PER_SOL
    "BTC" -> SATOSHIS_PER_BTC
    "SUI" -> MIST_PER_SUI
    else -> WEI_PER_ETH
}

// getBalance(address, network) accepts any address, not just the caller's
// own — network defaults to whichever chain is currently active. Fetch is
// keyed on the wallet's address so it doesn't re-run on every recomposition
// (Home rebuilds on userChanged/walletCreated).
@Composable
fun WalletBalanceLine(wallet: Wallet) {
    val unitName = unitNameFor(wallet.chain)
    val unitsPerToken = unitsPerTokenFor(wallet.chain)
    var balance by remember(wallet.address) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(wallet.address) {
        balance = try {
            val units = when (wallet.chain) {
                "SOL" -> BigInteger(Sdk.solana.getBalance(wallet.address))
                // BtcExtension.getBalance already returns Long, unlike Evm/
                // Solana's decimal-string wire shape — see EsploraBtcService's
                // doc for why satoshis fit comfortably in one without needing
                // BigInteger.
                // A decimal string now, like every other chain's balance in this SDK
                // (the flow returns what esplora's totals subtract to).
                "BTC" -> BigInteger(Sdk.btc.getBalance(wallet.address))
                "SUI" -> BigInteger(Sdk.sui.getBalance(wallet.address))
                else -> Sdk.evm.getBalance(wallet.address)
            }
            "${unitsToToken(units, unitsPerToken)} $unitName"
        } catch (e: Exception) {
            "unavailable"
        }
    }
    Text("Balance: ${balance ?: "loading…"}", style = MaterialTheme.typography.bodyMedium)
}

// No getMultichainBalance primitive exists anywhere in the SDK (spec, generator,
// or the native Evm/Solana extension packages) — this loops availableNetworks()
// and calls the existing per-call getBalance(address, network) once per network,
// concurrently, catching failures individually so one dead RPC doesn't blank the
// whole list.
@Composable
fun MultichainBalances(wallet: Wallet, evmNetworks: List<EvmNetwork>, solanaNetworks: List<SolanaNetwork>) {
    val isSolana = wallet.chain == "SOL"
    val unitName = if (isSolana) "SOL" else "ETH"
    val unitsPerToken = if (isSolana) LAMPORTS_PER_SOL else WEI_PER_ETH
    var results by remember(wallet.address) { mutableStateOf<List<Pair<String, String>>?>(null) }
    androidx.compose.runtime.LaunchedEffect(wallet.address) {
        results = if (isSolana) {
            solanaNetworks.map { network ->
                async {
                    network.name to try {
                        "${unitsToToken(BigInteger(Sdk.solana.getBalance(wallet.address, network)), unitsPerToken)} $unitName"
                    } catch (e: Exception) {
                        "unavailable"
                    }
                }
            }.awaitAll()
        } else {
            evmNetworks.map { network ->
                async {
                    network.name to try {
                        "${unitsToToken(Sdk.evm.getBalance(wallet.address, network), unitsPerToken)} $unitName"
                    } catch (e: Exception) {
                        "unavailable"
                    }
                }
            }.awaitAll()
        }
    }
    if (results == null) {
        Text("loading…", style = MaterialTheme.typography.bodyMedium)
    } else {
        results!!.forEach { (name, balance) -> Text("$name: $balance", style = MaterialTheme.typography.bodyMedium) }
    }
}
