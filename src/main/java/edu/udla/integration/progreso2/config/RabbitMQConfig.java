package edu.udla.integration.progreso2.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    /*
    * Se implementa configuracion de RabbitMQ para que se creen automaticamente
    * las colas, exchange y bindings al arrancar la aplicacion.
    * */

    // Point-to-Point: cola de facturación
    @Bean
    public Queue billingQueue() {
        return new Queue("billing.queue", true);
    }

    // Publish/Subscribe: exchange tipo fanout
    @Bean
    public FanoutExchange appointmentsExchange() {
        return new FanoutExchange("appointments.events");
    }

    // Colas suscriptoras al exchange
    @Bean
    public Queue notificationsQueue() {
        return new Queue("notifications.queue", true);
    }

    @Bean
    public Queue analyticsQueue() {
        return new Queue("analytics.queue", true);
    }

    // Binding: conectar las colas al exchange
    @Bean
    public Binding notificationsBinding() {
        return BindingBuilder.bind(notificationsQueue()).to(appointmentsExchange());
    }

    @Bean
    public Binding analyticsBinding() {
        return BindingBuilder.bind(analyticsQueue()).to(appointmentsExchange());
    }
}