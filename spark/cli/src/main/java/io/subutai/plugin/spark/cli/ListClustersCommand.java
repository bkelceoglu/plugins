package io.subutai.plugin.spark.cli;


import java.util.List;

import io.subutai.plugin.spark.api.Spark;
import io.subutai.plugin.spark.api.SparkClusterConfig;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;


/**
 * Displays the last log entries
 */
@Command( scope = "spark", name = "list-clusters", description = "mydescription" )
public class ListClustersCommand extends OsgiCommandSupport
{

    private final Spark sparkManager;


    public ListClustersCommand( final Spark sparkManager )
    {
        this.sparkManager = sparkManager;
    }


    protected Object doExecute()
    {
        List<SparkClusterConfig> configList = sparkManager.getClusters();
        if ( !configList.isEmpty() )
        {
            for ( SparkClusterConfig config : configList )
            {
                System.out.println( config.getClusterName() );
            }
        }
        else
        {
            System.out.println( "No Spark cluster" );
        }

        return null;
    }
}
