package io.subutai.plugin.mysql.impl.handler;


import java.util.Set;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import io.subutai.common.command.CommandCallback;
import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.command.Response;
import io.subutai.common.environment.Blueprint;
import io.subutai.common.environment.ContainerHostNotFoundException;
import io.subutai.common.environment.Environment;
import io.subutai.common.environment.EnvironmentModificationException;
import io.subutai.common.environment.EnvironmentNotFoundException;
import io.subutai.common.environment.NodeGroup;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.protocol.PlacementStrategy;
import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.plugincommon.api.AbstractOperationHandler;
import io.subutai.core.plugincommon.api.ClusterConfigurationException;
import io.subutai.core.plugincommon.api.ClusterException;
import io.subutai.core.plugincommon.api.ClusterOperationHandlerInterface;
import io.subutai.core.plugincommon.api.ClusterOperationType;
import io.subutai.core.plugincommon.api.NodeType;
import io.subutai.plugin.mysql.api.MySQLClusterConfig;
import io.subutai.plugin.mysql.impl.ClusterConfig;
import io.subutai.plugin.mysql.impl.MySQLCImpl;
import io.subutai.plugin.mysql.impl.common.Commands;


public class ClusterOperationHandler extends AbstractOperationHandler<MySQLCImpl, MySQLClusterConfig>
        implements ClusterOperationHandlerInterface
{
    private static final Logger LOG = Logger.getLogger( ClusterOperationHandler.class.getName() );
    private ClusterOperationType operationType;
    private MySQLClusterConfig config;
    private NodeType nodeType;


    public ClusterOperationHandler( final MySQLCImpl manager, final MySQLClusterConfig mySQLClusterConfig,
                                    final ClusterOperationType clusterOperationType, NodeType nodeType )
    {
        super( manager, mySQLClusterConfig );
        this.config = mySQLClusterConfig;
        this.operationType = clusterOperationType;
        this.nodeType = nodeType;
        trackerOperation = manager.getTracker().createTrackerOperation( MySQLClusterConfig.PRODUCT_KEY,
                String.format( "Creating %s tracker object...", clusterName ) );
    }


    @Override
    public void run()
    {

        Preconditions.checkNotNull( config, "Configuration is null !!!" );
        switch ( operationType )
        {
            case INSTALL:
                setupCluster();
                break;
            case STOP_ALL:
                stopCluster( config );
                break;
            case DESTROY:
                destroyCluster();
                break;
            case START_ALL:
                startCluster( config );
                break;
            case ADD:
                addNode( nodeType );
                break;
        }
    }


    private void addNode( NodeType nodeType )
    {
        LocalPeer localPeer = manager.getPeerManager().getLocalPeer();
        EnvironmentManager environmentManager = manager.getEnvironmentManager();
        NodeGroup nodeGroup = new NodeGroup( MySQLClusterConfig.PRODUCT_NAME, MySQLClusterConfig.TEMPLATE_NAME, 1, 1, 1,
                new PlacementStrategy( "ROUND_ROBIN" ), localPeer.getId() );

        Blueprint blueprint = new Blueprint( "nodeGroup" + nodeType.name(), null, Sets.newHashSet( nodeGroup ) );

        EnvironmentContainerHost newNode;


        EnvironmentContainerHost unusedNodeInEnvironment = findUnUsedContainerInEnvironment( environmentManager );
        if ( unusedNodeInEnvironment != null )
        {
            newNode = unusedNodeInEnvironment;
        }
        else
        {
            Set<EnvironmentContainerHost> newNodeSet;

            try
            {
                newNodeSet = environmentManager.growEnvironment( config.getEnvironmentId(), blueprint, false );
            }
            catch ( EnvironmentModificationException | EnvironmentNotFoundException e )
            {
                LOG.severe( "Could not add new node to environment" );
                throw new RuntimeException( e );
            }
            newNode = newNodeSet.iterator().next();
        }
        switch ( nodeType )
        {

            case MASTER_NODE:
                config.getManagerNodes().add( newNode.getId() );
                break;
            case DATANODE:
                config.getDataNodes().add( newNode.getId() );
                break;
        }
        try
        {
            manager.saveConfig( config );
        }
        catch ( ClusterException e )
        {
            LOG.severe( String.format( "Could not save cluster configuration. %s", e.toString() ) );
        }

        try
        {

            new ClusterConfig( trackerOperation, manager )
                    .configureCluster( config, environmentManager.loadEnvironment( config.getEnvironmentId() ) );
        }
        catch ( EnvironmentNotFoundException | ClusterConfigurationException e )
        {
            e.printStackTrace();
        }
        trackerOperation.addLog( "Node added" );
    }


    private EnvironmentContainerHost findUnUsedContainerInEnvironment( EnvironmentManager environmentManager )
    {
        EnvironmentContainerHost unusedNode = null;

        try
        {
            Environment environment = environmentManager.loadEnvironment( config.getEnvironmentId() );
            Set<EnvironmentContainerHost> containerHostSet = environment.getContainerHosts();
            for ( EnvironmentContainerHost host : containerHostSet )
            {
                if ( ( !config.getAllNodes().contains( host.getId() ) ) && host.getTemplateName().equals(
                        MySQLClusterConfig.TEMPLATE_NAME ) )
                {
                    unusedNode = host;
                    break;
                }
            }
        }
        catch ( EnvironmentNotFoundException e )
        {
            e.printStackTrace();
        }
        return checkUnusedNode( unusedNode );
    }


    private EnvironmentContainerHost checkUnusedNode( EnvironmentContainerHost node )
    {
        if ( node != null )
        {
            for ( MySQLClusterConfig config : manager.getClusters() )
            {
                if ( !config.getAllNodes().contains( node.getId() ) )
                {
                    return node;
                }
            }
        }
        return null;
    }


    /*
    *  Stops whole cluster through manager node
    * */
    private void stopCluster( MySQLClusterConfig config )
    {
        Environment env;
        CommandResult commandResult;

        try
        {
            env = manager.getEnvironmentManager().loadEnvironment( config.getEnvironmentId() );
        }
        catch ( EnvironmentNotFoundException e )
        {
            throw new RuntimeException( e );
        }
        //on each management node issue ndb_mgm -e shutdown command
        for ( String nodeId : config.getManagerNodes() )
        {
            try
            {
                EnvironmentContainerHost host = env.getContainerHostById( nodeId );
                commandResult = host.execute( new RequestBuilder( Commands.stopAllCommand ) );
                trackerOperation.addLog( Commands.stopAllCommand + "\n" + commandResult.getStdOut() );
            }
            catch ( ContainerHostNotFoundException | CommandException e )
            {
                e.printStackTrace();
            }
        }
        //shutdown mysql servers if they are installed on data nodes
        for ( String nodeId : config.getDataNodes() )
        {
            try
            {
                EnvironmentContainerHost host = env.getContainerHostById( nodeId );
                if ( config.getIsSqlInstalled().get( host.getHostname() ) )
                {
                    host.executeAsync( new RequestBuilder( Commands.stopMySQLServer ), new CommandCallback()
                    {
                        @Override
                        public void onResponse( final Response response, final CommandResult commandResult )
                        {
                            trackerOperation.addLogDone( commandResult.getStdOut() );
                        }
                    } );
                }
            }
            catch ( CommandException | ContainerHostNotFoundException e )
            {
                e.printStackTrace();
            }
        }
        trackerOperation
                .addLogDone( String.format( "MySQL Cluster %s", config.getClusterName() + " has been stopped" ) );
    }


    //start cluster
    public void startCluster( MySQLClusterConfig config )
    {
        Environment env;
        try
        {
            env = manager.getEnvironmentManager().loadEnvironment( config.getEnvironmentId() );
        }
        catch ( EnvironmentNotFoundException e )
        {
            throw new RuntimeException( e );
        }

        CommandResult result;
        EnvironmentContainerHost containerHost;

        LOG.info( String.format( "Total management nodes to start: %d", config.getManagerNodes().size() ) );
        //start management nodes, before starting data nodes
        for ( String managerNode : config.getManagerNodes() )
        {
            try
            {
                String commandToExecute = Commands.startManagementNode;
                containerHost = env.getContainerHostById( managerNode );
                //if management node requires reload of config file pass --initial arg
                if ( config.getRequiresReloadConf().get( containerHost.getHostname() ) )
                {
                    commandToExecute = Commands.startInitManageNode;
                    config.getRequiresReloadConf().put( containerHost.getHostname(), false );

                    manager.saveConfig( config );
                }

                trackerOperation.addLog( commandToExecute );
                result = containerHost.execute( new RequestBuilder( commandToExecute ) );

                LOG.info( "Starting management node..." );

                trackerOperation.addLog( result.getStdOut() );
            }
            catch ( CommandException | ContainerHostNotFoundException | ClusterException e )
            {
                throw new RuntimeException( e );
            }
        }

        LOG.info( String.format( "Total data nodes to start: %d", config.getDataNodes().size() ) );
        //start data nodes. order and sequence does not matter
        for ( String dataNode : config.getDataNodes() )
        {

            try
            {
                containerHost = env.getContainerHostById( dataNode );
                LOG.info(
                        String.format( "Starting data node(ndbd) at container host %s", containerHost.getHostname() ) );
                trackerOperation.addLog( String.format( "Starting ndbd %s ", containerHost.getHostname() ) );
                //start with --initial arg if starting ndbd for the first time
                if ( config.getIsInitialStart().get( containerHost.getHostname() ) )
                {
                    containerHost.executeAsync(
                            new RequestBuilder( Commands.ndbdInit ).daemon().withCwd( config.getDataNodeDataDir() ),
                            new CommandCallback()
                            {
                                @Override
                                public void onResponse( final Response response, final CommandResult commandResult )
                                {
                                    trackerOperation.addLog( commandResult.getStdOut() );
                                }
                            } );


                    config.getIsInitialStart().put( containerHost.getHostname(), false );
                    manager.saveConfig( config );
                    LOG.info( "Initial start of data node " + containerHost.getHostname() );
                }
                else
                {
                    containerHost.executeAsync(
                            new RequestBuilder( String.format( Commands.startCommand, config.getConfNodeDataFile() ) )
                                    .daemon(), new CommandCallback()
                            {
                                @Override
                                public void onResponse( final Response response, final CommandResult commandResult )
                                {
                                    trackerOperation.addLog( commandResult.getStdOut() );
                                }
                            } );
                }


                //start mysql servers if they are installed
                if ( config.getIsSqlInstalled().get( containerHost.getHostname() ) )
                {
                    trackerOperation
                            .addLog( String.format( "Starting MySQL Server %s ", containerHost.getHostname() ) );
                    containerHost.executeAsync( new RequestBuilder( Commands.startMySQLServer ), new CommandCallback()
                    {
                        @Override
                        public void onResponse( final Response response, final CommandResult commandResult )
                        {
                            trackerOperation.addLog( commandResult.getStdOut() );
                        }
                    } );
                }
                else
                {
                    trackerOperation.addLog( "Data node does not have installed MySQL Server API..." );
                }
            }
            catch ( ContainerHostNotFoundException | CommandException | ClusterException e )
            {
                e.printStackTrace();
            }
        }

        trackerOperation
                .addLogDone( String.format( "MySQL Cluster %s", config.getClusterName() + " has been started" ) );
    }


    @Override
    public void runOperationOnContainers( final ClusterOperationType clusterOperationType )
    {

    }


    @Override
    public void setupCluster()
    {
        trackerOperation.addLog( "Setting up cluster ..." );
        try
        {
            Environment env = manager.getEnvironmentManager().loadEnvironment( config.getEnvironmentId() );

            new ClusterConfig( trackerOperation, manager ).configureCluster( config, env );
            trackerOperation.addLogDone( String.format( "Cluster %s configured successfully", clusterName ) );
        }
        catch ( EnvironmentNotFoundException | ClusterConfigurationException e )
        {
            trackerOperation
                    .addLogFailed( String.format( "Failed to setup cluster %s : %s", clusterName, e.getMessage() ) );
        }
    }


    @Override
    public void destroyCluster()
    {
        MySQLClusterConfig config = manager.getCluster( clusterName );
        if ( config == null )
        {
            trackerOperation.addLogFailed(
                    String.format( "Cluster with name %s does not exist. Operation aborted", clusterName ) );
            return;
        }

        try
        {
            manager.getEnvironmentManager().loadEnvironment( config.getEnvironmentId() );
        }
        catch ( EnvironmentNotFoundException ex )
        {
            trackerOperation.addLogFailed( "Environment not found" );
            return;
        }
        try
        {
            manager.deleteConfig( config );
        }
        catch ( ClusterException e )
        {
            trackerOperation.addLogFailed( "Failed to delete cluster information from database." );
            return;
        }

        trackerOperation.addLogDone( "Cluster removed from database" );
    }
}