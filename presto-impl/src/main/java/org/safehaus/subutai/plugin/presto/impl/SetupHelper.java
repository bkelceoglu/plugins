package org.safehaus.subutai.plugin.presto.impl;


import java.util.Set;

import org.safehaus.subutai.common.command.CommandException;
import org.safehaus.subutai.common.command.CommandResult;
import org.safehaus.subutai.common.environment.ContainerHostNotFoundException;
import org.safehaus.subutai.common.environment.Environment;
import org.safehaus.subutai.common.peer.ContainerHost;
import org.safehaus.subutai.common.tracker.TrackerOperation;
import org.safehaus.subutai.plugin.common.api.ClusterSetupException;
import org.safehaus.subutai.plugin.presto.api.PrestoClusterConfig;

import com.google.common.base.Preconditions;


public class SetupHelper
{

    final TrackerOperation po;
    final PrestoImpl manager;
    final PrestoClusterConfig config;


    public SetupHelper( TrackerOperation po, PrestoImpl manager, PrestoClusterConfig config )
    {

        Preconditions.checkNotNull( config, "Presto cluster config is null" );
        Preconditions.checkNotNull( po, "Product operation tracker is null" );
        Preconditions.checkNotNull( manager, "Presto manager is null" );

        this.po = po;
        this.manager = manager;
        this.config = config;
    }


    void checkConnected( Environment environment ) throws ClusterSetupException
    {

        if ( !getCoordinatorHost( environment ).isConnected() )
        {
            throw new ClusterSetupException( "Coordinator node is not connected" );
        }

        try
        {
            for ( ContainerHost host : environment.getContainerHostsByIds( config.getWorkers() ) )
            {
                if ( !host.isConnected() )
                {
                    throw new ClusterSetupException( "Not all worker nodes are connected" );
                }
            }
        }
        catch ( ContainerHostNotFoundException e )
        {
            e.printStackTrace();
        }
    }


    public void configureAsCoordinator( ContainerHost host, Environment environment )
            throws ClusterSetupException, CommandException
    {
        po.addLog( "Configuring coordinator..." );

        CommandResult result =
                host.execute( manager.getCommands().getSetCoordinatorCommand( getCoordinatorHost( environment ) ) );
        processResult( host, result );
    }


    public void configureAsWorker( Set<ContainerHost> workerHosts ) throws ClusterSetupException
    {
        po.addLog( "Configuring worker(s)..." );

        for ( ContainerHost host : workerHosts )
        {
            try
            {
                CommandResult result = host.execute( manager.getCommands().getSetWorkerCommand( host ) );
                processResult( host, result );
            }
            catch ( CommandException e )
            {
                throw new ClusterSetupException(
                        String.format( "Failed to configure workers Presto node(s): %s", e.getMessage() ) );
            }
        }
    }


    public void startNodes( final Set<ContainerHost> set ) throws ClusterSetupException
    {
        po.addLog( "Starting Presto node(s)..." );
        for ( ContainerHost host : set )
        {
            try
            {
                CommandResult result = host.execute( manager.getCommands().getStartCommand() );
                processResult( host, result );
            }
            catch ( CommandException e )
            {
                throw new ClusterSetupException(
                        String.format( "Failed to start Presto node(s): %s", e.getMessage() ) );
            }
            po.addLogDone( "Presto node(s) started successfully\nDone" );
        }
    }


    public void processResult( ContainerHost host, CommandResult result ) throws ClusterSetupException
    {

        if ( !result.hasSucceeded() )
        {
            throw new ClusterSetupException( String.format( "Error on container %s: %s", host.getHostname(),
                    result.hasCompleted() ? result.getStdErr() : "Command timed out" ) );
        }
    }


    public ContainerHost getCoordinatorHost( Environment environment )
    {
        try
        {
            return environment.getContainerHostById( config.getCoordinatorNode() );
        }
        catch ( ContainerHostNotFoundException e )
        {
            e.printStackTrace();
        }
        return null;
    }
}
