package io.subutai.plugin.hipi.ui.wizard;


import java.util.UUID;
import java.util.concurrent.ExecutorService;

import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.ui.Window;

import io.subutai.core.tracker.api.Tracker;
import io.subutai.plugin.common.ui.ConfigView;
import io.subutai.plugin.hipi.api.Hipi;
import io.subutai.plugin.hipi.api.HipiConfig;
import io.subutai.server.ui.component.ProgressWindow;


public class VerificationStep extends Panel
{

    public VerificationStep( final Hipi hipi, final ExecutorService executorService, final Tracker tracker,
                             final Wizard wizard )
    {

        setSizeFull();

        GridLayout grid = new GridLayout( 1, 5 );
        grid.setSpacing( true );
        grid.setMargin( true );
        grid.setSizeFull();

        Label confirmationLbl = new Label( "<strong>Please verify the installation settings "
                + "(you may change them by clicking on Back button)</strong><br/>" );
        confirmationLbl.setContentMode( ContentMode.HTML );

        // Display config values

        final HipiConfig config = wizard.getConfig();
        ConfigView cfgView = new ConfigView( "Installation configuration" );
        cfgView.addStringCfg( "Hadoop cluster name", config.getHadoopClusterName() );


        for ( String nodeId : wizard.getConfig().getNodes() )
        {
            cfgView.addStringCfg( "Node to install", nodeId + "" );
        }


        // Install button

        Button install = new Button( "Install" );
        install.setId( "hipiInstall" );
        install.addStyleName( "default" );
        install.addClickListener( new Button.ClickListener()
        {
            @Override
            public void buttonClick( Button.ClickEvent clickEvent )
            {

                UUID trackId = hipi.installCluster( config );

                ProgressWindow window = new ProgressWindow( executorService, tracker, trackId, HipiConfig.PRODUCT_KEY );
                window.getWindow().addCloseListener( new Window.CloseListener()
                {
                    @Override
                    public void windowClose( Window.CloseEvent closeEvent )
                    {
                        wizard.init();
                    }
                } );
                getUI().addWindow( window.getWindow() );
            }
        } );

        Button back = new Button( "Back" );
        back.setId( "hipiVerificationBack" );
        back.addStyleName( "default" );
        back.addClickListener( new Button.ClickListener()
        {
            @Override
            public void buttonClick( Button.ClickEvent clickEvent )
            {
                wizard.back();
            }
        } );

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.addComponent( back );
        buttons.addComponent( install );

        grid.addComponent( confirmationLbl, 0, 0 );

        grid.addComponent( cfgView.getCfgTable(), 0, 1, 0, 3 );

        grid.addComponent( buttons, 0, 4 );

        setContent( grid );
    }
}
