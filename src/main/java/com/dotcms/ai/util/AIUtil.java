package com.dotcms.ai.util;

import com.dotcms.ai.app.AppKeys;
import com.dotcms.security.apps.AppSecrets;
import com.dotcms.security.apps.Secret;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import io.vavr.control.Try;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class AIUtil {

    public static Map<String, Secret> getSecrets(Contentlet contentlet) {
        return getSecrets(contentlet.getHost());
    }

    public static Map<String, Secret> getSecrets(String hostId) {
        Host host = Try.of(() -> APILocator.getHostAPI().find(hostId, APILocator.systemUser(), true)).getOrNull();
        if (UtilMethods.isEmpty(() -> host.getIdentifier())) {
            return Map.of();
        }
        Optional<AppSecrets> secrets = Try.of(
                        () -> APILocator.getAppsAPI().getSecrets(AppKeys.APP_KEY, true, host, APILocator.systemUser()))
                .getOrElse(Optional.empty());
        if (secrets.isEmpty()) {
            return Map.of();
        }

        return secrets.get().getSecrets();
    }

    private static final String PROPERTY_FILE_NAME = "plugin.properties";
    private static final Properties properties;
    static {
        properties = new Properties();
        try ( InputStream in = AIUtil.class.getResourceAsStream("/" + PROPERTY_FILE_NAME)){
            properties.load(in);
        } catch (Exception e) {
            Logger.warn(AIUtil.class,"Exception : Can't read " + PROPERTY_FILE_NAME + " : " + e.getMessage());

        }
    }



    public static String getProperty(String key, String defaultValue) {
        String x = properties.getProperty(key);
        return (x == null) ? defaultValue : x;
    }

    public static String getProperty(String key) {
        return getProperty(key, null);
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        return Try.of(()->Boolean.parseBoolean(properties.getProperty(key))).getOrElse(defaultValue);

    }



}
