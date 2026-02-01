package tech.icc.filesrv.core.infra.executor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tech.icc.filesrv.core.domain.events.CallbackTaskEvent;
import tech.icc.filesrv.core.infra.executor.CallbackTaskPublisher;

/**
 * Spring Event implementation of callback task publisher.
 * <p>
 * This implementation is active in test environments only, publishing events
 * via Spring's {@link ApplicationEventPublisher} for local asynchronous processing.
 * Production environments use {@link KafkaCallbackTaskPublisher} instead.
 * </p>
 * <p>
 * Events are processed by {@link CallbackTaskEventListener} which executes
 * the callback chain asynchronously in a separate thread pool.
 * </p>
 */
@Component
@Profile("test")
public class SpringEventCallbackPublisher implements CallbackTaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringEventCallbackPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    public SpringEventCallbackPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(String taskId) {
        CallbackTaskEvent event = CallbackTaskEvent.of(taskId);

        log.info("Publishing callback task event: taskId={}, messageId={}",
                taskId, event.messageId());

        eventPublisher.publishEvent(event);

        log.debug("Callback task event published: taskId={}, messageId={}",
                taskId, event.messageId());
    }
}
