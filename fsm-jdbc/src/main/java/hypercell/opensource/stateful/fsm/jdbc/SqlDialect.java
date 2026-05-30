package hypercell.opensource.stateful.fsm.jdbc;

import java.util.List;

/**
 * Database-specific SQL for {@link JdbcSnapshotRepository}.
 * <p>
 * Only the upsert statement and DDL type names vary between databases; every other
 * operation (SELECT, DELETE, listPendingRetries) uses standard SQL shared across all dialects.
 * <p>
 * Built-in implementations:
 * <ul>
 *   <li>{@link hypercell.opensource.stateful.fsm.jdbc.dialect.PostgreSqlDialect}</li>
 *   <li>{@link hypercell.opensource.stateful.fsm.jdbc.dialect.MySqlDialect}</li>
 *   <li>{@link hypercell.opensource.stateful.fsm.jdbc.dialect.H2Dialect}</li>
 *   <li>{@link hypercell.opensource.stateful.fsm.jdbc.dialect.SqliteDialect}</li>
 *   <li>{@link hypercell.opensource.stateful.fsm.jdbc.dialect.OracleDialect}</li>
 * </ul>
 * Implement this interface to support any other SQL database.
 */
public interface SqlDialect {

    /**
     * Return the upsert SQL for the given table name.
     * <p>
     * The statement must accept exactly 13 positional parameters in this order:
     * <ol>
     *   <li>execution_id</li>
     *   <li>machine_definition_id</li>
     *   <li>current_state_name</li>
     *   <li>failed_state_name</li>
     *   <li>failed_sub_step_name</li>
     *   <li>last_trigger_event</li>
     *   <li>attempt_number</li>
     *   <li>last_failed_at (ISO-8601 string or null)</li>
     *   <li>scheduled_retry_at (ISO-8601 string or null)</li>
     *   <li>last_error_message</li>
     *   <li>status</li>
     *   <li>captured_at (ISO-8601 string)</li>
     *   <li>completed_steps (serialized text)</li>
     * </ol>
     * The {@code version} column is managed internally — the insert sets it to {@code 1}
     * and the update increments it by {@code 1} without requiring a caller-supplied value.
     *
     * @param tableName the table name, e.g. {@code "fsm_snapshots"}
     * @return the fully formed upsert SQL string
     */
    String upsertSql(String tableName);

    /**
     * Return the DDL statements needed to create the snapshot table and its index.
     * <p>
     * Called once by {@link JdbcSnapshotRepository} at construction time if the table
     * does not yet exist. The default implementation uses standard SQL types compatible
     * with PostgreSQL, MySQL, MariaDB, H2, and SQLite. Override this method for
     * databases that require different type names (e.g. Oracle — see
     * {@link hypercell.opensource.stateful.fsm.jdbc.dialect.OracleDialect}).
     *
     * @param tableName the table name to create
     * @return ordered list of DDL statements to execute; each is executed separately
     */
    default List<String> ddlStatements(String tableName) {
        return List.of(
                "CREATE TABLE " + tableName + " ("
                        + "    execution_id          VARCHAR(255) NOT NULL,"
                        + "    machine_definition_id VARCHAR(255) NOT NULL,"
                        + "    current_state_name    VARCHAR(255),"
                        + "    failed_state_name     VARCHAR(255),"
                        + "    failed_sub_step_name  VARCHAR(255),"
                        + "    last_trigger_event    VARCHAR(255),"
                        + "    attempt_number        INT          NOT NULL DEFAULT 1,"
                        + "    last_failed_at        VARCHAR(50),"
                        + "    scheduled_retry_at    VARCHAR(50),"
                        + "    captured_at           VARCHAR(50)  NOT NULL,"
                        + "    last_error_message    TEXT,"
                        + "    status                VARCHAR(50)  NOT NULL,"
                        + "    completed_steps       TEXT,"
                        + "    version               BIGINT       NOT NULL DEFAULT 1,"
                        + "    CONSTRAINT pk_" + tableName + " PRIMARY KEY (execution_id)"
                        + ")",
                "CREATE INDEX idx_" + tableName + "_status ON " + tableName + " (status)"
        );
    }
}
