/**
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 */

package com.dell.cpsd.paqx.fru.amqp.consumer.handler;

import com.dell.cpsd.common.rabbitmq.consumer.error.ErrorTransformer;
import com.dell.cpsd.common.rabbitmq.consumer.handler.DefaultMessageHandler;
import com.dell.cpsd.common.rabbitmq.message.HasMessageProperties;
import com.dell.cpsd.common.rabbitmq.validators.DefaultMessageValidator;
import com.dell.cpsd.paqx.fru.rest.dto.VCenterHostPowerOperationStatus;
import com.dell.cpsd.virtualization.capabilities.api.HostPowerOperationResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.dell.cpsd.paqx.fru.amqp.config.RabbitConfig.EXCHANGE_FRU_RESPONSE;

/**
 * TODO: Document usage.
 * <p>
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 * </p>
 *
 * @version 1.0
 * @since 1.0
 */
public class VCenterHostPowerOperationResponseHandler extends DefaultMessageHandler<HostPowerOperationResponseMessage>
        implements AsyncAcknowledgement<VCenterHostPowerOperationStatus> {
    private static final Logger LOG = LoggerFactory
            .getLogger(VCenterHostPowerOperationResponseHandler.class);
    private Map<String, CompletableFuture<VCenterHostPowerOperationStatus>> asyncRequests = new HashMap<>();

    public VCenterHostPowerOperationResponseHandler(ErrorTransformer<HasMessageProperties<?>> errorTransformer) {
        super(HostPowerOperationResponseMessage.class, new DefaultMessageValidator<>(), EXCHANGE_FRU_RESPONSE, errorTransformer);
    }

    @Override
    protected void executeOperation(HostPowerOperationResponseMessage hostPowerOperationResponseMessage) throws Exception {
        LOG.info("Received message {}", hostPowerOperationResponseMessage);
        final String correlationId = hostPowerOperationResponseMessage.getMessageProperties().getCorrelationId();

        final VCenterHostPowerOperationStatus vCenterHostPowerOperationStatus = new VCenterHostPowerOperationStatus(
                hostPowerOperationResponseMessage.getStatus().value());

        final CompletableFuture<VCenterHostPowerOperationStatus> completableFuture = asyncRequests.get(correlationId);

        LOG.info("Completing expectation for  {} {}", correlationId, completableFuture);

        if (completableFuture != null) {
            final boolean complete = completableFuture.complete(vCenterHostPowerOperationStatus);
            LOG.info("Completed expectation for  {} {} {}", correlationId, completableFuture, complete);
            asyncRequests.remove(correlationId);
        }
    }

    @Override
    public CompletableFuture<VCenterHostPowerOperationStatus> register(String correlationId) {

        LOG.info("Setting expectation for  {}", correlationId);
        CompletableFuture<VCenterHostPowerOperationStatus> completableFuture = new CompletableFuture<>();
        completableFuture.whenComplete((systemRest, throwable) -> asyncRequests.remove(correlationId));
        asyncRequests.put(correlationId, completableFuture);
        return completableFuture;
    }
}
