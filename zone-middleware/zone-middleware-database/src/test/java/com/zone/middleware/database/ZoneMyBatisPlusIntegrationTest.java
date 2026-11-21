package com.zone.middleware.database;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zone.middleware.database.config.ZoneDataSourceAutoConfiguration;
import com.zone.middleware.database.config.ZoneMyBatisPlusConfiguration;
import com.zone.middleware.database.mapper.ZoneUser;
import com.zone.middleware.database.mapper.ZoneUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ZoneMyBatisPlusIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ZoneDataSourceAutoConfiguration.class,
                    ZoneMyBatisPlusConfiguration.class,
                    com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class));

    private ApplicationContextRunner withMapperPackage() {
        return runner.withPropertyValues(
                "zone.middleware.db.url=jdbc:h2:mem:zonemybatis;DB_CLOSE_DELAY=-1",
                "zone.middleware.db.username=sa",
                "zone.middleware.db.mybatis.mapper-package=com.zone.middleware.database.mapper");
    }

    @Test
    void baseMapperCrudShouldWork() {
        withMapperPackage().run(context -> {
            DataSource dataSource = context.getBean(DataSource.class);
            new JdbcTemplate(dataSource).execute(
                    "CREATE TABLE IF NOT EXISTS zone_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                            + "name VARCHAR(64), age INT)");

            ZoneUserMapper mapper = context.getBean(ZoneUserMapper.class);
            ZoneUser user = new ZoneUser("zone", 3);
            assertEquals(1, mapper.insert(user));
            assertNotNull(user.getId(), "自增主键应回填");

            ZoneUser loaded = mapper.selectById(user.getId());
            assertEquals("zone", loaded.getName());
            assertEquals(3, loaded.getAge());

            loaded.setAge(4);
            assertEquals(1, mapper.updateById(loaded));
            assertEquals(4, mapper.selectById(user.getId()).getAge());

            assertEquals(1, mapper.deleteById(user.getId()));
            assertNull(mapper.selectById(user.getId()));
        });
    }

    @Test
    void paginationShouldBeAssembledByDefault() {
        withMapperPackage().run(context -> {
            assertNotNull(context.getBean(MybatisPlusInterceptor.class));

            DataSource dataSource = context.getBean(DataSource.class);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("DROP TABLE IF EXISTS zone_user");
            jdbc.execute("CREATE TABLE zone_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(64), age INT)");
            for (int i = 1; i <= 5; i++) {
                jdbc.update("INSERT INTO zone_user(name, age) VALUES (?, ?)", "u" + i, 20 + i);
            }

            ZoneUserMapper mapper = context.getBean(ZoneUserMapper.class);
            Page<ZoneUser> page = mapper.selectPage(new Page<>(1, 2), null);
            assertEquals(5, page.getTotal(), "总记录数应为 5");
            assertEquals(2, page.getRecords().size(), "当前页应为 2 条");

            Page<ZoneUser> page2 = mapper.selectPage(new Page<>(2, 2), null);
            assertEquals(2, page2.getRecords().size());
            List<ZoneUser> records = page.getRecords();
            assertEquals("u1", records.get(0).getName());
        });
    }

    @Test
    void paginationCanBeDisabled() {
        withMapperPackage().withPropertyValues(
                        "zone.middleware.db.mybatis.pagination-enabled=false")
                .run(context -> assertEquals(false,
                        context.containsBean("zoneMybatisPlusInterceptor")));
    }
}
