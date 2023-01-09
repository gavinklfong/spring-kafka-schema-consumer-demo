package space.gavinklfong.demo.insurance.messaging;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import space.gavinklfong.demo.insurance.dto.ClaimRequest;
import space.gavinklfong.demo.insurance.schema.InsuranceClaim;
import space.gavinklfong.demo.insurance.schema.InsuranceClaimKey;
import space.gavinklfong.demo.insurance.service.ClaimReviewService;

import static space.gavinklfong.demo.insurance.config.KafkaConfig.CLAIM_SUBMITTED_TOPIC;

@Slf4j
@Component
public class ClaimReqEventListener {
    @Autowired
    private ClaimReviewService claimReviewService;

    @KafkaListener(id = "new-claim-handler", topics = CLAIM_SUBMITTED_TOPIC)
    public void handleClaimRequestEvent(ConsumerRecord<InsuranceClaimKey, InsuranceClaim> data)  {
        log.info("Claim request received: key={}, value={}", data.key(), data.value());
        claimReviewService.processClaimRequest(convertToClaimRequest(data));
    }

    private ClaimRequest convertToClaimRequest(ConsumerRecord<InsuranceClaimKey, InsuranceClaim> data) {
        return ClaimRequest.builder()
                .claimAmount(data.value().getClaimAmount())
                .id(data.key().getClaimId())
                .customerId(data.key().getCustomerId())
                .priority(resolvePriority(data.value().getPriority()))
                .product(resolveProduct(data.value().getProduct()))
                .build();
    }

    private space.gavinklfong.demo.insurance.dto.Priority resolvePriority(space.gavinklfong.demo.insurance.schema.Priority priority) {
        return space.gavinklfong.demo.insurance.dto.Priority.valueOf(priority.name());
    }

    private space.gavinklfong.demo.insurance.dto.Product resolveProduct(space.gavinklfong.demo.insurance.schema.Product product) {
        return space.gavinklfong.demo.insurance.dto.Product.valueOf(product.name());
    }
}

