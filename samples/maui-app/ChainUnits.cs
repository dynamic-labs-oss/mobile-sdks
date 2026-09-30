using System.Numerics;
using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Per-chain native-unit metadata (decimals + display symbol) and
/// shared balance helpers for the four Tier-1 chains (EVM/SOL/BTC/SUI) —
/// used by HomePage, WalletDetailsPage, and SendPage so the unit
/// conversion isn't hand-duplicated three times over. EVM = wei (18
/// decimals), SOL/SUI = lamports/MIST (9 decimals each), BTC = satoshis (8
/// decimals). Every chain here has its own extension installed in App.cs
/// (UseEvm/UseSolana/UseBtc/UseSui), so GetBalanceUnitsAsync never needs a
/// fallback branch.</summary>
public static class ChainUnits
{
    public static string UnitName(string chain) => chain switch
    {
        "EVM" => "ETH",
        "SOL" => "SOL",
        "BTC" => "BTC",
        "SUI" => "SUI",
        _ => chain,
    };

    public static BigInteger UnitsPerToken(string chain) => chain switch
    {
        "EVM" => BigInteger.Pow(10, 18),
        "BTC" => BigInteger.Pow(10, 8),
        // SOL (lamports) and SUI (MIST) both use 9 decimals.
        _ => BigInteger.Pow(10, 9),
    };

    public static string UnitsToToken(string chain, BigInteger units)
    {
        var unitsPerToken = UnitsPerToken(chain);
        var whole = units / unitsPerToken;
        var fracDigits = unitsPerToken.ToString().Length - 1;
        var frac = (units % unitsPerToken).ToString().PadLeft(fracDigits, '0').TrimEnd('0');
        return frac.Length == 0 ? whole.ToString() : $"{whole}.{frac}";
    }

    public static BigInteger TokenToUnits(string chain, string token)
    {
        var unitsPerToken = UnitsPerToken(chain);
        var fracDigits = unitsPerToken.ToString().Length - 1;
        var parts = token.Split('.');
        var whole = BigInteger.Parse(string.IsNullOrEmpty(parts[0]) ? "0" : parts[0]);
        var fracStr = (parts.Length > 1 ? parts[1] : string.Empty).PadRight(fracDigits, '0').Substring(0, fracDigits);
        return whole * unitsPerToken + BigInteger.Parse(string.IsNullOrEmpty(fracStr) ? "0" : fracStr);
    }

    /// <summary>Native balance of <paramref name="wallet"/>'s address, in the
    /// chain's base units, via whichever chain extension is installed for
    /// wallet.Chain. Every chain returns a decimal string;
    /// Solana/Sui return a decimal string — BigInteger is the one type all
    /// four chains' results convert into without precision loss.</summary>
    public static async Task<BigInteger> GetBalanceUnitsAsync(DynamicClient client, Wallet wallet) => wallet.Chain switch
    {
        "EVM" => await client.Evm().GetBalanceAsync(wallet.Address),
        "SOL" => BigInteger.Parse(await client.Solana().GetBalanceAsync(wallet.Address)),
        "SUI" => BigInteger.Parse(await client.Sui().GetBalanceAsync(wallet.Address)),
        // A decimal string now, like every other chain's balance.
        "BTC" => BigInteger.Parse(await client.Btc().GetBalanceAsync(wallet.Address)),
        _ => throw new DynamicStateException($"no chain extension installed for chain {wallet.Chain}"),
    };

    public static async Task<string> FormatBalanceAsync(DynamicClient client, Wallet wallet)
    {
        var units = await GetBalanceUnitsAsync(client, wallet);
        return $"{UnitsToToken(wallet.Chain, units)} {UnitName(wallet.Chain)}";
    }
}
