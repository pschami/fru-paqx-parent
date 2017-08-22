/**
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 */

package com.dell.cpsd.paqx.fru;

import com.dell.cpsd.hdp.capability.registry.client.lookup.config.CapabilityRegistryLookupManagerConfig;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Import;
import java.util.Scanner;


/**
 * Startup application.
 * <p>
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 * </p>
 */
@SpringBootApplication
@Import({CapabilityRegistryLookupManagerConfig.class})
public class Application extends SpringBootServletInitializer
{

    public static void main(String[] args)
    {
        new Application().configure(new SpringApplicationBuilder(Application.class)).bannerMode(Banner.Mode.OFF).run(args);
    }
}
