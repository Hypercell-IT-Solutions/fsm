package hypercell.opensource.stateful.fsm.jdbc;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates unique in-memory H2 databases per test to ensure isolation.
 */
class H2DataSourceHelper {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    static DataSource newInMemoryDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:fsm_test_" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }
}