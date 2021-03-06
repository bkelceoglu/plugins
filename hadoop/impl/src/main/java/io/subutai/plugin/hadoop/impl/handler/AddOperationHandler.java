package io.subutai.plugin.hadoop.impl.handler;


import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.subutai.common.command.CommandException;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.environment.ContainerHostNotFoundException;
import io.subutai.common.environment.Environment;
import io.subutai.common.environment.EnvironmentModificationException;
import io.subutai.common.environment.EnvironmentNotFoundException;
import io.subutai.common.environment.Node;
import io.subutai.common.environment.Topology;
import io.subutai.common.peer.ContainerSize;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.Peer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.ResourceHost;
import io.subutai.common.resource.PeerGroupResources;
import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.plugincommon.api.AbstractOperationHandler;
import io.subutai.core.plugincommon.api.ClusterException;
import io.subutai.plugin.hadoop.api.HadoopClusterConfig;
import io.subutai.plugin.hadoop.impl.Commands;
import io.subutai.plugin.hadoop.impl.HadoopImpl;


public class AddOperationHandler extends AbstractOperationHandler<HadoopImpl, HadoopClusterConfig>
{

    private int nodeCount;
    private static final Logger LOGGER = LoggerFactory.getLogger( AddOperationHandler.class );


    public AddOperationHandler( HadoopImpl manager, String clusterName, int nodeCount )
    {
        super( manager, manager.getCluster( clusterName ) );
        this.nodeCount = nodeCount;
        trackerOperation = manager.getTracker().createTrackerOperation( HadoopClusterConfig.PRODUCT_KEY,
                String.format( "Adding node to cluster %s", clusterName ) );
    }


    @Override
    public void run()
    {
        addNode();
    }


    /**
     * Steps: 1) Creates a new container from hadoop template 2) Include node
     */
    public void addNode()
    {
        try
        {
            EnvironmentManager environmentManager = manager.getEnvironmentManager();
            Set<EnvironmentContainerHost> newlyCreatedContainers = new HashSet<>();
            /**
             * first check if there are containers in environment that is not being used in hadoop cluster,
             * if yes, then do not create new containers.
             */
            Environment environment = environmentManager.loadEnvironment( config.getEnvironmentId() );
            int numberOfContainersNotBeingUsed = 0;
            boolean allContainersNotBeingUsed = false;
            for ( EnvironmentContainerHost containerHost : environment.getContainerHosts() )
            {
                if ( !config.getAllNodes().contains( containerHost.getId() ) )
                {
                    if ( containerHost.getTemplateName().equals( HadoopClusterConfig.TEMPLATE_NAME ) )
                    {
                        if ( !isThisNodeUsedInOtherClusters( containerHost.getId() ) )
                        {
                            allContainersNotBeingUsed = true;
                            numberOfContainersNotBeingUsed++;
                        }
                    }
                }
            }


            if ( ( !allContainersNotBeingUsed ) | ( numberOfContainersNotBeingUsed < nodeCount ) )
            {

                String containerName = HadoopClusterConfig.PRODUCT_NAME + "_" + System.currentTimeMillis();
                final int newNodes = nodeCount - numberOfContainersNotBeingUsed;
                Set<Node> nodeGroups = new HashSet<>();
                PeerGroupResources groupResources = manager.getPeerManager().getPeerGroupResources();
                for ( Peer peer : environment.getPeers() )
                {
                    try
                    {
                        groupResources.getResources().add(
                                peer.getResourceLimits( manager.getPeerManager().getLocalPeer().getId() ) );
                    }
                    catch ( PeerException e )
                    {
                        //ignore
                    }
                }

                for ( int i = 0; i < newNodes; i++ )
                {
                    ResourceHost resourceHost =
                            manager.getPeerManager().getLocalPeer().getResourceHosts().iterator().next();

                    Node nodeGroup =
                            new Node( UUID.randomUUID().toString(), containerName, HadoopClusterConfig.TEMPLATE_NAME,
                                    ContainerSize.SMALL, 1, 1, resourceHost.getPeerId(), resourceHost.getId() );

                    nodeGroups.add( nodeGroup );
                }


                if ( numberOfContainersNotBeingUsed > 0 )
                {
                    trackerOperation.addLog(
                            "Using " + numberOfContainersNotBeingUsed + " existing containers and creating" + (
                                    nodeCount - numberOfContainersNotBeingUsed ) + " containers." );
                }
                else
                {
                    trackerOperation.addLog( "Creating new containers..." );
                }
                Topology topology = new Topology( environment.getName() );

                for ( Node nodeGroup : nodeGroups )
                {
                    topology.addNodePlacement( nodeGroup.getPeerId(), nodeGroup );
                }
                newlyCreatedContainers =
                        environmentManager.growEnvironment( config.getEnvironmentId(), topology, false );
                for ( EnvironmentContainerHost host : newlyCreatedContainers )
                {
                    config.getDataNodes().add( host.getId() );
                    config.getTaskTrackers().add( host.getId() );
                    trackerOperation.addLog( host.getHostname() + " is added as slave node." );
                }
            }
            else
            {
                trackerOperation.addLog( "Using existing containers that are not taking role in cluster" );
                // update cluster configuration on DB
                int count = 0;
                environment = environmentManager.loadEnvironment( config.getEnvironmentId() );
                for ( EnvironmentContainerHost containerHost : environment.getContainerHosts() )
                {
                    if ( !config.getAllNodes().contains( containerHost.getId() ) )
                    {
                        if ( count <= nodeCount )
                        {
                            config.getDataNodes().add( containerHost.getId() );
                            config.getTaskTrackers().add( containerHost.getId() );
                            trackerOperation.addLog( containerHost.getHostname() + " is added as slave node." );
                            count++;
                            newlyCreatedContainers.add( containerHost );
                        }
                    }
                }
            }

            manager.saveConfig( config );

            // configure ssh keys
            //TODO: fix me below
            //            Set<EnvironmentContainerHost> allNodes = new HashSet<>();
            //            allNodes.addAll( newlyCreatedContainers );
            //            allNodes.addAll( environment.getContainerHosts() );
            //            Set<ContainerHost> ch = Sets.newHashSet();
            //            ch.addAll( allNodes );
            //            try
            //            {
            //                manager.getNetworkManager().exchangeSshKeys( ch );
            //            }
            //            catch ( NetworkManagerException e )
            //            {
            //                logExceptionWithMessage( "Error exchanging with keys", e );
            //                return;
            //            }

            // include newly created containers to existing hadoop cluster
            for ( EnvironmentContainerHost containerHost : newlyCreatedContainers )
            {
                trackerOperation.addLog( "Configuring " + containerHost.getHostname() );
                configureSlaveNode( config, containerHost, environment );
                manager.includeNode( config, containerHost.getHostname() );
            }
            trackerOperation.addLogDone( "Finished." );
        }
        catch ( EnvironmentNotFoundException | EnvironmentModificationException | PeerException e )
        {
            logExceptionWithMessage( "Error executing operations with environment", e );
        }
        catch ( ClusterException e )
        {
            LOGGER.error( "Could not save cluster configuration", e );
            e.printStackTrace();
        }
    }


    /**
     * Configures newly added slave node.
     *
     * @param config hadoop configuration
     * @param containerHost node to be configured
     * @param environment environment in which given container reside
     */
    private void configureSlaveNode( HadoopClusterConfig config, EnvironmentContainerHost containerHost,
                                     Environment environment )
    {
        try
        {
            // Clear configuration files
            executeCommandOnContainer( containerHost, Commands.getClearMastersCommand() );
            executeCommandOnContainer( containerHost, Commands.getClearSlavesCommand() );
            // Configure NameNode
            EnvironmentContainerHost namenode =
                    ( EnvironmentContainerHost ) environment.getContainerHostById( config.getNameNode() );
            EnvironmentContainerHost jobtracker =
                    ( EnvironmentContainerHost ) environment.getContainerHostById( config.getJobTracker() );
            executeCommandOnContainer( containerHost,
                    Commands.getSetMastersCommand( namenode.getHostname(), jobtracker.getHostname(),
                            config.getReplicationFactor() ) );
        }
        catch ( ContainerHostNotFoundException e )
        {
            logExceptionWithMessage( "Error while configuring slave node and getting container host by id", e );
        }
    }


    private void executeCommandOnContainer( EnvironmentContainerHost containerHost, String command )
    {
        try
        {
            containerHost.execute( new RequestBuilder( command ) );
        }
        catch ( CommandException e )
        {
            logExceptionWithMessage( "Error executing command: " + command, e );
        }
    }


    private void logExceptionWithMessage( String message, Exception e )
    {
        LOGGER.error( message, e );
        trackerOperation.addLogFailed( message );
    }


    private boolean isThisNodeUsedInOtherClusters( String id )
    {
        List<HadoopClusterConfig> configs = manager.getClusters();
        for ( HadoopClusterConfig config1 : configs )
        {
            if ( config1.getAllNodes().contains( id ) )
            {
                return true;
            }
        }
        return false;
    }
}
