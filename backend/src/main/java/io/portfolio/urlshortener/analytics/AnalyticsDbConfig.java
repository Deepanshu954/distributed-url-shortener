package io.portfolio.urlshortener.analytics;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(AnalyticsDbProperties.class)
@EnableJpaRepositories(
        basePackages = "io.portfolio.urlshortener.analytics",
        entityManagerFactoryRef = "analyticsEntityManagerFactory",
        transactionManagerRef = "analyticsTransactionManager")
public class AnalyticsDbConfig {

    static final String MIGRATION_LOCATION = "classpath:db/migration/analytics";
    static final long CONNECTION_TIMEOUT_MS = 1000;

    private static final Logger log = LoggerFactory.getLogger(AnalyticsDbConfig.class);

    @Bean(destroyMethod = "close")
    public HikariDataSource analyticsDataSource(AnalyticsDbProperties properties) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("analytics-db");
        ds.setJdbcUrl(properties.jdbcUrl());
        ds.setUsername(properties.username());
        ds.setPassword(properties.password());
        ds.setMaximumPoolSize(properties.poolSize());
        ds.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        return ds;
    }

    @Bean
    public Flyway analyticsFlyway(@org.springframework.beans.factory.annotation.Qualifier("analyticsDataSource") DataSource analyticsDataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(analyticsDataSource)
                .locations(MIGRATION_LOCATION)
                .validateOnMigrate(false)
                .load();
        log.info("running analytics-db migrations");
        flyway.migrate();
        return flyway;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean analyticsEntityManagerFactory(
            @org.springframework.beans.factory.annotation.Qualifier("analyticsDataSource") DataSource analyticsDataSource,
            @org.springframework.beans.factory.annotation.Qualifier("analyticsFlyway") Flyway analyticsFlyway) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(analyticsDataSource);
        emf.setPackagesToScan("io.portfolio.urlshortener.analytics");
        emf.setPersistenceUnitName("analytics");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(false);
        emf.setJpaVendorAdapter(adapter);

        emf.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "none",
                "jakarta.persistence.query.timeout", "1000"
        ));
        return emf;
    }

    @Bean(name = {"analyticsTransactionManager", "controlTransactionManager"})
    public PlatformTransactionManager analyticsTransactionManager(
            @org.springframework.beans.factory.annotation.Qualifier("analyticsEntityManagerFactory") EntityManagerFactory analyticsEntityManagerFactory) {
        return new JpaTransactionManager(analyticsEntityManagerFactory);
    }
}
