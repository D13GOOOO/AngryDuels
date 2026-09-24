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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
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
 * and executes the bundled {@code schema.sql} to ensure all tables exist.
 * Finally, it schedules the retry task that drains the offline queue.</p>
 *
 * <p>Two distinct readiness concepts are exposed:</p>
 * <ul>
 *     <li>{@link #isReady()} is a cheap flag check, safe to call from the
 *     main thread. It only says whether the pool has been initialized,
 *     not whether MySQL is currently reachable.</li>
 *     <li>{@link #ping()} performs a real round-trip and must only be
 *     called from async threads, since it can block for up to the
 *     configured connection timeout.</li>
 * </ul>
 *
 * <p>The retry queue is thread-safe and may be filled from any thread.
 * The drain task runs asynchronously and periodically, and re-inserts
 * any operation that fails so that no data is silently lost.</p>
 */
public class DatabaseManager {

    private final DuelsPlugin plugin;
    private final List<Consumer<Connection>> retryQueue =
            Collections.synchronizedList(new ArrayList<>());

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
     * the connection pool, applies the schema and starts the retry task.
     *
     * <p>If any step fails, the manager is left in a non-ready state,
     * the error is logged, and the plugin continues to run without
     * statistics. This allows the rest of the duel system to function
     * even when MySQL is temporarily unavailable at startup.</p>
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
     * Connects to the MySQL server without selecting a database and
     * creates the target database if it does not exist yet.
     *
     * <p>The connection is opened with a plain {@link DriverManager}
     * call rather than through the pool, because the pool URL requires
     * the database to already exist.</p>
     *
     * <p>The database name is sanitized before being embedded in the
     * statement: any backtick is stripped, since MySQL uses backticks as
     * the identifier delimiter and an unsanitized name would allow SQL
     * injection through the config file.</p>
     *
     * @param host MySQL host
     * @param port MySQL port
     * @param db   database name to create if missing
     * @param user MySQL username
     * @param pass MySQL password
     * @param ssl  whether to require SSL
     * @throws SQLException if the connection or the CREATE statement fails
     */
    private void createDatabaseIfMissing(String host, int port, String db,
                                         String user, String pass, boolean ssl)
            throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + port + "/?useSSL=" + ssl
                + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String safeDb = db.replace("`", "");
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + safeDb
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    /**
     * Opens the HikariCP pool against the target database.
     *
     * <p>The timeouts are chosen to be tolerant of slow first responses
     * from a local MySQL instance. {@code minimumIdle} and
     * {@code keepaliveTime} are intentionally left at their HikariCP
     * defaults, because eagerly-created idle connections can cause the
     * pool to fail initialization on slower machines.</p>
     *
     * @param host     MySQL host
     * @param port     MySQL port
     * @param db       database name
     * @param user     MySQL username
     * @param pass     MySQL password
     * @param poolSize maximum pool size
     * @param ssl      whether to require SSL
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
        cfg.setConnectionTimeout(10000);
        cfg.setValidationTimeout(5000);
        cfg.setIdleTimeout(600000);
        cfg.setMaxLifetime(1800000);
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.dataSource = new HikariDataSource(cfg);
    }

    /**
     * Executes the bundled {@code schema.sql} file to create any missing
     * table.
     *
     * <p>The file is split on semicolons and each statement is executed
     * individually. Statements that fail do not abort the loop, so a
     * single problem in one table does not prevent the others from being
     * created.</p>
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
     * Starts the repeating task that drains the retry queue.
     *
     * <p>The task runs asynchronously every ten seconds. It skips work
     * when the queue is empty or when {@link #ping()} reports that MySQL
     * is still unreachable. Operations that fail during the drain are
     * re-inserted into the queue so that no data is lost.</p>
     *
     * <p>The queue is copied under its own lock before being drained, so
     * concurrent producers calling {@link #queue(Consumer)} while the
     * task is running do not cause lost updates or iteration errors.</p>
     */
    private void startRetryTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (retryQueue.isEmpty()) return;
            if (!ping()) return;

            List<Consumer<Connection>> snapshot;
            synchronized (retryQueue) {
                snapshot = new ArrayList<>(retryQueue);
                retryQueue.clear();
            }

            for (Consumer<Connection> op : snapshot) {
                try (Connection conn = dataSource.getConnection()) {
                    op.accept(conn);
                } catch (Exception e) {
                    retryQueue.add(op);
                }
            }
        }, 200L, 200L);
    }

    /**
     * Returns the connection pool.
     *
     * @return the pool, or {@code null} if it has not been initialized
     */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    /**
     * Checks whether the pool is open.
     *
     * <p>This is a flag-only check that does not perform any network
     * activity. It is safe to call from the main thread.</p>
     *
     * @return {@code true} if the pool has been created and is open
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Checks whether the manager is ready to serve queries.
     *
     * <p>This is a cheap flag-only check and is safe to call from the
     * main thread. It does not verify that the underlying MySQL server
     * is actually reachable; use {@link #ping()} for that.</p>
     *
     * @return {@code true} if the pool has been initialized
     */
    public boolean isReady() {
        return ready && dataSource != null && !dataSource.isClosed();
    }

    /**
     * Verifies that a connection can actually be obtained from the pool.
     *
     * <p>This method performs a real round-trip to the database and
     * must only be invoked from async threads, since it can block for
     * up to the configured connection timeout.</p>
     *
     * @return {@code true} if a valid connection is available
     */
    public boolean ping() {
        if (!isReady()) return false;
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Pushes an operation to the retry queue.
     *
     * <p>The operation is executed later by the retry task on an async
     * thread, with a live connection obtained from the pool. Callers
     * are responsible for keeping the closure free of any state that
     * could become stale between the failure and the retry.</p>
     *
     * <p>This method is thread-safe.</p>
     *
     * @param op operation to retry later
     */
    public void queue(Consumer<Connection> op) {
        retryQueue.add(op);
    }

    /**
     * Returns the number of operations currently waiting in the retry
     * queue.
     *
     * @return pending retry count
     */
    public int retryQueueSize() {
        return retryQueue.size();
    }

    /**
     * Closes the connection pool.
     *
     * <p>Any pending retry operation is discarded, as the plugin is
     * shutting down and the queue will not be processed.</p>
     */
    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}