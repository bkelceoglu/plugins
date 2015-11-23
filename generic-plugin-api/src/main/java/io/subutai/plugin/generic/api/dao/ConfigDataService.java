package io.subutai.plugin.generic.api.dao;


import java.util.List;

import io.subutai.plugin.generic.api.model.Operation;
import io.subutai.plugin.generic.api.model.Profile;


public interface ConfigDataService
{
    void saveProfile( String profileName );

    List<Profile> getAllProfiles();

    void saveOperation( Long profileId, String operationName, String commandName, String cwd, String timeout,
                        Boolean daemon, Boolean fromFile );

    List<Operation> getOperations( Long profileId );

    boolean isOperationRegistered( String operationName );

    Operation getOperationByName( String operationName );

    void updateOperation( Long operationId, String commandValue, String cwdValue, String timeoutValue,
                          Boolean daemonValue, Boolean fromFile, String operationName );

    void deleteOperation( Long operationId );

    void deleteProfile( Long profileId );

    void deleteOperations( Long profileId );
}
