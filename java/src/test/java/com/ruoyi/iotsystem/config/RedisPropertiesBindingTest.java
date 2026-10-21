package com.ruoyi.iotsystem.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisPropertiesBindingTest {

    // 从已提交的配置文件加载隔离环境，避免继承本机系统变量或敏感配置。
    private ConfigurableEnvironment environment(String profileResource, Map<String, Object> values)
            throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(new MapPropertySource("testValues", values));
        if (profileResource != null) {
            environment.getPropertySources().addLast(new PropertiesPropertySource("profile",
                    PropertiesLoaderUtils.loadProperties(new ClassPathResource(profileResource))));
        }
        environment.getPropertySources().addLast(new PropertiesPropertySource("defaults",
                PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))));
        return environment;
    }

    // 按当前 Boot 版本绑定实际配置文件中的 Redis 属性。
    private RedisProperties bind(ConfigurableEnvironment environment) {
        return Binder.get(environment).bind("spring.redis", Bindable.of(RedisProperties.class)).get();
    }

    // 验证默认客户端与连接池参数绑定到 starter 实际使用的 Lettuce。
    @Test
    void defaultsBindLettuceConnectionAndPool() throws IOException {
        RedisProperties properties = bind(environment(null, new HashMap<>()));

        assertEquals("localhost", properties.getHost());
        assertEquals(6379, properties.getPort());
        assertEquals(Duration.ofSeconds(2), properties.getTimeout());
        assertEquals(RedisProperties.ClientType.LETTUCE, properties.getClientType());
        RedisProperties.Pool pool = properties.getLettuce().getPool();
        assertNotNull(pool);
        assertEquals(8, pool.getMaxActive());
        assertEquals(8, pool.getMaxIdle());
        assertEquals(0, pool.getMinIdle());
        assertEquals(Duration.ofMillis(-1), pool.getMaxWait());
    }

    // 验证生产环境占位符绑定非默认主机、端口、密码和超时，防止静默回退。
    @Test
    void productionBindsEnvironmentConnectionValues() throws IOException {
        Map<String, Object> values = new HashMap<>();
        values.put("REDIS_HOST", "redis.test.invalid");
        values.put("REDIS_PORT", "16380");
        values.put("REDIS_PASSWORD", "non-secret-test-value");
        RedisProperties properties = bind(environment("application-prod.properties", values));

        assertEquals("redis.test.invalid", properties.getHost());
        assertEquals(16380, properties.getPort());
        assertEquals("non-secret-test-value", properties.getPassword());
        assertEquals(Duration.ofSeconds(2), properties.getTimeout());
    }

    // 验证测试环境保持不可用端口且仓库开关仍使用 Spring Data 的独立属性前缀。
    @Test
    void testProfileBindsIsolatedConnectionAndDisablesRepositories() throws IOException {
        ConfigurableEnvironment environment = environment("application-test.properties", new HashMap<>());
        RedisProperties properties = bind(environment);

        assertEquals("127.0.0.1", properties.getHost());
        assertEquals(1, properties.getPort());
        assertFalse(environment.getProperty("spring.data.redis.repositories.enabled", Boolean.class));
    }

    // 仅创建实际连接工厂并核查配置，不获取连接或访问 Redis 网络服务。
    @Test
    void autoConfigurationUsesLettuceWithConfiguredEndpoint() throws IOException {
        ConfigurableEnvironment environment = environment(null, new HashMap<>());
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withInitializer(context -> {
                    context.getEnvironment().getPropertySources().remove(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    context.getEnvironment().getPropertySources().remove(
                            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    context.getEnvironment().merge(environment);
                })
                .withPropertyValues("spring.redis.host=redis.test.invalid", "spring.redis.port=16380")
                .run(context -> {
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    assertEquals("redis.test.invalid", factory.getHostName());
                    assertEquals(16380, factory.getPort());
                    assertTrue(factory.getClientConfiguration() instanceof LettucePoolingClientConfiguration);
                    LettucePoolingClientConfiguration configuration =
                            (LettucePoolingClientConfiguration) factory.getClientConfiguration();
                    assertEquals(8, configuration.getPoolConfig().getMaxTotal());
                });
    }
}
