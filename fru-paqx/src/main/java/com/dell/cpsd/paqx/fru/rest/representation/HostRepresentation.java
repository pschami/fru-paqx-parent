/**
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 */
package com.dell.cpsd.paqx.fru.rest.representation;

/**
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 */
public class HostRepresentation
{
    private String hostName;
    private String serialNumber;
    private String managementIP;
    private String status;

    public HostRepresentation(final String hostName, final String serialNumber, final String managementIP, final String status)
    {
        this.hostName = hostName;
        this.serialNumber = serialNumber;
        this.managementIP = managementIP;
        this.status = status;
    }

    public String getHostName()
    {
        return hostName;
    }

    public String getSerialNumber()
    {
        return serialNumber;
    }

    public String getManagementIP()
    {
        return managementIP;
    }

    public String getStatus()
    {
        return status;
    }
}
