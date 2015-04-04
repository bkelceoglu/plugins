/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.safehaus.subutai.plugin.mongodb.impl;


import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.safehaus.subutai.common.environment.Environment;
import org.safehaus.subutai.common.mdc.SubutaiExecutors;
import org.safehaus.subutai.common.peer.ContainerHost;
import org.safehaus.subutai.common.tracker.TrackerOperation;
import org.safehaus.subutai.core.env.api.EnvironmentEventListener;
import org.safehaus.subutai.core.env.api.EnvironmentManager;
import org.safehaus.subutai.core.metric.api.Monitor;
import org.safehaus.subutai.core.metric.api.MonitorException;
import org.safehaus.subutai.core.metric.api.MonitoringSettings;
import org.safehaus.subutai.core.peer.api.PeerManager;
import org.safehaus.subutai.core.tracker.api.Tracker;
import org.safehaus.subutai.plugin.common.PluginDAO;
import org.safehaus.subutai.plugin.common.api.AbstractOperationHandler;
import org.safehaus.subutai.plugin.common.api.ClusterException;
import org.safehaus.subutai.plugin.common.api.ClusterOperationType;
import org.safehaus.subutai.plugin.common.api.ClusterSetupStrategy;
import org.safehaus.subutai.plugin.common.api.NodeOperationType;

import org.safehaus.subutai.plugin.mongodb.api.Mongo;
import org.safehaus.subutai.plugin.mongodb.api.MongoClusterConfig;
import org.safehaus.subutai.plugin.mongodb.api.NodeType;
import org.safehaus.subutai.plugin.mongodb.impl.alert.MongoAlertListener;
import org.safehaus.subutai.plugin.mongodb.impl.common.Commands;
import org.safehaus.subutai.plugin.mongodb.impl.handler.ClusterOperationHandler;
import org.safehaus.subutai.plugin.mongodb.impl.handler.NodeOperationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


/**
 * Implementation of Mongo interface. Implements all backend logic for mongo cluster management
 */

public class MongoImpl implements Mongo, EnvironmentEventListener
{

    private static final Logger LOG = LoggerFactory.getLogger( MongoImpl.class.getName() );
    private Tracker tracker;
    private EnvironmentManager environmentManager;
    private ExecutorService executor;
    private Commands commands;
    private PluginDAO pluginDAO;
    private PeerManager peerManager;
    private Gson GSON = new GsonBuilder().serializeNulls().excludeFieldsWithoutExposeAnnotation().create();
    private Monitor monitor;
    private MonitoringSettings alertSettings = new MonitoringSettings().withIntervalBetweenAlertsInMin( 45 );
    private MongoAlertListener mongoAlertListener;


    public MongoImpl( Monitor monitor )
    {
        this.monitor = monitor;
        this.mongoAlertListener = new MongoAlertListener( this );
        this.monitor.addAlertListener( mongoAlertListener );
    }


    public Commands getCommands()
    {
        return commands;
    }


    public void setCommands( final Commands commands )
    {
        this.commands = commands;
    }


    public PluginDAO getPluginDAO()
    {
        return pluginDAO;
    }


    public EnvironmentManager getEnvironmentManager()
    {
        return environmentManager;
    }


    public PeerManager getPeerManager()
    {
        return peerManager;
    }


    public void setPeerManager( final PeerManager peerManager )
    {
        this.peerManager = peerManager;
    }


    public void setEnvironmentManager( final EnvironmentManager environmentManager )
    {
        this.environmentManager = environmentManager;
    }


    public Tracker getTracker()
    {
        return tracker;
    }


    public void setTracker( final Tracker tracker )
    {
        this.tracker = tracker;
    }


    public void setExecutor( final ExecutorService executor )
    {
        this.executor = executor;
    }


    public void init()
    {
        try
        {
            this.pluginDAO = new PluginDAO( null );
        }
        catch ( SQLException e )
        {
            LOG.error( e.getMessage(), e );
        }

        executor = SubutaiExecutors.newSingleThreadExecutor();
    }


    public void destroy()
    {
        executor.shutdown();
    }


    public UUID installCluster( MongoClusterConfig config )
    {
        AbstractOperationHandler operationHandler =
                new ClusterOperationHandler( this, config, ClusterOperationType.INSTALL, null );
        executor.execute( operationHandler );
        return operationHandler.getTrackerId();
    }


    public UUID uninstallCluster( final String clusterName )
    {
        MongoClusterConfig config = getCluster( clusterName );
        AbstractOperationHandler operationHandler =
                new ClusterOperationHandler( this, config, ClusterOperationType.REMOVE, null );
        executor.execute( operationHandler );
        return operationHandler.getTrackerId();
    }


    public List<MongoClusterConfig> getClusters()
    {
        return pluginDAO.getInfo( MongoClusterConfig.PRODUCT_KEY, MongoClusterConfig.class );
    }


    @Override
    public MongoClusterConfig getCluster( String clusterName )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( clusterName ), "Cluster name is null or empty" );
        return pluginDAO.getInfo( MongoClusterConfig.PRODUCT_KEY, clusterName, MongoClusterConfig.class );
    }


    @Override
    public UUID addNode( final String clusterName, final String agentHostName )
    {
        throw new UnsupportedOperationException( "Unsupported operation exception." );
    }


    public UUID addNode( final String clusterName, final NodeType nodeType )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( clusterName ), "Cluster name is null or empty" );
        AbstractOperationHandler operationHandler = new ClusterOperationHandler( this,
                getCluster( clusterName ), ClusterOperationType.ADD,nodeType );
        executor.execute( operationHandler );
        return operationHandler.getTrackerId();
    }


    public UUID destroyNode( final String clusterName, final String lxcHostname, final NodeType nodeType )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( clusterName ), "Cluster name is null or empty" );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( lxcHostname ), "Lxc hostname is null or empty" );
        AbstractOperationHandler operationHandler = new NodeOperationHandler( this, clusterName, lxcHostname,
                nodeType, NodeOperationType.DESTROY );
        executor.execute( operationHandler );
        return operationHandler.getTrackerId();
    }


    public UUID startNode( final String clusterName, final String lxcHostname, NodeType nodeType )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( clusterName ), "Cluster name is null or empty" );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( lxcHostname ), "Lxc hostname is null or empty" );
        AbstractOperationHandler operationHandler = new NodeOperationHandler( this, clusterName, lxcHostname,
                nodeType, NodeOperationType.START );
        executor.execute( operationHandler );
        return operationHandler.getTrackerId();
    }


    public UUID stopNode( final String clusterName, final String lxcHostname, NodeType nodeType )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( clusterName ), "Cluster name is null or empty" );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( lxcHostname ), "Lxc hostname is null or empty" );
        AbstractOperationHandler operationHandler = new NodeOperationHandler( this, clusterName, lxcHostname,
                nodeType, NodeOperationType.STOP );
        executor.execute( operationHandler );
        return operationHandler.getTrackerId();
    }


    @Override
    public UUID startAllNodes( final String clusterName )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( clusterName ), "Cluster name is null or empty" );
        AbstractOperationHandler operationHandler = new ClusterOperationHandler( this,
                getCluster( clusterName ), ClusterOperationType.START_ALL, null );
        executor.execute( operationHandler );
        return operationHandler.getTrackerId();
    }


    @Override
    public UUID stopAllNodes( final String clusterName )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( clusterName ), "Cluster name is null or empty" );
        AbstractOperationHandler operationHandler = new ClusterOperationHandler( this,
                getCluster( clusterName ), ClusterOperationType.STOP_ALL, null );
        executor.execute( operationHandler );
        return operationHandler.getTrackerId();
    }


    public UUID checkNode( final String clusterName, final String lxcHostname, NodeType nodeType )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( clusterName ), "Cluster name is null or empty" );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( lxcHostname ), "Lxc hostname is null or empty" );
        AbstractOperationHandler operationHandler = new NodeOperationHandler( this, clusterName, lxcHostname,
                nodeType, NodeOperationType.STATUS );
        executor.execute( operationHandler );
        return operationHandler.getTrackerId();
    }


    @Override
    public ClusterSetupStrategy getClusterSetupStrategy( final Environment environment, final MongoClusterConfig config,
                                                         final TrackerOperation po )
    {
        Preconditions.checkNotNull( environment, "Environment is null" );
        Preconditions.checkNotNull( config, "Mongo cluster config is null" );
        Preconditions.checkNotNull( po, "Product operation is null" );

        return new MongoDbSetupStrategy( environment, config, po, this );
    }


    public void subscribeToAlerts( Environment environment ) throws MonitorException
    {
        getMonitor().startMonitoring( mongoAlertListener, environment, alertSettings );
    }


    public void subscribeToAlerts( ContainerHost host ) throws MonitorException
    {
        getMonitor().activateMonitoring( host, alertSettings );
    }


    public void unsubscribeFromAlerts( final Environment environment ) throws MonitorException
    {
        getMonitor().stopMonitoring( mongoAlertListener, environment );
    }


    @Override
    public MongoClusterConfig newMongoClusterConfigInstance()
    {
        return new MongoClusterConfig();
    }


    @Override
    public void saveConfig( final MongoClusterConfig config ) throws ClusterException
    {
        Preconditions.checkNotNull( config );

        if ( !getPluginDAO().saveInfo( MongoClusterConfig.PRODUCT_KEY, config.getClusterName(), config ) )
        {
            throw new ClusterException( "Could not save cluster info" );
        }
    }


    @Override
    public void deleteConfig( final MongoClusterConfig config ) throws ClusterException
    {
        Preconditions.checkNotNull( config );

        if ( !getPluginDAO().deleteInfo( MongoClusterConfig.PRODUCT_KEY, config.getClusterName() ) )
        {
            throw new ClusterException( "Could not delete cluster info" );
        }
    }


    public Monitor getMonitor()
    {
        return monitor;
    }


    public MonitoringSettings getAlertSettings()
    {
        return alertSettings;
    }


    public void setAlertSettings( final MonitoringSettings alertSettings )
    {
        this.alertSettings = alertSettings;
    }


    @Override
    public void onEnvironmentCreated( final Environment environment )
    {
        LOG.info( "Environment created." );
    }


    @Override
    public void onEnvironmentGrown( final Environment environment, final Set<ContainerHost> set )
    {
        LOG.info( "Environment grown" );
    }


    @Override
    public void onContainerDestroyed( final Environment environment, final UUID containerHostId )
    {
//        List<String> clusterDataList = pluginDAO.getInfo( MongoClusterConfig.PRODUCT_KEY );
//        for ( final String clusterData : clusterDataList )
//        {
//            MongoClusterConfig clusterConfig = GSON.fromJson( clusterData, MongoClusterConfig.class );
//            if ( clusterConfig.getEnvironmentId().equals( environment.getId() ) )
//            {
//                clusterConfig.removeNode( containerHostId );
//                Gson GSON = new GsonBuilder().serializeNulls().excludeFieldsWithoutExposeAnnotation().create();
//                String json = GSON.toJson( clusterConfig );
//                getPluginDAO().saveInfo( MongoClusterConfig.PRODUCT_KEY, clusterConfig.getClusterName(), json );
//                LOG.info( String.format( "Container host: %s destroyed in cluster: %s", containerHostId,
//                        clusterConfig.getClusterName() ) );
//            }
//        }

        //        List<MongoClusterConfig> clusterConfigs = getClusters();
        //        for ( final MongoClusterConfig clusterConfig : clusterConfigs )
        //        {
        //            if ( clusterConfig.getEnvironmentId().equals( environment.getId() ) )
        //            {
        //                if ( clusterConfig.getAllNodeIds().contains( containerHostId ) )
        //                {
        //                    clusterConfig.removeNode( containerHostId );
        //                    Gson GSON = new GsonBuilder().serializeNulls().excludeFieldsWithoutExposeAnnotation()
        // .create();
        //                    String json = GSON.toJson( clusterConfig.prepare() );
        //                    getPluginDAO().saveInfo( MongoClusterConfig.PRODUCT_KEY, clusterConfig.getClusterName()
        // , json );
        //                    LOG.info( String.format( "Container host: %s destroyed in cluster: %s", containerHostId,
        //                            clusterConfig.getClusterName() ) );
        //                }
        //            }
        //        }
    }


    @Override
    public void onEnvironmentDestroyed( final UUID environmentId )
    {
        List<String> clusterDataList = pluginDAO.getInfo( MongoClusterConfig.PRODUCT_KEY );
        for ( final String clusterData : clusterDataList )
        {
            MongoClusterConfig clusterConfig = GSON.fromJson( clusterData, MongoClusterConfig.class );
            if ( clusterConfig.getEnvironmentId().equals( environmentId ) )
            {
                try
                {
                    deleteConfig( clusterConfig );
                    LOG.info( String.format( "Mongo cluster: %s destroyed", clusterConfig.getClusterName() ) );
                }
                catch ( ClusterException e )
                {
                    e.printStackTrace();
                }
            }
        }
    }
}
