/**
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 */

package com.dell.cpsd.paqx.fru.amqp.consumer.handler;

import com.dell.cpsd.common.rabbitmq.consumer.error.ErrorTransformer;
import com.dell.cpsd.common.rabbitmq.consumer.handler.DefaultMessageHandler;
import com.dell.cpsd.common.rabbitmq.message.HasMessageProperties;
import com.dell.cpsd.common.rabbitmq.validators.DefaultMessageValidator;
import com.dell.cpsd.paqx.fru.rest.dto.vcenter.HostMaintenanceModeResponse;
import com.dell.cpsd.virtualization.capabilities.api.HostMaintenanceModeResponseMessage;
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
public class vCenterHostMaintenanceModeResponseHandler extends DefaultMessageHandler<HostMaintenanceModeResponseMessage>
        implements AsyncAcknowledgement<HostMaintenanceModeResponse> {
    private static final Logger LOG = LoggerFactory
            .getLogger(VCenterDestroyVmResponseHandler.class);
    private Map<String, CompletableFuture<HostMaintenanceModeResponse>> asyncRequests = new HashMap<>();

    public vCenterHostMaintenanceModeResponseHandler(ErrorTransformer<HasMessageProperties<?>> errorTransformer) {
        super(HostMaintenanceModeResponseMessage.class, new DefaultMessageValidator<>(), EXCHANGE_FRU_RESPONSE, errorTransformer);
    }

    @Override
    protected void executeOperation(final HostMaintenanceModeResponseMessage responseMessage) throws Exception {
        LOG.info("Received message {}", responseMessage);
        final String correlationId = responseMessage.getMessageProperties().getCorrelationId();

        final HostMaintenanceModeResponse hostMaintenanceModeResponse = new HostMaintenanceModeResponse(
                responseMessage.getStatus().value());

        final CompletableFuture<HostMaintenanceModeResponse> completableFuture = asyncRequests.get(correlationId);

        LOG.info("Completing expectation for  {} {}", correlationId, completableFuture);

        if (completableFuture != null) {
            final boolean complete = completableFuture.complete(hostMaintenanceModeResponse);
            LOG.info("Completed expectation for  {} {} {}", correlationId, completableFuture, complete);
            asyncRequests.remove(correlationId);
        }
    }

    @Override
    public CompletableFuture<HostMaintenanceModeResponse> register(final String correlationId) {
        LOG.info("Setting expectation for  {}", correlationId);
        CompletableFuture<HostMaintenanceModeResponse> completableFuture = new CompletableFuture<>();
        completableFuture.whenComplete((systemRest, throwable) -> asyncRequests.remove(correlationId));
        asyncRequests.put(correlationId, completableFuture);
        return completableFuture;
    }
}