/**
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 **/

package com.dell.cpsd.paqx.fru.service;

import com.dell.cpsd.hdp.capability.registry.api.Capability;
import com.dell.cpsd.hdp.capability.registry.client.CapabilityRegistryException;
import com.dell.cpsd.hdp.capability.registry.client.ICapabilityRegistryLookupManager;
import com.dell.cpsd.paqx.fru.amqp.consumer.handler.AsyncAcknowledgement;
import com.dell.cpsd.paqx.fru.dto.ConsulRegistryResult;
import com.dell.cpsd.paqx.fru.rest.dto.EndpointCredentials;
import com.dell.cpsd.paqx.fru.valueobject.LongRunning;
import com.dell.cpsd.service.common.client.exception.ServiceTimeoutException;
import com.dell.cpsd.storage.capabilities.api.ConsulRegisterRequestMessage;
import com.dell.cpsd.storage.capabilities.api.ListStorageRequestMessage;
import com.dell.cpsd.storage.capabilities.api.MessageProperties;
import com.dell.cpsd.storage.capabilities.api.OrderAckMessage;
import com.dell.cpsd.storage.capabilities.api.OrderInfo;
import com.dell.cpsd.storage.capabilities.api.RegistrationInfo;
import com.dell.cpsd.storage.capabilities.api.SIONodeRemoveData;
import com.dell.cpsd.storage.capabilities.api.SIONodeRemoveRequestMessage;
import com.dell.cpsd.storage.capabilities.api.ScaleIOSystemDataRestRep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 */
@Service
public class ScaleIOServiceImpl implements ScaleIOService
{
    private static final Logger LOG = LoggerFactory.getLogger(ScaleIOServiceImpl.class);

    private final RabbitTemplate                                 rabbitTemplate;
    private final AsyncAcknowledgement<ScaleIOSystemDataRestRep> listStorageAsyncAcknowledgement;
    private final AsyncAcknowledgement<OrderAckMessage>          ackAsyncAcknowledgement;
    private final AsyncAcknowledgement<OrderInfo>                scaleIORemoveAsyncAcknowledgement;
    private final AsyncAcknowledgement<ConsulRegistryResult>     consulRegistryResultAsyncAcknowledgement;

    private final String     replyTo;
    private final FruService fruService;

    @Autowired
    public ScaleIOServiceImpl(final RabbitTemplate rabbitTemplate,
            @Qualifier(value = "listStorageResponseHandler") final AsyncAcknowledgement<ScaleIOSystemDataRestRep> listStorageAsyncAcknowledgement,
            @Qualifier(value = "scaleIOOrderAckResponseHandler") final AsyncAcknowledgement<OrderAckMessage> ackAsyncAcknowledgement,
            @Qualifier(value = "scaleIORemoveResponseHandler") final AsyncAcknowledgement<OrderInfo> scaleIORemoveAsyncAcknowledgement,
            @Qualifier(value = "coprHDConsulRegisterResponseHandler") final AsyncAcknowledgement<ConsulRegistryResult> consulRegistryResultAsyncAcknowledgement,
            final String replyTo, final FruService fruService)
    {
        this.rabbitTemplate = rabbitTemplate;
        this.listStorageAsyncAcknowledgement = listStorageAsyncAcknowledgement;
        this.ackAsyncAcknowledgement = ackAsyncAcknowledgement;
        this.scaleIORemoveAsyncAcknowledgement = scaleIORemoveAsyncAcknowledgement;
        this.consulRegistryResultAsyncAcknowledgement = consulRegistryResultAsyncAcknowledgement;
        this.replyTo = replyTo;
        this.fruService = fruService;
    }

    public CompletableFuture<ScaleIOSystemDataRestRep> listStorage(final EndpointCredentials scaleIOCredentials)
    {
        final String requiredCapability = "coprhd-list-storage";
        try
        {
            final List<Capability> matchedCapabilities = fruService.findMatchingCapabilities(requiredCapability);
            if (matchedCapabilities.isEmpty())
            {
                LOG.info("No matching capability found for capability [{}]", requiredCapability);
                return CompletableFuture.completedFuture(null);
            }
            final Capability matchedCapability = matchedCapabilities.stream().findFirst().get();
            LOG.debug("Found capability {}", matchedCapability.getProfile());

            final Map<String, String> amqpProperties = fruService.declareBinding(matchedCapability, replyTo);
            final String correlationID = UUID.randomUUID().toString();
            final ListStorageRequestMessage requestMessage = new ListStorageRequestMessage();

            // Check data and form Message
            try
            {
                new URL(scaleIOCredentials.getEndpointUrl());
            }
            catch (MalformedURLException e)
            {
                final CompletableFuture<ScaleIOSystemDataRestRep> promise = new CompletableFuture<>();
                promise.completeExceptionally(e);
                return promise;
            }
            requestMessage.setEndpointURL(scaleIOCredentials.getEndpointUrl());
            requestMessage.setPassword(scaleIOCredentials.getPassword());
            requestMessage.setUserName(scaleIOCredentials.getUsername());
            requestMessage.setMessageProperties(new MessageProperties(new Date(), correlationID, replyTo));
            // Message created

            final String requestExchange = amqpProperties.get("request-exchange");
            final String requestRoutingKey = amqpProperties.get("request-routing-key");
            final CompletableFuture<ScaleIOSystemDataRestRep> promise = listStorageAsyncAcknowledgement.register(correlationID.toString());
            rabbitTemplate.convertAndSend(requestExchange, requestRoutingKey, requestMessage);

            return promise;
        }
        catch (ServiceTimeoutException | CapabilityRegistryException e)
        {
            return CompletableFuture.completedFuture(null);
        }
    }

    public LongRunning<OrderAckMessage, OrderInfo> sioNodeRemove(final EndpointCredentials scaleIOCredentials,
            final EndpointCredentials scaleIOMDMCredentials)
    {
        final String requiredCapability = "coprhd-sio-node-remove";
        try
        {
            final List<Capability> matchedCapabilities = fruService.findMatchingCapabilities(requiredCapability);
            if (matchedCapabilities.isEmpty())
            {
                LOG.info("No matching capability found for capability [{}]", requiredCapability);
                return new LongRunning<>(CompletableFuture.completedFuture(null), CompletableFuture.completedFuture(null));
            }
            final Capability matchedCapability = matchedCapabilities.stream().findFirst().get();
            LOG.debug("Found capability {}", matchedCapability.getProfile());

            final Map<String, String> amqpProperties = fruService.declareBinding(matchedCapability, replyTo);
            final String correlationID = UUID.randomUUID().toString();
            SIONodeRemoveRequestMessage requestMessage = new SIONodeRemoveRequestMessage();

            // Check data and form Message
            try
            {
                new URL(scaleIOCredentials.getEndpointUrl());
            }
            catch (MalformedURLException e)
            {
                final CompletableFuture<OrderAckMessage> acknowledgementPromise = new CompletableFuture<>();
                acknowledgementPromise.completeExceptionally(e);
                final CompletableFuture<OrderInfo> completionPromise = new CompletableFuture<>();
                completionPromise.completeExceptionally(e);
                return new LongRunning<>(acknowledgementPromise, completionPromise);
            }


            // Create default message data
            // TODO: Get the data for the request message

            SIONodeRemoveData sioNodeRemoveData = new SIONodeRemoveData();
            sioNodeRemoveData.setScaleioInterface("eth0");
            sioNodeRemoveData.setScaleioVolumeName("empty");

            // Assumption about usr/password being same for MDM,SDS,SDC
            final String USER = scaleIOMDMCredentials.getUsername();
            final String PASSWORD = scaleIOMDMCredentials.getPassword();

            // these IP addresses need to come from coprhd/vcenter correlation data set from some db or dto object
            sioNodeRemoveData.setMdmHosts(
                    "[{‘ip’:‘1.1.1.50’,‘user’:‘" + USER + "’,‘pass’:‘" + PASSWORD + "’},{‘ip’:‘1.1.1.51’,‘user’:‘" + USER
                            + "’,‘pass’:‘" + PASSWORD + "’},{‘ip’:‘1.1.1.52’,‘user’:‘" + USER + "’,‘pass’:‘" + PASSWORD
                            + "’}]");
            sioNodeRemoveData.setSdcHosts("[{‘ip’:‘1.1.1.54’,‘user’:‘" + USER + "’,‘pass’:‘" + PASSWORD + "’}]");
            sioNodeRemoveData.setSdsHosts("[]");

            requestMessage.setSIONodeRemoveData(sioNodeRemoveData);
            requestMessage.setApiPortNumber("4443");
            requestMessage.setServicePortNumber("443");
            // TODO: are these the right creds? SIO creds vs CoprHD creds
            // Hard coded in coprhd client right now so these are not used
            requestMessage.setPassword(scaleIOCredentials.getPassword());
            requestMessage.setUserName(scaleIOCredentials.getUsername());

            final CompletableFuture<OrderAckMessage> acknowledgementPromise = ackAsyncAcknowledgement
                    .register(correlationID.toString());
            final CompletableFuture<OrderInfo> completionPromise = scaleIORemoveAsyncAcknowledgement
                    .register(correlationID.toString());

            final String requestExchange = amqpProperties.get("request-exchange");
            final String requestRoutingKey = amqpProperties.get("request-routing-key");
            // final String requestAckRoutingKey = amqpProperties.get("request-ack-routing-key"); //// This is done in FruServiceImpl now
            rabbitTemplate.convertAndSend(requestExchange, requestRoutingKey, requestMessage);

            return new LongRunning<>(acknowledgementPromise, completionPromise);
        }
        catch (ServiceTimeoutException | CapabilityRegistryException e)
        {
            return new LongRunning<>(CompletableFuture.completedFuture(null), CompletableFuture.completedFuture(null));
        }
    }

    public CompletableFuture<ConsulRegistryResult> requestConsulRegistration(final EndpointCredentials scaleIOCredentials)
    {
        final String requiredCapability = "coprhd-consul-register";
        try
        {
            final List<Capability> matchedCapabilities = fruService.findMatchingCapabilities(requiredCapability);
            if (matchedCapabilities.isEmpty())
            {
                LOG.info("No matching capability found for capability [{}]", requiredCapability);
                return CompletableFuture.completedFuture(null);
            }
            final Capability matchedCapability = matchedCapabilities.stream().findFirst().get();
            LOG.debug("Found capability {}", matchedCapability.getProfile());

            final Map<String, String> amqpProperties = fruService.declareBinding(matchedCapability, replyTo);

            final UUID correlationId = UUID.randomUUID();
            ConsulRegisterRequestMessage requestMessage = new ConsulRegisterRequestMessage();
            requestMessage.setMessageProperties(
                    new com.dell.cpsd.storage.capabilities.api.MessageProperties().withCorrelationId(correlationId.toString())
                            .withReplyTo(replyTo).withTimestamp(new Date()));

            try
            {
                new URL(scaleIOCredentials.getEndpointUrl());
            }
            catch (MalformedURLException e)
            {
                final CompletableFuture<ConsulRegistryResult> promise = new CompletableFuture<>();
                promise.completeExceptionally(e);
                return promise;
            }
            final RegistrationInfo registrationInfo = new RegistrationInfo(scaleIOCredentials.getEndpointUrl(),
                    scaleIOCredentials.getPassword(), scaleIOCredentials.getUsername());
            requestMessage.setRegistrationInfo(registrationInfo);

            final CompletableFuture<ConsulRegistryResult> promise = consulRegistryResultAsyncAcknowledgement
                    .register(correlationId.toString());

            final String requestExchange = amqpProperties.get("request-exchange");
            final String requestRoutingKey = amqpProperties.get("request-routing-key");
            rabbitTemplate.convertAndSend(requestExchange, requestRoutingKey, requestMessage);

            return promise;
        }
        catch (ServiceTimeoutException | CapabilityRegistryException e)
        {
            return CompletableFuture.completedFuture(null);
        }
    }
}
