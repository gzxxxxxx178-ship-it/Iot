package com.ruoyi.iotsystem.config;

import org.flywaydb.core.internal.database.DatabaseTypeRegister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DevMigrationConfigurationTest {
    @TempDir Path directory;

    // 模拟只有凭据的旧dev properties，使用真实Boot配置加载器合并已提交的YAML默认值。
    @Test
    void legacyDevCredentialsStillEnableMigrationBeforeSchemaValidation() throws Exception {
        for (String name : new String[]{"application.properties", "application-dev.yml"}) {
            try (java.io.InputStream input = new ClassPathResource(name).getInputStream()) {
                Files.copy(input, directory.resolve(name));
            }
        }
        Files.write(directory.resolve("application-dev.properties"),
                "spring.datasource.username=non-secret-fixture\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=dev", "spring.config.location=" + directory.toUri())
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    FlywayProperties properties = Binder.get(context.getEnvironment())
                            .bind("spring.flyway", Bindable.of(FlywayProperties.class)).get();
                    assertTrue(properties.isEnabled());
                    assertFalse(properties.isBaselineOnMigrate());
                    assertTrue(properties.isValidateOnMigrate());
                    assertTrue(properties.isCleanDisabled());
                    assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
                    assertEquals("non-secret-fixture", context.getEnvironment().getProperty("spring.datasource.username"));
                });
    }

    // 通过Flyway实际数据库类型注册表确认MySQL插件可加载，不创建任何数据库连接。
    @Test
    void flywayRecognizesMysqlJdbcUrlWithoutConnecting() {
        assertEquals("MySQL", DatabaseTypeRegister.getDatabaseTypeForUrl("jdbc:mysql://127.0.0.1:1/fixture").getName());
    }
}
