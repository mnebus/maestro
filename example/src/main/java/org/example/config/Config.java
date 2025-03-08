package org.example.config;

import jakarta.annotation.PostConstruct;
import lucidity.maestro.engine.MaestroService;
import lucidity.maestro.engine.api.Maestro;
import lucidity.maestro.engine.api.activity.ActivityOptions;
import lucidity.maestro.engine.internal.MaestroImpl;
import org.example.activity.impl.InventoryActivityImpl;
import org.example.activity.impl.NotificationActivityImpl;
import org.example.activity.impl.PaymentActivityImpl;
import org.example.service.EmailService;
import org.example.workflow.OrderWorkflowImpl;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class Config {

    private final EmailService emailService;

    public Config(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostConstruct
    public void init() {

        Maestro maestro = MaestroService.builder()
                .configureDataSource(System.getenv("MAESTRO_DB_USERNAME"), System.getenv("MAESTRO_DB_PASSWORD"), System.getenv("MAESTRO_DB_URL"))
                .build();

        maestro.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);

        maestro.registerActivity(new InventoryActivityImpl());
        maestro.registerActivity(new NotificationActivityImpl(emailService));
        maestro.registerActivity(
                new PaymentActivityImpl(),
                new ActivityOptions(Duration.ofMinutes(1)) // Activity will be retried if it hasn't completed one minute after starting
        );
    }
}
