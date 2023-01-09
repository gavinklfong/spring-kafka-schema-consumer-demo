package space.gavinklfong.demo.insurance.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import space.gavinklfong.demo.insurance.schema.InsuranceClaim;
import space.gavinklfong.demo.insurance.schema.InsuranceClaimKey;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig {
    public static final String CLAIM_SUBMITTED_TOPIC = "claim-submitted";

    @Bean
    public NewTopic claimSubmittedTopic() {
        return TopicBuilder.name(CLAIM_SUBMITTED_TOPIC).build();
    }


}
