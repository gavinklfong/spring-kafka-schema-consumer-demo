package space.gavinklfong.demo.insurance.messaging;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import space.gavinklfong.demo.insurance.InsuranceClaimConsumerApplication;
import space.gavinklfong.demo.insurance.config.KafkaConfig;
import space.gavinklfong.demo.insurance.dto.ClaimRequest;
import space.gavinklfong.demo.insurance.dto.Priority;
import space.gavinklfong.demo.insurance.dto.Product;
import space.gavinklfong.demo.insurance.model.ClaimReviewResult;
import space.gavinklfong.demo.insurance.model.Status;
import space.gavinklfong.demo.insurance.schema.InsuranceClaim;
import space.gavinklfong.demo.insurance.schema.InsuranceClaimKey;
import space.gavinklfong.demo.insurance.schema.Metadata;
import space.gavinklfong.demo.insurance.service.ClaimReviewService;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@Slf4j
@SpringBootTest(classes = {ClaimReqEventListener.class})
@ContextConfiguration(classes = {KafkaConfig.class, KafkaAutoConfiguration.class})
@Testcontainers
class ClaimReqEventListenerIT {

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @MockBean
    ClaimReviewService claimReviewService;
    KafkaProducer<InsuranceClaimKey, InsuranceClaim> kafkaProducer;
    KafkaConsumer<String, ClaimReviewResult> kafkaConsumer;

    @BeforeEach
    void setup() {
        kafkaProducer = createKafkaProducer();
        kafkaConsumer = createKafkaConsumer();
        kafkaConsumer.subscribe(Collections.singletonList("claim-updated"));
    }

    private KafkaProducer<InsuranceClaimKey, InsuranceClaim> createKafkaProducer() {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put("schema.registry.url", "http://localhost:8081");
        return new KafkaProducer<>(props);
    }

    private KafkaConsumer<String, ClaimReviewResult> createKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-test-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, InsuranceClaimConsumerApplication.class.getPackage().getName());
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ClaimReviewResult.class);
        return new KafkaConsumer<>(props);
    }


    @Test
    void whenClaimReviewResultSent_thenListenerIsInvoked() throws ExecutionException, InterruptedException {

        when(claimReviewService.processClaimRequest((any(ClaimRequest.class)))).thenAnswer(invocation -> {
            ClaimRequest request = invocation.getArgument(0);
            return ClaimReviewResult.builder()
                    .claimId(request.getId())
                    .customerId(request.getCustomerId())
                    .status(Status.APPROVED)
                    .build();
        });

        InsuranceClaimKey key = generateAvroClaimRequestKey();
        InsuranceClaim value = generateAvroClaimRequest();
        ProducerRecord<InsuranceClaimKey, InsuranceClaim> producerRecord = new ProducerRecord<>("claim-submitted",
                key, value);
        kafkaProducer.send(producerRecord).get();
        log.info("claim request sent");

        await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> {
            ConsumerRecords<String, ClaimReviewResult> records = kafkaConsumer.poll(Duration.ofSeconds(2));
            records.forEach(record -> {
                log.info("received message: {}", record);
                assertThat(record.key()).isEqualTo(key.getCustomerId());
            });
            assertThat(records.count()).isEqualTo(1);
        });
    }

    private InsuranceClaim generateAvroClaimRequest() {
        Metadata metadata = Metadata.newBuilder()
                .setCorrelationId(UUID.randomUUID().toString())
                .setTimestamp(Instant.now())
                .build();

        return InsuranceClaim.newBuilder()
                .setClaimAmount(RandomUtils.nextDouble(200, 7000))
                .setPriority(space.gavinklfong.demo.insurance.schema.Priority.HIGH)
                .setProduct(space.gavinklfong.demo.insurance.schema.Product.MEDICAL)
                .setMetadata(metadata)
                .build();
    }

    private InsuranceClaimKey generateAvroClaimRequestKey() {
        return InsuranceClaimKey.newBuilder()
                .setClaimId(UUID.randomUUID().toString())
                .setCustomerId(UUID.randomUUID().toString())
                .build();
    }

}
