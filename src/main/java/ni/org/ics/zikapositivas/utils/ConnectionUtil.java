package ni.org.ics.zikapositivas.utils;

import com.mysql.cj.jdbc.MysqlDataSource;

import java.sql.Connection;

public class ConnectionUtil {

    public static Connection getConnection() throws Exception {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUser("zikapos");
        dataSource.setPassword("jeKAQudi");
        dataSource.setServerName("localhost");
        dataSource.setPort(3306);
        dataSource.setDatabaseName("zika_positivas");
        dataSource.setUseSSL(false);
        dataSource.setCharacterEncoding("UTF-8");
        dataSource.setServerTimezone("UTC");

        return dataSource.getConnection();
    }
}

