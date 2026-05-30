package hypercell.opensource.stateful.fsm.spring;

import hypercell.opensource.stateful.fsm.jdbc.JdbcSnapshotRepository;
import hypercell.opensource.stateful.fsm.jdbc.SqlDialect;
import hypercell.opensource.stateful.fsm.jdbc.dialect.*;
import hypercell.opensource.stateful.fsm.resume.SnapshotRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Spring Boot autoconfiguration for FSM JDBC repository.
 * <p>
 * ENABLED BY DEFAULT. To disable:
 * <pre>{@code
 * fsm.jdbc.enabled: false
 * }</pre>
 * <p>
 * CUSTOMIZATION:
 * <pre>{@code
 * fsm.jdbc.dialect: postgresql|mysql|h2|sqlite|oracle
 * fsm.jdbc.table-name: my_custom_table
 * }</pre>
 * <p>
 * OPTIONALITY: If you don't add this starter:
 * - Define your own {@code @Bean SnapshotRepository} if needed
 * - Or use {@code StateMachine.inMemoryRepository()}/{@code StateMachine.fileRepository()} directly
 * <p>
 * PRECEDENCE: If a {@code SnapshotRepository} bean already exists (user-defined),
 * this autoconfiguration will not create one ({@code @ConditionalOnMissingBean}).
 */
@AutoConfiguration
@ConditionalOnClass(JdbcSnapshotRepository.class)
@ConditionalOnProperty(
        name = "fsm.jdbc.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(FsmJdbcProperties.class)
public class FsmJdbcAutoConfiguration {

    /**
     * Creates a {@link JdbcSnapshotRepository} bean if one doesn't already exist.
     * <p>
     * Uses the configured {@link FsmJdbcProperties} to select the SQL dialect and
     * table name. The repository is initialized with an autoconfigured DataSource
     * from Spring Boot's default configuration.
     *
     * @param dataSource Spring Boot autoconfigured DataSource
     * @param properties User configuration from application.yml
     * @return a new JdbcSnapshotRepository, or skips if a bean already exists
     */
    @Bean
    @ConditionalOnMissingBean(SnapshotRepository.class)
    public SnapshotRepository snapshotRepository(DataSource dataSource, FsmJdbcProperties properties) {
        SqlDialect dialect = selectDialect(properties.getDialect());
        return new JdbcSnapshotRepository(dataSource, dialect, properties.getTableName());
    }

    /**
     * Select the appropriate {@link SqlDialect} based on configuration.
     *
     * @param dialectName one of: PostgreSQL (default), mysql, h2, sqlite, oracle
     * @return the corresponding dialect implementation
     * @throws IllegalArgumentException if the dialect is not recognized
     */
    private static SqlDialect selectDialect(String dialectName) {
        return switch (dialectName.toLowerCase()) {
            case "postgresql", "postgres" -> new PostgreSqlDialect();
            case "mysql", "mariadb" -> new MySqlDialect();
            case "h2" -> new H2Dialect();
            case "sqlite" -> new SqliteDialect();
            case "oracle" -> new OracleDialect();
            default -> throw new IllegalArgumentException(
                    "Unknown FSM JDBC dialect: '" + dialectName + "'. " +
                            "Supported: postgresql, mysql, h2, sqlite, oracle");
        };
    }
}
