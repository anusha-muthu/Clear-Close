package com.clearclose.exit.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.clearclose.exit.events.LoanExitCompletedEvent;
import com.clearclose.exit.events.PaymentEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
public class KafkaEventConfig {
    @Bean
    public DefaultKafkaProducerFactory<String, Object> eventProducerFactory(KafkaProperties properties) {
        // Malformed JSON is recovered as original bytes, rather than JSON/base64 encoded bytes.
        Map<Class<?>, Serializer<?>> serializers = new LinkedHashMap<>();
        serializers.put(byte[].class, new ByteArraySerializer());
        var json = new JacksonJsonSerializer<Object>();
        serializers.put(PaymentEvent.class, json);
        serializers.put(LoanExitCompletedEvent.class, json);
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(),
            new StringSerializer(), new DelegatingByTypeSerializer(serializers));
    }

    @Bean
    public KafkaTemplate<String, Object> eventKafkaTemplate(
            DefaultKafkaProducerFactory<String, Object> eventProducerFactory) {
        return new KafkaTemplate<>(eventProducerFactory);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> paymentKafkaListenerContainerFactory(
            KafkaProperties properties, KafkaTemplate<String, Object> template,
            @Value("${clearclose.events.payment-dlt}") String dlt) {
        // Fixed target type; do not trust Java class names supplied in message headers.
        var json = new JacksonJsonDeserializer<>(PaymentEvent.class, false);
        var consumerFactory = new DefaultKafkaConsumerFactory<>(properties.buildConsumerProperties(),
            new StringDeserializer(), new ErrorHandlingDeserializer<>(json));
        var factory = new ConcurrentKafkaListenerContainerFactory<String, PaymentEvent>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setSyncCommits(true);
        factory.setMissingTopicsFatal(false);

        var recoverer = new DeadLetterPublishingRecoverer(template,
            (record, error) -> new TopicPartition(dlt, record.partition()));
        // Never acknowledge a failed record unless its DLT send is confirmed.
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(20));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 2L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(handler);
        return factory;
    }

    @Bean
    public NewTopic paymentSucceededTopic(@Value("${clearclose.events.payment-topic}") String topic,
            @Value("${clearclose.events.partitions}") int partitions,
            @Value("${clearclose.events.replicas}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic loanExitCompletedTopic(@Value("${clearclose.events.completion-topic}") String topic,
            @Value("${clearclose.events.partitions}") int partitions,
            @Value("${clearclose.events.replicas}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic paymentDeadLetterTopic(@Value("${clearclose.events.payment-dlt}") String topic,
            @Value("${clearclose.events.partitions}") int partitions,
            @Value("${clearclose.events.replicas}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }
}
