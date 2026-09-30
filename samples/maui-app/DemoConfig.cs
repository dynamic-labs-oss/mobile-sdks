using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Static configuration values only (no client state).</summary>
public static class DemoConfig
{
    public const string EnvironmentId = "3e219b76-dcf1-40ab-aad6-652c4dfab4cc";
    public const string ApiBaseUrl = "https://app.dynamicauth.com/api/v0";
    public const string NativeLink = "dynamicdemo://";
    public const string UniversalLink = "https://demo.dynamic.xyz";

    /// <summary>EVM networks the demo supports (first = initial active network).</summary>
    public static readonly IReadOnlyList<EvmNetwork> EvmNetworks = new[]
    {
        new EvmNetwork(11155111, "https://ethereum-sepolia-rpc.publicnode.com", "Sepolia"),
        new EvmNetwork(84532, "https://sepolia.base.org", "Base Sepolia"),
        new EvmNetwork(1, "https://ethereum-rpc.publicnode.com", "Ethereum"),
        new EvmNetwork(137, "https://polygon-bor-rpc.publicnode.com", "Polygon"),
    };

    /// <summary>Solana networks the demo supports (first = initial active network).</summary>
    public static readonly IReadOnlyList<SolanaNetwork> SolanaNetworks = new[]
    {
        new SolanaNetwork("https://api.devnet.solana.com", "Devnet", "devnet", "EtWTRABZaYq6iMfeYKouRu166VU2xqa1"),
        new SolanaNetwork("https://api.mainnet-beta.solana.com", "Mainnet Beta", "mainnet-beta", "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"),
    };

    /// <summary>BTC networks the demo supports (first = default network — see
    /// BtcClient's doc for why there's no headless active-network switch
    /// the way EVM/Solana/Sui have one).</summary>
    public static readonly List<BtcNetwork> BtcNetworks = new()
    {
        new BtcNetwork("Testnet", "https://blockstream.info/testnet/api", isTestnet: true),
    };

    /// <summary>Sui networks the demo supports (first = initial active network).</summary>
    public static readonly List<SuiNetwork> SuiNetworks = new()
    {
        new SuiNetwork("https://graphql.testnet.sui.io/graphql", "Testnet", "testnet"),
        new SuiNetwork("https://graphql.mainnet.sui.io/graphql", "Mainnet", "mainnet"),
    };

    public static readonly IReadOnlyList<StellarNetwork> StellarNetworks = new[]
    {
        new StellarNetwork(
            "Test SDF Network ; September 2015",
            "https://horizon-testnet.stellar.org",
            "Testnet",
            "2"),
    };

    public static readonly List<TonNetwork> TonNetworks = new()
    {
        new TonNetwork("https://testnet.toncenter.com/api/v2/jsonRPC", "Testnet", -3),
    };
}
