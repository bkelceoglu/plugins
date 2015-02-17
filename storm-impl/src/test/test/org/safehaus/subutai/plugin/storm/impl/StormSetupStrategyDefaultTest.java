package org.safehaus.subutai.plugin.storm.impl;


import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.safehaus.subutai.common.command.CommandResult;
import org.safehaus.subutai.common.command.RequestBuilder;
import org.safehaus.subutai.common.environment.Environment;
import org.safehaus.subutai.common.environment.EnvironmentNotFoundException;
import org.safehaus.subutai.common.peer.ContainerHost;
import org.safehaus.subutai.common.protocol.Template;
import org.safehaus.subutai.common.tracker.TrackerOperation;
import org.safehaus.subutai.core.env.api.EnvironmentManager;
import org.safehaus.subutai.core.tracker.api.Tracker;
import org.safehaus.subutai.plugin.common.PluginDAO;
import org.safehaus.subutai.plugin.common.api.ClusterSetupException;
import org.safehaus.subutai.plugin.common.api.ClusterSetupStrategy;
import org.safehaus.subutai.plugin.common.api.NodeType;
import org.safehaus.subutai.plugin.storm.api.StormClusterConfiguration;
import org.safehaus.subutai.plugin.zookeeper.api.CommandType;
import org.safehaus.subutai.plugin.zookeeper.api.Zookeeper;
import org.safehaus.subutai.plugin.zookeeper.api.ZookeeperClusterConfig;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith( MockitoJUnitRunner.class )
public class StormSetupStrategyDefaultTest
{
    private StormSetupStrategyDefault stormSetupStrategyDefault;
    private UUID uuid;
    private Set<ContainerHost> mySet;
    private Set<UUID> myUUID;
    private Set<String> myString;
    @Mock
    CommandResult commandResult;
    @Mock
    ContainerHost containerHost;
    @Mock
    StormImpl stormImpl;
    @Mock
    StormClusterConfiguration stormClusterConfiguration;
    @Mock
    Tracker tracker;
    @Mock
    EnvironmentManager environmentManager;
    @Mock
    TrackerOperation trackerOperation;
    @Mock
    Environment environment;
    @Mock
    ClusterSetupStrategy clusterSetupStrategy;
    @Mock
    PluginDAO pluginDAO;
    @Mock
    Zookeeper zookeeper;
    @Mock
    ZookeeperClusterConfig zookeeperClusterConfig;
    @Mock
    Template template;

    @Before
    public void setUp() throws Exception
    {
        uuid = UUID.randomUUID();

        mySet = new HashSet<>();
        mySet.add( containerHost );

        myUUID = new HashSet<>();
        myUUID.add( uuid );

        myString = new HashSet<>(  );
        myString.add( Commands.PACKAGE_NAME );

        // mock constructor
        when( stormImpl.getTracker() ).thenReturn( tracker );
        when( tracker.createTrackerOperation( anyString(), anyString() ) ).thenReturn( trackerOperation );
        when( trackerOperation.getId() ).thenReturn( uuid );

        stormSetupStrategyDefault =
                new StormSetupStrategyDefault( stormImpl, stormClusterConfiguration, trackerOperation );

        // mock setup method
        when( stormImpl.getZookeeperManager() ).thenReturn( zookeeper );
        when( zookeeper.getCluster( anyString() ) ).thenReturn( zookeeperClusterConfig );
        when( zookeeperClusterConfig.getEnvironmentId() ).thenReturn( uuid );
        when( stormImpl.getEnvironmentManager() ).thenReturn( environmentManager );
        when( environmentManager.findEnvironment( any( UUID.class ) ) ).thenReturn( environment );
    }


    @Test
    public void testSetupNoEnvironment() throws Exception
    {
        when( stormImpl.getEnvironmentManager() ).thenThrow( EnvironmentNotFoundException.class );

        stormSetupStrategyDefault.setup();
    }


    @Test(expected = ClusterSetupException.class)
    public void testSetupEnvironmentNotSpecified() throws Exception
    {
        when( environmentManager.findEnvironment( any(UUID.class) ) ).thenReturn( null );

        stormSetupStrategyDefault.setup();
    }


    @Test(expected = ClusterSetupException.class)
    public void testSetupEnvironmentHasNoNodes() throws Exception
    {
        stormSetupStrategyDefault.setup();
    }


    @Test(expected = ClusterSetupException.class)
    public void testSetupNoStormInstalled() throws Exception
    {
        when( environment.getContainerHosts() ).thenReturn( mySet );
        when( containerHost.getTemplate() ).thenReturn( template );

        stormSetupStrategyDefault.setup();
    }


    @Test(expected = ClusterSetupException.class)
    public void testSetupExternalZookeeperException() throws Exception
    {
        when( environment.getContainerHosts() ).thenReturn( mySet );
        when( containerHost.getTemplate() ).thenReturn( template );
        when( template.getProducts() ).thenReturn( myString );
        when( stormClusterConfiguration.isExternalZookeeper() ).thenReturn( true );

        stormSetupStrategyDefault.setup();
    }


    @Test(expected = ClusterSetupException.class)
    public void testSetupClusterAlreadyExist() throws Exception
    {
        when( environment.getContainerHosts() ).thenReturn( mySet );
        when( containerHost.getTemplate() ).thenReturn( template );
        when( template.getProducts() ).thenReturn( myString );
        when( stormImpl.getCluster( anyString() ) ).thenReturn( stormClusterConfiguration );

        stormSetupStrategyDefault.setup();
    }


    @Test(expected = ClusterSetupException.class)
    public void testSetupExternalZookeeperNimbusIsNotPartOfCluster() throws Exception
    {
        when( environment.getContainerHosts() ).thenReturn( mySet );
        when( containerHost.getTemplate() ).thenReturn( template );
        when( template.getProducts() ).thenReturn( myString );
        when( stormClusterConfiguration.isExternalZookeeper() ).thenReturn( true );
        when( stormClusterConfiguration.getNimbus() ).thenReturn( uuid );

        stormSetupStrategyDefault.setup();
    }


    @Test
    public void testSetupExternalZookeeper() throws Exception
    {
        when( environment.getContainerHosts() ).thenReturn( mySet );
        when( containerHost.getTemplate() ).thenReturn( template );
        when( template.getProducts() ).thenReturn( myString );
        when( stormClusterConfiguration.isExternalZookeeper() ).thenReturn( true );
        when( stormClusterConfiguration.getNimbus() ).thenReturn( uuid );
        when( zookeeperClusterConfig.getNodes() ).thenReturn( myUUID );
        when( containerHost.getNodeGroupName() ).thenReturn( StormService.SUPERVISOR.toString() );
        when( containerHost.getId() ).thenReturn( uuid );
        when( environment.getContainerHostById( any(UUID.class) ) ).thenReturn( containerHost );
        when( environment.getContainerHostsByIds( anySetOf( UUID.class ) ) ).thenReturn( mySet );
        when( containerHost.execute( any( RequestBuilder.class) ) ).thenReturn( commandResult );
        when( stormImpl.getPluginDAO() ).thenReturn( pluginDAO );

        stormSetupStrategyDefault.setup();
    }


    @Test
    public void testSetupExternalZookeeperFalse() throws Exception
    {
        when( environment.getContainerHosts() ).thenReturn( mySet );
        when( containerHost.getTemplate() ).thenReturn( template );
        when( template.getProducts() ).thenReturn( myString );
        when( stormClusterConfiguration.isExternalZookeeper() ).thenReturn( false );
        when( stormClusterConfiguration.getNimbus() ).thenReturn( uuid );
        when( zookeeperClusterConfig.getNodes() ).thenReturn( myUUID );
        when( containerHost.getId() ).thenReturn( uuid );
        when( environment.getContainerHostById( any(UUID.class) ) ).thenReturn( containerHost );
        when( environment.getContainerHostsByIds( anySetOf( UUID.class ) ) ).thenReturn( mySet );
        when( containerHost.execute( any( RequestBuilder.class) ) ).thenReturn( commandResult );
        when( stormImpl.getPluginDAO() ).thenReturn( pluginDAO );
        when( containerHost.getNodeGroupName() ).thenReturn( StormService.NIMBUS.toString() );
        when( containerHost.getIpByInterfaceName( anyString() ) ).thenReturn( "test" );
        when( zookeeper.getCommand( CommandType.INSTALL ) ).thenReturn( "testCommand" );
        when( environment.getId() ).thenReturn( uuid );

        stormSetupStrategyDefault.setup();

        // assertions
        verify( stormClusterConfiguration ).setEnvironmentId( uuid );
        assertEquals( pluginDAO, stormImpl.getPluginDAO() );
        verify( trackerOperation ).addLog( "Cluster info successfully saved" );
    }


    @Test
    public void testGetNodePlacementStrategyByNodeType() throws Exception
    {
        StormSetupStrategyDefault.getNodePlacementStrategyByNodeType( NodeType.STORM_NIMBUS );
        StormSetupStrategyDefault.getNodePlacementStrategyByNodeType( NodeType.STORM_SUPERVISOR );
        StormSetupStrategyDefault.getNodePlacementStrategyByNodeType( NodeType.MASTER_NODE );
    }
}