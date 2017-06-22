/**
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 */

package com.dell.cpsd.paqx.fru.rest;

import com.dell.cpsd.hdp.capability.registry.api.Capability;
import com.dell.cpsd.hdp.capability.registry.api.CapabilityProvider;
import com.dell.cpsd.hdp.capability.registry.client.CapabilityRegistryException;
import com.dell.cpsd.hdp.capability.registry.client.ICapabilityRegistryLookupManager;
import com.dell.cpsd.hdp.capability.registry.client.callback.ListCapabilityProvidersResponse;
import com.dell.cpsd.paqx.fru.rest.representation.AboutRepresentation;
import com.dell.cpsd.paqx.fru.service.NodeDiscovery;
import com.dell.cpsd.service.common.client.exception.ServiceTimeoutException;
import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * About resource, used to discover status of the system.
 * <p>
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 * </p>
 */
@Api
@Component
@Path("/about")
@Produces(MediaType.APPLICATION_JSON)
public class AboutResource {
    private static final Logger LOG = LoggerFactory.getLogger(AboutResource.class);

    private final ICapabilityRegistryLookupManager capabilityRegistryLookupManager;
    private final NodeDiscovery nodeDiscovery;

    @Autowired
    public AboutResource(final ICapabilityRegistryLookupManager capabilityRegistryLookupManager, final NodeDiscovery nodeDiscovery) {
        Assert.notNull(capabilityRegistryLookupManager, "capabilityRegistryLookupManager cannot be null");
        Assert.notNull(nodeDiscovery, "nodeDiscovery cannot be null");
        this.capabilityRegistryLookupManager = capabilityRegistryLookupManager;
        this.nodeDiscovery = nodeDiscovery;
    }

    @GET
    public void root(@Suspended final AsyncResponse asyncResponse) {

        asyncResponse.setTimeoutHandler(asyncResponse1 -> asyncResponse1
                .resume(Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("{\"status\":\"timeout\"}").build()));
        asyncResponse.setTimeout(10, TimeUnit.SECONDS);

        final AboutRepresentation representation = new AboutRepresentation();
        requiredCapabilities().forEach(capability -> representation.addRequiredCapability(capability));

        try {
            final ListCapabilityProvidersResponse listCapabilityProvidersResponse = this.capabilityRegistryLookupManager
                    .listCapabilityProviders(TimeUnit.SECONDS.toMillis(2L));

            for (final CapabilityProvider capabilityProvider : listCapabilityProvidersResponse.getResponse()) {
                final String provider = capabilityProvider.getIdentity().getName();
                for (final Capability capability : capabilityProvider.getCapabilities()) {
                    representation.capabilityMetBy(capability.getProfile(), provider);
                }
            }
        } catch (CapabilityRegistryException e) {
            asyncResponse.resume(e);
            return;
        } catch (ServiceTimeoutException e) {
            asyncResponse.resume(Response.status(Response.Status.REQUEST_TIMEOUT).build());
            return;
        }
        asyncResponse.resume(Response.ok(representation).build());

        LOG.debug("Completed the response");

/*        nodeDiscovery.discover().thenAccept(nodes ->
        {
            nodes.stream().forEach(node -> representation.addNode(node.getId(), node.getName(),node.getType()));
            asyncResponse.resume(Response.ok(representation).build());
            LOG.debug("Completed response");
        });*/
        LOG.debug("Exiting about the resource");
    }

    private List<String> requiredCapabilities() {
        return Arrays
                .asList("endpoint-registry-lookup", "validate-vcenter-endpoint", "validate-scaleio-endpoint", "data-collection-vcenter",
                        "data-collection-scaleio", "install-esxi", "install-scaleio", "rackhd-list-nodes", "coprhd-list-storage",
                        "vcenter-discover");
    }
}
