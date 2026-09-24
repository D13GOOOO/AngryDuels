package com.angryguyy.duels.stats;

import com.angryguyy.duels.DuelsPlugin;
import com.angryguyy.duels.util.Log;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages the MySQL connection pool, schema creation and the retry queue
 * used when the database becomes unreachable during runtime.
 *
 * <p>On startup, the manager first connects to the MySQL server without
 * selecting a database and issues a {@code CREATE DATABASE IF NOT EXISTS}
 * statement. It then opens a HikariCP pool against the target database
 * and executes the bundled {@code schema.sql} to ensure all tables exist.</p>
 *
 * <p>When a query fails because the database is down, the caller may push
 * the operation into the retry queue. A scheduled task drains the queue
 * every few seconds until the connection is restored.</p>
 */
public class DatabaseManager {

    private final DuelsPlugin plugin;
    private final List<Consumer<Connection>> retryQueue = new ArrayList<>();

    private HikariDataSource dataSource;
    private boolean ready;

    /**
     * Creates a new database manager.
     *
     * @param plugin owning plugin
     */
    public DatabaseManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the database: creates the database if missing, opens
     * the connection pool and applies the schema.
     *
     * @return {@code true} if the database is ready to use
     */
    public boolean init() {
        if (!plugin.config().statsEnabled()) {
            Log.info("Stats are disabled in config.");
            return false;
        }

        String host = plugin.config().mysqlHost();
        int port = plugin.config().mysqlPort();
        String db = plugin.config().mysqlDatabase();
        String user = plugin.config().mysqlUsername();
        String pass = plugin.config().mysqlPassword();
        int poolSize = plugin.config().mysqlPoolSize();
        boolean useSsl = plugin.config().mysqlUseSsl();

        try {
            createDatabaseIfMissing(host, port, db, user, pass, useSsl);
            openPool(host, port, db, user, pass, poolSize, useSsl);
            applySchema();
            startRetryTask();
            this.ready = true;
            Log.info("MySQL connected: %s:%d/%s", host, port, db);
            return true;
        } catch (Exception e) {
            Log.error(e, "MySQL connection failed. Stats will be disabled until restart.");
            this.ready = false;
            return false;
        }
    }

    /**
     * Connects without selecting a database and creates it if missing.
     */
    private void createDatabaseIfMissing(String host, int port, String db,
                                         String user, String pass, boolean ssl)
            throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + port + "/?useSSL=" + ssl
                + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection conn = java.sql.DriverManager.getConnection(url, user, pass);
             Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + db
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    /**
     * Opens the HikariCP pool against the target database.
     */
    private void openPool(String host, int port, String db,
                          String user, String pass, int poolSize, boolean ssl) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(poolSize);
        cfg.setPoolName("AngryDuels-Pool");
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.dataSource = new HikariDataSource(cfg);
    }

    /**
     * Executes the bundled schema.sql file to create missing tables.
     */
    private void applySchema() {
        InputStream is = plugin.getResource("schema.sql");
        if (is == null) {
            Log.error("schema.sql not found in resources!");
            return;
        }
        String sql;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            sql = reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            Log.error(e, "Failed to read schema.sql");
            return;
        }
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    st.executeUpdate(trimmed);
                }
            }
        } catch (SQLException e) {
            Log.error(e, "Failed to apply schema.sql");
        }
    }

    /**
     * Starts the scheduled task that drains the retry queue.
     */
    private void startRetryTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (retryQueue.isEmpty()) return;
            if (!isConnected()) return;
            List<Consumer<Connection>> pending = new ArrayList<>(retryQueue);
            retryQueue.clear();
            for (Consumer<Connection> op : pending) {
                try (Connection conn = dataSource.getConnection()) {
                    op.accept(conn);
                } catch (SQLException e) {
                    retryQueue.add(op);
                }
            }
        }, 200L, 200L);
    }

    /**
     * Returns the connection pool, or {@code null} if not ready.
     */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    /**
     * Checks whether the pool is open and reachable.
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Checks whether the manager is ready to serve queries.
     */
    public boolean isReady() {
        return ready && isConnected();
    }

    /**
     * Pushes an operation to the retry queue.
     *
     * @param op operation to retry later
     */
    public void queue(Consumer<Connection> op) {
        retryQueue.add(op);
    }

    /**
     * Closes the connection pool.
     */
    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}