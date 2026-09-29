package io.portfolio.urlshortener;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EntityScan(basePackages = "io.portfolio.urlshortener.shortener")
@EnableJpaRepositories(basePackages = "io.portfolio.urlshortener.shortener")
public class ShardJpaConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.sharding", name = "enabled", havingValue = "false", matchIfMissing = true)
    public DataSource shardDataSource(
            @org.springframework.beans.factory.annotation.Value("${spring.datasource.url:jdbc:h2:mem:shortener;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=KEY}") String url,
            @org.springframework.beans.factory.annotation.Value("${spring.datasource.username:sa}") String username,
            @org.springframework.beans.factory.annotation.Value("${spring.datasource.password:}") String password) {
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setPoolName("shard-primary-db");
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.sharding", name = "enabled", havingValue = "false", matchIfMissing = true)
    public Flyway defaultShardFlyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/shard")
                .validateOnMigrate(false)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            @org.springframework.beans.factory.annotation.Qualifier("defaultShardFlyway") ObjectProvider<Flyway> flywayProvider) {
        // Run migration if present before entity manager factory binds
        flywayProvider.ifAvailable(Flyway::migrate);

        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("io.portfolio.urlshortener.shortener");
        emf.setPersistenceUnitName("shard");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(false);
        emf.setJpaVendorAdapter(adapter);

        emf.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "none",
                "jakarta.persistence.query.timeout", "1000"
        ));
        return emf;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
