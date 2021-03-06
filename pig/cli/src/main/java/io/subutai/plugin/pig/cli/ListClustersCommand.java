package io.subutai.plugin.pig.cli;


import java.util.List;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;

import io.subutai.plugin.pig.api.Pig;
import io.subutai.plugin.pig.api.PigConfig;


/**
 * sample command : pig:list-clusters
 */
@Command( scope = "pig", name = "list-clusters", description = "Lists Pig clusters" )
public class ListClustersCommand extends OsgiCommandSupport
{

    private Pig pigManager;


    public Pig getPigManager()
    {
        return pigManager;
    }


    public void setPigManager( Pig pigManager )
    {
        this.pigManager = pigManager;
    }


    protected Object doExecute()
    {
        List<PigConfig> configList = pigManager.getClusters();
        if ( !configList.isEmpty() )
        {
            for ( PigConfig config : configList )
            {
                System.out.println( config.getClusterName() );
            }
        }
        else
        {
            System.out.println( "No Pig cluster" );
        }

        return null;
    }
}
