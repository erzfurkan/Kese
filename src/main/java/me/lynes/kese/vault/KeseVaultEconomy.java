package me.lynes.kese.vault;

import me.lynes.kese.Database;
import me.lynes.kese.Kese;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class KeseVaultEconomy implements Economy {
    private static final EconomyResponse NOT_IMPLEMENTED = new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    private final Kese plugin = Kese.getInstance();
    private final Database db = plugin.getDatabase();

    private final ConcurrentHashMap<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Set<UUID> dirtySet = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public KeseVaultEconomy() {
        scheduler.scheduleAtFixedRate(this::saveDirtyBalances, 10, 10, TimeUnit.SECONDS);
    }

    public void shutdown() {
        saveDirtyBalances();
        scheduler.shutdown();
    }

    private double getBalanceFromCacheOrDB(UUID uuid) {
        if (!db.isOpen()) {
            plugin.getLogger().warning("Database bağlantısı kapalı! (getBalanceFromCacheOrDB çağrısı engellendi)");
            return 0D;
        }
        Double cached = balances.get(uuid);
        if (cached != null) return cached;
        try (PreparedStatement statement = db.getConnection().prepareStatement("SELECT balance FROM economy WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    double bal = rs.getDouble(1);
                    balances.put(uuid, bal);
                    return bal;
                }
            }
        } catch (SQLException ex) {
            db.report(ex);
        }
        balances.put(uuid, 0D);
        return 0D;
    }

    public void saveDirtyBalances() {
        if (!db.isOpen()) {
            plugin.getLogger().warning("Database bağlantısı kapalı! Kayıt yapılmadı.");
            return;
        }
        Set<UUID> toSave = new HashSet<>(dirtySet);
        dirtySet.clear();
        for (UUID uuid : toSave) {
            Double balance = balances.get(uuid);
            if (balance == null) continue;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (!db.isOpen()) {
                    plugin.getLogger().warning("Database bağlantısı kapalı! (Async kayıt iptal)");
                    return;
                }
                try (PreparedStatement statement = db.getConnection().prepareStatement(
                        "INSERT OR REPLACE INTO economy (uuid, balance) VALUES (?, ?)")) {
                    statement.setString(1, uuid.toString());
                    statement.setDouble(2, balance);
                    statement.executeUpdate();
                } catch (SQLException ex) {
                    db.report(ex);
                }
            });
        }
    }

    private UUID getUUID(String player) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + player).getBytes(UTF_8));
    }

    @Override public boolean isEnabled() { return db.isOpen(); }
    @Override public String getName() { return "Kese"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return -1; }
    @Override public String format(double amount) {
        return String.format(Locale.ENGLISH, "%.2f", amount);
    }
    @Override public String currencyNamePlural() { return null; }
    @Override public String currencyNameSingular() { return null; }

    @Override public boolean hasAccount(String player) { return hasAccount(getUUID(player)); }
    @Override public boolean hasAccount(OfflinePlayer offlinePlayer) { return hasAccount(offlinePlayer.getUniqueId()); }
    private boolean hasAccount(UUID uuid) {
        // getBalanceFromCacheOrDB zaten db.isOpen() korumalı
        return balances.containsKey(uuid) || getBalanceFromCacheOrDB(uuid) > 0;
    }
    @Override public boolean hasAccount(String player, String world) { return hasAccount(player); }
    @Override public boolean hasAccount(OfflinePlayer offlinePlayer, String world) { return hasAccount(offlinePlayer); }

    @Override public double getBalance(String player) { return getBalance(getUUID(player)); }
    @Override public double getBalance(OfflinePlayer offlinePlayer) { return getBalance(offlinePlayer.getUniqueId()); }
    private double getBalance(UUID uuid) { return getBalanceFromCacheOrDB(uuid); }
    @Override public double getBalance(String player, String world) { return getBalance(player); }
    @Override public double getBalance(OfflinePlayer offlinePlayer, String world) { return getBalance(offlinePlayer); }

    public EconomyResponse setBalance(OfflinePlayer offlinePlayer, double balance) {
        UUID uuid = offlinePlayer.getUniqueId();
        balances.put(uuid, balance);
        dirtySet.add(uuid);
        return new EconomyResponse(balance, balance, EconomyResponse.ResponseType.SUCCESS, "");
    }

    public EconomyResponse setBalance(String player, double balance) {
        UUID uuid = getUUID(player);
        balances.put(uuid, balance);
        dirtySet.add(uuid);
        return new EconomyResponse(balance, balance, EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override public boolean has(String player, double amount) { return getBalance(player) >= amount; }
    @Override public boolean has(OfflinePlayer offlinePlayer, double amount) { return getBalance(offlinePlayer) >= amount; }
    @Override public boolean has(String player, String world, double amount) { return has(player, amount); }
    @Override public boolean has(OfflinePlayer offlinePlayer, String world, double amount) { return has(offlinePlayer, amount); }

    @Override public EconomyResponse withdrawPlayer(String player, double amount) { return withdrawPlayer(getUUID(player), amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer offlinePlayer, double amount) { return withdrawPlayer(offlinePlayer.getUniqueId(), amount); }
    private EconomyResponse withdrawPlayer(UUID uuid, double amount) {
        double balance = getBalance(uuid);
        if (balance < amount) return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Yetersiz bakiye");
        balances.put(uuid, balance - amount);
        dirtySet.add(uuid);
        return new EconomyResponse(amount, balance - amount, EconomyResponse.ResponseType.SUCCESS, "");
    }
    @Override public EconomyResponse withdrawPlayer(String player, String world, double amount) { return withdrawPlayer(player, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer offlinePlayer, String world, double amount) { return withdrawPlayer(offlinePlayer, amount); }

    @Override public EconomyResponse depositPlayer(String player, double amount) { return depositPlayer(getUUID(player), amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer offlinePlayer, double amount) { return depositPlayer(offlinePlayer.getUniqueId(), amount); }
    private EconomyResponse depositPlayer(UUID uuid, double amount) {
        double balance = getBalance(uuid);
        balances.put(uuid, balance + amount);
        dirtySet.add(uuid);
        return new EconomyResponse(amount, balance + amount, EconomyResponse.ResponseType.SUCCESS, "");
    }
    @Override public EconomyResponse depositPlayer(String player, String world, double amount) { return depositPlayer(player, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer offlinePlayer, String world, double amount) { return depositPlayer(offlinePlayer, amount); }

    @Override public EconomyResponse createBank(String bank, String player) { return NOT_IMPLEMENTED; }
    @Override public EconomyResponse createBank(String bank, OfflinePlayer offlinePlayer) { return NOT_IMPLEMENTED; }
    @Override public EconomyResponse deleteBank(String bank) { return NOT_IMPLEMENTED; }
    @Override public EconomyResponse bankBalance(String bank) { return NOT_IMPLEMENTED; }
    @Override public EconomyResponse bankHas(String bank, double amount) { return NOT_IMPLEMENTED; }
    @Override public EconomyResponse bankWithdraw(String bank, double amount) { return NOT_IMPLEMENTED; }
    @Override public EconomyResponse bankDeposit(String bank, double amount) { return NOT_IMPLEMENTED; }
    @Override public EconomyResponse isBankOwner(String bank, String player) { return NOT_IMPLEMENTED; }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer offlinePlayer) { return NOT_IMPLEMENTED; }
    @Override public EconomyResponse isBankMember(String bank, String player) { return NOT_IMPLEMENTED; }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer offlinePlayer) { return NOT_IMPLEMENTED; }
    @Override public List<String> getBanks() { return Collections.emptyList(); }

    @Override public boolean createPlayerAccount(String player) { return createPlayerAccount(getUUID(player)); }
    @Override public boolean createPlayerAccount(OfflinePlayer offlinePlayer) { return createPlayerAccount(offlinePlayer.getUniqueId()); }
    @Override public boolean createPlayerAccount(String player, String world) { return createPlayerAccount(player); }
    @Override public boolean createPlayerAccount(OfflinePlayer offlinePlayer, String world) { return createPlayerAccount(offlinePlayer); }
    private boolean createPlayerAccount(UUID uuid) {
        if (balances.containsKey(uuid)) return false;
        balances.put(uuid, 0D);
        dirtySet.add(uuid);
        return true;
    }
}
