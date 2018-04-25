package com.veertu;

import com.veertu.utils.AnkaConstants;
import com.veertu.utils.AnkaCloudPropertiesProcesser;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class AnkaCloudClientFactory implements CloudClientFactory {


    @NotNull private final String cloudProfileSettings;
    @NotNull private final ServerPaths serverPaths;



    public AnkaCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                  @NotNull final PluginDescriptor pluginDescriptor,
                                  @NotNull final ServerPaths serverPaths) {
        cloudProfileSettings = pluginDescriptor.getPluginResourcesPath(AnkaConstants.PROFILE_SETTING_HTML);
        this.serverPaths = serverPaths;
        cloudRegistrar.registerCloudFactory(this);
    }

    @NotNull
    @Override
    public CloudClientEx createNewClient(@NotNull CloudState cloudState, @NotNull CloudClientParameters cloudClientParameters) {
        String host = cloudClientParameters.getParameter(AnkaConstants.HOST_NAME);
        String port = cloudClientParameters.getParameter(AnkaConstants.PORT);
        String sshUser = cloudClientParameters.getParameter(AnkaConstants.SSH_USER);
        String sshPassword = cloudClientParameters.getParameter(AnkaConstants.SSH_PASSWORD);
        String imageName = cloudClientParameters.getParameter(AnkaConstants.IMAGE_NAME);
        String imageTag = cloudClientParameters.getParameter(AnkaConstants.IMAGE_TAG);
        String agentPath = cloudClientParameters.getParameter(AnkaConstants.AGENT_PATH);
        String serverUrl = cloudClientParameters.getParameter(AnkaConstants.OPTIONAL_SERVER_URL);
        Integer agentPoolId = null;
        String agentPoolIdVal = cloudClientParameters.getParameter(AnkaConstants.AGENT_POOL_ID);
        try {
            if (!agentPoolIdVal.isEmpty()) {
                agentPoolId = Integer.valueOf(agentPoolIdVal);
            }
        } catch (NullPointerException | NumberFormatException e) {
            // do nothing - agentPoolId will just be null...
        }
        Integer maxInstances = 1;
        try {
            String maxInstancesString = cloudClientParameters.getParameter(AnkaConstants.MAX_INSTANCES);
            maxInstances = Integer.valueOf(maxInstancesString);
        } catch (NullPointerException | NumberFormatException e) {
            // do nothing - maxInstances will just be 1...
        }

        String profileId = cloudClientParameters.getParameter("system.cloud.profile_id");
        AnkaCloudConnector connector = new AnkaCloudConnector(host, port, imageName, imageTag, sshUser, sshPassword, agentPath, serverUrl, agentPoolId, profileId, maxInstances);
        return new AnkaCloudClientEx(connector);

    }

    @NotNull
    @Override
    public String getCloudCode() {
        return AnkaConstants.CLOUD_CODE;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return AnkaConstants.CLOUD_DISPLAY_NAME;
    }

    @Nullable
    @Override
    public String getEditProfileUrl() {
        return cloudProfileSettings;
    }

    @NotNull
    @Override
    public Map<String, String> getInitialParameterValues() {
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put(AnkaConstants.HOST_NAME, "18.236.6.136");
        parameters.put(AnkaConstants.PORT, "8090");
        parameters.put(AnkaConstants.SSH_PASSWORD, "admin");
        parameters.put(AnkaConstants.SSH_USER, "anka");
        parameters.put(AnkaConstants.AGENT_PATH, "/Users/anka/buildAgent");
        return parameters;
    }

    @NotNull
    @Override
    public PropertiesProcessor getPropertiesProcessor() {
        return new AnkaCloudPropertiesProcesser();
    }

    @Override
    public boolean canBeAgentOfType(@NotNull AgentDescription agentDescription) {
        Map<String, String> availableParameters = agentDescription.getAvailableParameters();
        return availableParameters.get(AnkaConstants.ENV_ANKA_CLOUD_KEY).equals(AnkaConstants.ENV_ANKA_CLOUD_VALUE);
    }
}
