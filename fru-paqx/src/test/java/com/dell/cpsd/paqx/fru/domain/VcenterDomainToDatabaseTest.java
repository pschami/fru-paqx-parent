/**
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * Dell EMC Confidential/Proprietary Information
 **/

package com.dell.cpsd.paqx.fru.domain;

import com.dell.cpsd.paqx.fru.amqp.config.*;
import com.dell.cpsd.paqx.fru.amqp.config.PersistenceConfig;
import com.dell.cpsd.paqx.fru.amqp.config.PersistencePropertiesConfig;
import com.dell.cpsd.paqx.fru.rest.repository.DataServiceRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;

@RunWith(SpringRunner.class)
@Import({ConsumerConfig.class, ContextConfig.class, PersistenceConfig.class, PersistencePropertiesConfig.class, ProducerConfig.class, ProductionConfig.class, PropertiesConfig.class, RabbitConfig.class})
@DataJpaTest
public class VcenterDomainToDatabaseTest
{
    private static int idCount=0;
    private static int clusterCount=2;
    private static int hostCount=5;

    @Autowired
    TestEntityManager testEntityManager;

/*    @Test
    public void testvCenterDomain() throws Exception {
        VCenter data = createDataObject();

        UUID randomUUID = UUID.randomUUID();
        Long databaseJobUUID = repository.saveVCenterData(randomUUID, data);

        FruJob job = testEntityManager.find(FruJob.class, databaseJobUUID);
        VCenter vcenterData = job.getVcenter();

        VCenter actualScaleIOData = testEntityManager.find(VCenter.class, vcenterData.getUuid());

        compareDataModels(data, vcenterData);
        compareDataModels(data, actualScaleIOData);
    }*/

    @Test
    public void createDataObject() throws Exception
    {
        // Create a vcenter instance
        VCenter vCenter = new VCenter(newId(), "vc-1");

        // Create datacenter and persist entry
        Datacenter dc = new Datacenter(newId(), "dc-1");
        dc.setvCenter(vCenter);
        vCenter.setDatacenterList(Arrays.asList(dc));
        testEntityManager.persist(vCenter);

        Datacenter dcQuery = testEntityManager.find(Datacenter.class,1l);
        assertTrue(dc.equals(dcQuery));
        assertTrue(vCenter.equals(dcQuery.getvCenter()));

        // create clusters
        List<Cluster> clusterList = new ArrayList<>();
        for(int i=0; i<clusterCount; i++)
        {
            // Create clusters
            Cluster cluster = new Cluster(newId(), newNamedId("domain"));
            cluster.setDatacenter(dc);
            for(int j=0; j<hostCount; j++)
            {
                Host host = new Host(newId(),newNamedId("host"),"on");
                host.setCluster(cluster);
                cluster.addHost(host);
            }
            clusterList.add(cluster);
        }
        dc.setClusterList(clusterList);

        testEntityManager.persist(dc);

        // Validate cluster list for dc is as expected
        dcQuery = testEntityManager.find(Datacenter.class,1l);
        assertTrue(dcQuery.getClusterList().containsAll(clusterList));

        // Validate cluster exists
        Cluster clusterQuery = testEntityManager.find(Cluster.class, 1l);
        assertTrue(clusterQuery.getDatacenter().equals(dc));
        assertTrue(clusterQuery.equals(clusterList.get(0)));

        clusterQuery = testEntityManager.find(Cluster.class, 2l);
        assertTrue(clusterQuery.getDatacenter().equals(dc));
        assertTrue(clusterQuery.equals(clusterList.get(1)));


//      dcQuery = testEntityManager.find(Datacenter.class,1l);

        // Add datastores to datacenter
        Datastore ds1 = new Datastore(newId(), "ds-01","VMFS", "/im/a/fake/url1");
        Datastore ds2 = new Datastore(newId(), "ds-02","VMFS", "/im/a/fake/url2");
        Datastore ds3 = new Datastore(newId(), "ds-03","VMFS", "/im/a/fake/url3");
        ds1.setDatacenter(dc);
        ds2.setDatacenter(dc);
        ds3.setDatacenter(dc);

        // Validate no datastores exist within dc
        dcQuery = testEntityManager.find(Datacenter.class,1l);
        assertTrue(dcQuery.getDatastoreList().isEmpty());

        // Update dc with datastores
        List<Datastore> datastoreList = Arrays.asList(ds1, ds2, ds3);
        dc.setDatastoreList(datastoreList);

        testEntityManager.persist(dc);

        Datastore datastoreQuery = testEntityManager.find(Datastore.class, 1l);
        assertTrue(datastoreQuery.equals(ds1));
        assertTrue(datastoreQuery.getDatacenter().equals(dc));

        datastoreQuery = testEntityManager.find(Datastore.class, 2l);
        assertTrue(datastoreQuery.equals(ds2));
        assertTrue(datastoreQuery.getDatacenter().equals(dc));

        datastoreQuery = testEntityManager.find(Datastore.class, 3l);
        assertTrue(datastoreQuery.equals(ds3));
        assertTrue(datastoreQuery.getDatacenter().equals(dc));

        // Create some dvSwitches
        DVSwitch dvSwitch1 = new DVSwitch(newId(), "dvs-1", true);
        dvSwitch1.setDatacenter(dc);

        DVSwitch dvSwitch2 = new DVSwitch(newId(), "dvs-2", true);
        dvSwitch2.setDatacenter(dc);

        // Do bi-directional mapping & persist
        List<DVSwitch> dvSwitches = Arrays.asList(dvSwitch1, dvSwitch2);
        dc.setDvSwitchList(dvSwitches);

        testEntityManager.persist(dc);

        // Validate dvswitches are mapped to dc
        DVSwitch dvSwitchQuery = testEntityManager.find(DVSwitch.class, 1l);
        assertTrue(dvSwitchQuery.equals(dvSwitch1));
        assertTrue(dvSwitchQuery.getDatacenter().equals(dc));

        // Validate dc mapped to dvswitches
        dcQuery = testEntityManager.find(Datacenter.class, 1l);
        assertTrue(dcQuery.getDvSwitchList().containsAll(dvSwitches));

//
//        // Add hosts to clusters
//        Host host1 = new Host(newId(), "host-01","POWERED_ON", cluster1);
//        Host host2 = new Host(newId(), "host-02","POWERED_ON", cluster1);
//        Host host3 = new Host(newId(), "host-03","POWERED_ON", cluster1);
//        List<Host> cluster1HostList = Arrays.asList(host1, host2, host3);
//
//        cluster1.setHostList(cluster1HostList);
//
//        Host host4 = new Host(newId(), "host-04","POWERED_ON", cluster2);
//        Host host5 = new Host(newId(), "host-05","POWERED_ON", cluster2);
//
//        List<Host> cluster2HostList = Arrays.asList(host4, host5);
//
//        cluster2.setHostList(cluster2HostList);

    }

    private void compareDataModels(final VCenter data, final VCenter data2)
    {
        assertTrue(data.equals(data2));
    }

    private static String newId()
    {
        return new String("id"+(idCount++));
    }

    private static String newNamedId(String baseName)
    {
        return new String(baseName+"-"+(idCount++));
    }
}

