package me.lynes.kese;

import me.lynes.kese.cmds.AltinCmd;
import me.lynes.kese.cmds.KeseAdminCmd;
import me.lynes.kese.cmds.KeseCmd;
import me.lynes.kese.listeners.PlayerListener;
import me.lynes.kese.utils.UpdateChecker;
import me.lynes.kese.vault.KeseVaultEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class Kese extends JavaPlugin {

    private static Kese instance;
    private KeseVaultEconomy economy;
    private Database db;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        this.db = new Database();

        try {
            db.connect();
            db.setup();
        } catch (SQLException exception) {
            db.report(exception);
            getLogger().severe("Veritabanı başlatılamadı, ekonomi sistemi devre dışı!");
        }

        // bStats, update checker vs...
        new Metrics(this, 13183);
        UpdateChecker.check(this);
        UpdateChecker.sendToConsole(this);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> UpdateChecker.check(this), 1728000, 1728000);

        // Vault integration
        economy = new KeseVaultEconomy();
        Bukkit.getServicesManager().register(Economy.class, economy, instance, ServicePriority.Normal);
        getCommand("kese").setExecutor(new KeseCmd());
        getCommand("altin").setExecutor(new AltinCmd());
        getCommand("keseadmin").setExecutor(new KeseAdminCmd());
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
    }

    @Override
    public void onDisable() {
        try {
            if (economy != null) economy.saveDirtyBalances();
            if (economy != null) economy.shutdown();
            if (db != null) db.disconnect();
        } catch (Exception exception) {
            if (db != null) db.report(exception instanceof SQLException ? (SQLException)exception : new SQLException(exception));
        }
    }

    public static Kese getInstance() { return instance; }
    public Database getDatabase() { return db; }
    public KeseVaultEconomy getEconomy() { return economy; }
}
