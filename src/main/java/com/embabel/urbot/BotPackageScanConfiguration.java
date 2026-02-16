package com.embabel.urbot;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Scans additional packages for bot components based on {@code urbot.bot-packages}.
 * Used as an {@link ImportBeanDefinitionRegistrar} so that scanning happens during
 * {@code ConfigurationClassPostProcessor} processing, ensuring {@code @Bean} methods
 * in discovered {@code @Configuration} classes are fully processed.
 */
class BotPackageScanConfiguration implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private static final Logger logger = LoggerFactory.getLogger(BotPackageScanConfiguration.class);

    private Environment environment;

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(@NonNull AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        var packages = environment.getProperty("urbot.bot-packages", String[].class);
        if (packages == null || packages.length == 0) {
            return;
        }
        var nonEmpty = java.util.Arrays.stream(packages)
                .filter(p -> !p.isBlank())
                .toArray(String[]::new);
        if (nonEmpty.length == 0) {
            return;
        }
        var scanner = new ClassPathBeanDefinitionScanner(registry);
        int count = scanner.scan(nonEmpty);
        logger.info("Scanned {} bot bean definitions from packages: {}", count, String.join(", ", nonEmpty));
    }
}
