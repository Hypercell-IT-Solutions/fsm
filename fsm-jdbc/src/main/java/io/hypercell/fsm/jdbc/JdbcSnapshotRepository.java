package io.hypercell.fsm.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hypercell.fsm.core.ActionResult;
import io.hypercell.fsm.exception.SnapshotException;
import io.hypercell.fsm.resume.ExecutionSnapshot;
import io.hypercell.fsm.resume.SnapshotRepository;
import io.hypercell.fsm.resume.SnapshotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * A {@link SnapshotRepository} backed by any SQL database via plain JDBC.
 * <p>
 * AUTOMATIC SCHEMA CREATION: The {@code fsm_snapshots} table and index are created
 * automatically on the first instantiation of this repository (if the table does not
 * already exist). No manual DDL step is required — just provide a {@code DataSource}
 * and a {@link SqlDialect} for your database.
 * <p>
 * The dialect's {@link SqlDialect#ddlStatements(String)} method provides the
 * create-table and create-index statements. Built-in dialects use standard SQL
 * types compatible with PostgreSQL, MySQL, MariaDB, H2, and SQLite. For Oracle,
 * use {@link io.hypercell.fsm.jdbc.dialect.OracleDialect}, which
 * automatically creates with Oracle-native types ({@code VARCHAR2}, {@code CLOB},
 * {@code NUMBER}).
 * <p>
 * A custom table name can be supplied via the three-argument constructor.
 * <p>
 * USAGE:
 * <pre>{@code
 * DataSource dataSource = ...; // your connection pool (HikariCP, etc.)
 * // Table is created on first instantiation:
 * SnapshotRepository repo = new JdbcSnapshotRepository(dataSource, new PostgreSqlDialect());
 *
 * StateMachineDefinition<OrderContext> definition = StateMachine.<OrderContext>define("order-workflow")
 *     .snapshotRepository(repo)
 *     ...
 *     .build();
 * }</pre>
 * <p>
 * THREAD SAFETY: thread-safe. Each operation acquires and releases a connection from
 * the pool independently.
 * <p>
 * OPTIMISTIC LOCKING: the {@code version} column is incremented on every save.
 * The upsert is atomic at the database level, preventing lost updates within a
 * single database instance. For cross-replica distributed locking, add a
 * compare-and-swap check in a custom {@link SqlDialect} that includes
 * {@code WHERE version = :expected} and fails if 0 rows are affected.
 * <p>
 * SERIALIZATION: {@code completedSubStepResults} is stored as JSON in the
 * {@code completed_steps} column in a direct key-value format:
 * {@code {subStepName: {status, error?, output}, ...}}. This enables database-native
 * querying. All timestamp fields are stored as ISO-8601 strings to avoid timezone
 * issues across different JDBC drivers.
 */
public class JdbcSnapshotRepository implements SnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcSnapshotRepository.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Default table name used when no explicit name is provided.
     */
    public static final String DEFAULT_TABLE = "fsm_snapshots";

    private static final String SELECT_COLUMNS = """
            execution_id, machine_definition_id, current_state_name, failed_state_name,
            failed_sub_step_name, last_trigger_event, attempt_number, last_failed_at,
            scheduled_retry_at, last_error_message, status, captured_at, completed_steps
            """;

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final String tableName;

    private final String selectSql;
    private final String deleteSql;
    private final String listPendingSql;
    private final String upsertSql;

    /**
     * Create a repository using the default table name {@value #DEFAULT_TABLE}.
     *
     * @param dataSource the connection pool; must be pre-configured and ready
     * @param dialect    the database-specific upsert strategy
     */
    public JdbcSnapshotRepository(DataSource dataSource, SqlDialect dialect) {
        this(dataSource, dialect, DEFAULT_TABLE);
    }

    /**
     * Create a repository with a custom table name.
     *
     * @param dataSource the connection pool
     * @param dialect    the database-specific upsert strategy
     * @param tableName  the table name to use instead of {@value #DEFAULT_TABLE}
     */
    public JdbcSnapshotRepository(DataSource dataSource, SqlDialect dialect, String tableName) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.tableName = tableName;
        this.selectSql = "SELECT " + SELECT_COLUMNS + " FROM " + tableName + " WHERE execution_id = ?";
        this.deleteSql = "DELETE FROM " + tableName + " WHERE execution_id = ?";
        this.listPendingSql = "SELECT " + SELECT_COLUMNS + " FROM " + tableName
                + " WHERE status IN ('FAILED', 'RETRY_SCHEDULED')";
        this.upsertSql = dialect.upsertSql(tableName);
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection()) {
            if (tableExists(conn)) {
                log.debug("[JdbcSnapshotRepository] Table '{}' already exists", tableName);
                return;
            }
        } catch (SQLException e) {
            log.warn("[JdbcSnapshotRepository] Could not check for table existence: {}. Attempting to create anyway.",
                    e.getMessage());
        }

        for (String ddl : dialect.ddlStatements(tableName)) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(ddl);
                log.debug("[JdbcSnapshotRepository] Executed: {}", ddl.substring(0, Math.min(60, ddl.length())) + "...");
            } catch (SQLException e) {
                log.debug("[JdbcSnapshotRepository] DDL statement failed (likely already exists): {}", e.getMessage());
            }
        }
    }

    private boolean tableExists(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(selectSql + " LIMIT 0")) {
            ps.executeQuery();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void save(String executionId, ExecutionSnapshot snapshot) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            bind(ps, snapshot);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new SnapshotException("Failed to save snapshot for '" + executionId + "'", e);
        }
    }

    @Override
    public Optional<ExecutionSnapshot> load(String executionId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(toSnapshot(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new SnapshotException("Failed to load snapshot for '" + executionId + "'", e);
        }
    }

    @Override
    public void delete(String executionId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, executionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[JdbcSnapshotRepository] Could not delete snapshot for '{}': {}",
                    executionId, e.getMessage(), e);
        }
    }

    @Override
    public List<ExecutionSnapshot> listPendingRetries() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(listPendingSql);
             ResultSet rs = ps.executeQuery()) {
            List<ExecutionSnapshot> results = new ArrayList<>();
            while (rs.next()) {
                results.add(toSnapshot(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new SnapshotException("Failed to list pending retries", e);
        }
    }

    private static void bind(PreparedStatement ps, ExecutionSnapshot s) throws SQLException {
        ps.setString(1, s.getExecutionId());
        ps.setString(2, s.getMachineDefinitionId());
        ps.setString(3, s.getCurrentStateName());
        ps.setString(4, s.getFailedStateName());
        ps.setString(5, s.getFailedSubStepName());
        ps.setString(6, s.getLastTriggerEvent());
        ps.setInt(7, s.getAttemptNumber());
        ps.setString(8, s.getLastFailedAt() != null ? s.getLastFailedAt().toString() : null);
        ps.setString(9, s.getScheduledRetryAt() != null ? s.getScheduledRetryAt().toString() : null);
        ps.setString(10, s.getLastErrorMessage());
        ps.setString(11, s.getStatus().name());
        ps.setString(12, s.getCapturedAt().toString());
        ps.setString(13, serializeSteps(s.getCompletedSubStepResults()));
    }

    private static ExecutionSnapshot toSnapshot(ResultSet rs) throws SQLException {
        return new ExecutionSnapshot.Builder()
                .executionId(rs.getString("execution_id"))
                .machineDefinitionId(rs.getString("machine_definition_id"))
                .currentStateName(rs.getString("current_state_name"))
                .failedStateName(rs.getString("failed_state_name"))
                .failedSubStepName(rs.getString("failed_sub_step_name"))
                .lastTriggerEvent(rs.getString("last_trigger_event"))
                .attemptNumber(rs.getInt("attempt_number"))
                .lastFailedAt(parseInstant(rs.getString("last_failed_at")))
                .scheduledRetryAt(parseInstant(rs.getString("scheduled_retry_at")))
                .lastErrorMessage(rs.getString("last_error_message"))
                .status(SnapshotStatus.valueOf(rs.getString("status")))
                .capturedAt(parseInstant(rs.getString("captured_at")))
                .completedSubStepResults(deserializeSteps(rs.getString("completed_steps")))
                .build();
    }

    static String serializeSteps(Map<String, ActionResult> results) {
        if (results.isEmpty()) return "";
        try {
            ObjectNode root = objectMapper.createObjectNode();
            for (Map.Entry<String, ActionResult> entry : results.entrySet()) {
                ObjectNode stepNode = objectMapper.createObjectNode();
                ActionResult ar = entry.getValue();
                stepNode.put("status", ar.getStatus().name());
                if (ar.getErrorMessage() != null) {
                    stepNode.put("error", ar.getErrorMessage());
                }
                ObjectNode outputNode = objectMapper.createObjectNode();
                for (Map.Entry<String, Object> out : ar.getOutput().entrySet()) {
                    outputNode.put(out.getKey(), out.getValue() != null ? out.getValue().toString() : "");
                }
                stepNode.set("output", outputNode);
                root.set(entry.getKey(), stepNode);
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new SnapshotException("Failed to serialize completed steps", e);
        }
    }

    static Map<String, ActionResult> deserializeSteps(String text) {
        if (text == null || text.isBlank()) return Collections.emptyMap();
        try {
            JsonNode root = objectMapper.readTree(text);
            Map<String, ActionResult> result = new LinkedHashMap<>();
            root.fields().forEachRemaining(field -> {
                String subStepName = field.getKey();
                JsonNode stepNode = field.getValue();
                String status = stepNode.get("status").asText();
                String error = stepNode.has("error") ? stepNode.get("error").asText() : null;
                JsonNode outputNode = stepNode.get("output");
                Map<String, Object> output = new LinkedHashMap<>();
                outputNode.fields().forEachRemaining(outField ->
                        output.put(outField.getKey(), outField.getValue().asText())
                );
                ActionResult ar;
                if ("FAILED".equals(status)) {
                    ar = error != null ? ActionResult.failed(error) : ActionResult.failed("unknown");
                } else if ("SKIPPED".equals(status)) {
                    ar = ActionResult.skipped();
                } else {
                    ar = output.isEmpty() ? ActionResult.success() : ActionResult.success(output);
                }
                result.put(subStepName, ar);
            });
            return result;
        } catch (JsonProcessingException e) {
            throw new SnapshotException("Failed to deserialize completed steps", e);
        }
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
