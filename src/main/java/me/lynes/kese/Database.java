package me.lynes.kese;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class Database {
    private final Kese plugin = Kese.getInstance();
    private Connection connection;

    public void connect() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Failed to load SQLite JDBC class", ex);
        }
        File database = new File(plugin.getDataFolder(), "database.db");
        try {
            database.getParentFile().mkdirs();
            database.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "File write error: database.db");
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + database);
    }

    public void setup() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS economy (" +
                    "`uuid` varchar(36) NOT NULL, `balance` double NOT NULL, PRIMARY KEY (`uuid`));");
        }
    }

    public Connection getConnection() { return connection; }

    public boolean isOpen() {
        if (connection == null) return false;
        try { return !connection.isClosed(); }
        catch (SQLException exception) { return false; }
    }

    public void disconnect() {
        if (connection != null) {
            try {
                if (!connection.isClosed())
                    connection.close();
            } catch (SQLException ex) {
                plugin.getLogger().warning("Veritabanı bağlantısı kapatılamadı: " + ex.getMessage());
            }
        }
    }

    public void report(SQLException exception) {
        plugin.getLogger().log(Level.SEVERE, "Unhandled exception: " + exception.getMessage(), exception);
    }
}
