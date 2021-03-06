package io.subutai.plugin.solr.impl.handler;


import java.util.Iterator;

import com.google.common.base.Preconditions;

import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.environment.Environment;
import io.subutai.common.environment.EnvironmentNotFoundException;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.tracker.TrackerOperation;
import io.subutai.core.plugincommon.api.AbstractOperationHandler;
import io.subutai.core.plugincommon.api.NodeOperationType;
import io.subutai.plugin.solr.api.SolrClusterConfig;
import io.subutai.plugin.solr.impl.Commands;
import io.subutai.plugin.solr.impl.SolrImpl;


public class NodeOperationHandler extends AbstractOperationHandler<SolrImpl, SolrClusterConfig>
{

    private String clusterName;
    private String hostName;
    private NodeOperationType operationType;


    public NodeOperationHandler( final SolrImpl manager, final String clusterName, final String hostName,
                                 NodeOperationType operationType )
    {
        super( manager, manager.getCluster( clusterName ) );
        this.hostName = hostName;
        this.clusterName = clusterName;
        this.operationType = operationType;
        this.trackerOperation = manager.getTracker().createTrackerOperation( SolrClusterConfig.PRODUCT_KEY,
                String.format( "Checking %s cluster...", clusterName ) );
    }


    @Override
    public void run()
    {
        SolrClusterConfig config = manager.getCluster( clusterName );
        if ( config == null )
        {
            trackerOperation.addLogFailed( String.format( "Cluster with name %s does not exist", clusterName ) );
            return;
        }

        try
        {
            Environment environment = manager.getEnvironmentManager().loadEnvironment( config.getEnvironmentId() );
            Iterator iterator = environment.getContainerHosts().iterator();
            ContainerHost host = null;
            while ( iterator.hasNext() )
            {
                host = ( ContainerHost ) iterator.next();
                if ( host.getHostname().equals( hostName ) )
                {
                    break;
                }
            }

            if ( host == null )
            {
                trackerOperation.addLogFailed( String.format( "No Container with ID %s", hostName ) );
                return;
            }

            CommandResult result = null;
            switch ( operationType )
            {
                case START:
                    result = host.execute( new RequestBuilder( Commands.startCommand ) );
                    break;
                case STOP:
                    result = host.execute( new RequestBuilder( Commands.stopCommand ) );
                    break;
                case STATUS:
                    result = host.execute( new RequestBuilder( Commands.statusCommand ) );
                    break;
            }
            logStatusResults( trackerOperation, result );
        }
        catch ( CommandException | EnvironmentNotFoundException e )
        {
            trackerOperation.addLogFailed( String.format( "Command failed, %s", e.getMessage() ) );
        }
    }


    public static void logStatusResults( TrackerOperation po, CommandResult result )
    {
        Preconditions.checkNotNull( result );
        StringBuilder log = new StringBuilder();
        String status;
        if ( result.getExitCode() == 0 )
        {
            status = result.getStdOut();
        }
        else if ( result.getExitCode() == 768 )
        {
            status = "solr is not running";
        }
        else
        {
            status = result.getStdOut();
        }
        log.append( String.format( "%s", status ) );
        po.addLogDone( log.toString() );
    }
}