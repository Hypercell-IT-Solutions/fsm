package io.hypercell.fsm.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for FSM JDBC repository autoconfiguration.
 * <p>
 * EXAMPLE application.yml:
 * <pre>{@code
 * fsm:
 *   jdbc:
 *     enabled: true              # enable/disable autoconfiguration
 *     dialect: postgresql         # postgresql, mysql, h2, sqlite, oracle
 *     table-name: fsm_snapshots   # custom table name (optional)
 * }</pre>
 * <p>
 * OPTIONALITY: This starter is OPTIONAL. You can use FSM without it:
 * <ul>
 *   <li>Use {@code StateMachine.inMemoryRepository()} directly — no beans needed</li>
 *   <li>Use {@code StateMachine.fileRepository(Path)} directly — no beans needed</li>
 *   <li>Define your own {@code @Bean SnapshotRepository} — autoconfiguration will skip</li>
 *   <li>Set {@code fsm.jdbc.enabled: false} — disables JDBC autoconfiguration</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "fsm.jdbc")
public class FsmJdbcProperties {

    /**
     * Enable or disable JDBC autoconfiguration. Defaults to true.
     */
    private boolean enabled = true;

    /**
     * SQL dialect: PostgreSQL, mysql, h2, sqlite, or oracle. Defaults to PostgreSQL.
     */
    private String dialect = "PostgreSQL";

    /**
     * Custom table name for snapshots. Defaults to fsm_snapshots.
     */
    private String tableName = "fsm_snapshots";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
}
