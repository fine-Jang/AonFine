package com.aonfine.ada;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

/** H2 local logic/DDL tests; only the CUBRID timestamp keyword is translated. NOT CUBRID certification. */
public final class LocalDatabase {
    private LocalDatabase() { }
    public static DataSource create() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        DataSource translated = (DataSource) Proxy.newProxyInstance(LocalDatabase.class.getClassLoader(),
                new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
                    Object value = invoke(source, method, args);
                    if (value instanceof Connection) {
                        Connection connection = (Connection) value;
                        return Proxy.newProxyInstance(LocalDatabase.class.getClassLoader(), new Class<?>[] { Connection.class },
                                (p, m, a) -> {
                                    if (m.getName().equals("prepareStatement")) a[0] = translate((String) a[0]);
                                    return invoke(connection, m, a);
                                });
                    }
                    return value;
                });
        run(translated, "sql/ada-schema-cubrid.sql");
        return translated;
    }
    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); } catch (InvocationTargetException e) { throw e.getCause(); }
    }
    public static String translate(String sql) { return sql.replace("CURRENT_DATETIME", "CURRENT_TIMESTAMP"); }
    public static void run(DataSource ds, String path) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).replaceAll("(?m)--.*$", "");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (String statement : sql.split(";")) if (!statement.trim().isEmpty()) s.execute(translate(statement));
        }
    }
    public static void inject(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException e) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
