package com.embabel.urbot;

import com.vaadin.flow.spring.SpringBootAutoConfiguration;
import com.vaadin.flow.spring.SpringSecurityAutoConfiguration;
import com.vaadin.flow.spring.VaadinScopesConfig;
import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivinePropertiesConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import static org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE;
import static org.springframework.context.annotation.FilterType.REGEX;

/**
 * Test boot class that mirrors {@link UrbotApplication} but excludes Vaadin, Hilla,
 * and security packages. Uses individual annotations instead of
 * {@code @SpringBootApplication} to avoid a double component scan that would
 * re-discover the excluded classes.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(
        exclude = {
                SpringBootAutoConfiguration.class,
                SpringSecurityAutoConfiguration.class,
                VaadinScopesConfig.class,
        },
        excludeName = {
                "com.vaadin.hilla.EndpointController",
                "com.vaadin.hilla.push.PushConfigurer",
                "com.vaadin.hilla.ApplicationContextProvider",
                "com.vaadin.hilla.startup.EndpointRegistryInitializer",
                "com.vaadin.hilla.startup.RouteUnifyingServiceInitListener",
                "com.vaadin.hilla.route.RouteUtil",
                "com.vaadin.hilla.route.RouteUnifyingConfiguration",
                "com.vaadin.hilla.signals.config.SignalsConfiguration"
        }
)
@EnableDrivine
@EnableDrivinePropertiesConfig
@org.springframework.context.annotation.Import(BotPackageScanConfiguration.class)
@ComponentScan(
        basePackages = "com.embabel.urbot",
        excludeFilters = {
                @ComponentScan.Filter(type = ASSIGNABLE_TYPE, classes = UrbotApplication.class),
                @ComponentScan.Filter(type = REGEX, pattern = "com\\.embabel\\.urbot\\.vaadin\\..*"),
                @ComponentScan.Filter(type = REGEX, pattern = "com\\.embabel\\.urbot\\.security\\..*")
        }
)
class TestUrbotApplication {
}
