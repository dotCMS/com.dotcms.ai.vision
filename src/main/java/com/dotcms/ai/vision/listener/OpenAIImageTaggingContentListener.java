package com.dotcms.ai.vision.listener;

import com.dotcms.ai.app.AppKeys;
import com.dotcms.ai.vision.Activator;
import com.dotcms.ai.vision.api.AIVisionAPI;
import com.dotcms.concurrent.DotConcurrentFactory;
import com.dotcms.concurrent.DotSubmitter;
import com.dotcms.content.elasticsearch.business.event.ContentletArchiveEvent;
import com.dotcms.content.elasticsearch.business.event.ContentletDeletedEvent;
import com.dotcms.content.elasticsearch.business.event.ContentletPublishEvent;
import com.dotcms.contenttype.model.field.Field;
import com.dotcms.security.apps.AppSecrets;
import com.dotcms.system.event.local.model.Subscriber;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.db.LocalTransaction;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.contentlet.model.ContentletListener;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.model.User;
import io.vavr.Lazy;
import io.vavr.control.Try;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;


public class OpenAIImageTaggingContentListener implements ContentletListener<Contentlet> {

    AIVisionAPI aiVisionAPI = AIVisionAPI.instance.get();





    @Override
    public String getId() {
        return this.getClass().getCanonicalName();
    }


    @Subscriber
    public void onPublish(final ContentletPublishEvent<Contentlet> contentletPublishEvent) {

        Contentlet contentlet = contentletPublishEvent.getContentlet();
        if (!aiVisionAPI.shouldAutoTag(contentlet)) {
            return;
        }





        if (contentletPublishEvent.isPublish()) {

            Activator.AIThreadPool.get().submit(() -> {
                try {
                    LocalTransaction.wrap(() -> {
                                try {
                                    boolean autoTagged = aiVisionAPI.tagImageIfNeeded(contentlet);
                                    boolean autoDescription = aiVisionAPI.addAltTextIfNeeded(contentlet);

                                    if (autoTagged || autoDescription) {
                                        saveContentlet(contentlet, APILocator.systemUser());
                                    }


                                } catch (Throwable e) {
                                    e.printStackTrace();
                                    Logger.warnAndDebug(this.getClass(), e);
                                }
                            }
                    );


                } catch (Throwable e) {
                    Logger.error(this, "Error tagging contentlet", e);
                }

                logEvent("onPublish - PublishEvent:true", contentlet);
            });
        } else {
            logEvent("onPublish - PublishEvent:false", contentlet);

        }
    }





    private Contentlet saveContentlet(Contentlet contentlet, User user) {

        try {
            contentlet.setProperty(Contentlet.WORKFLOW_IN_PROGRESS, Boolean.TRUE);
            contentlet.setProperty(Contentlet.SKIP_RELATIONSHIPS_VALIDATION, Boolean.TRUE);
            contentlet.setProperty(Contentlet.DONT_VALIDATE_ME, Boolean.TRUE);

            final boolean isPublished = APILocator.getVersionableAPI().isLive(contentlet);
            final Contentlet savedContent = APILocator.getContentletAPI().checkin(contentlet, user, false);
            if (isPublished) {
                savedContent.setProperty(Contentlet.WORKFLOW_IN_PROGRESS, Boolean.TRUE);
                savedContent.setProperty(Contentlet.SKIP_RELATIONSHIPS_VALIDATION, Boolean.TRUE);
                savedContent.setProperty(Contentlet.DONT_VALIDATE_ME, Boolean.TRUE);
            }
            return savedContent;
        } catch (Exception e) {
            throw new DotRuntimeException(e);
        }
    }

    void logEvent(String eventType, Contentlet contentlet) {
        //System.out.println(  "GOT " + eventType + " for content: " + contentlet.getTitle() + " id:" + contentlet.getIdentifier());
        Logger.info(OpenAIImageTaggingContentListener.class,
                "GOT " + eventType + " for content: " + contentlet.getTitle() + " id:" + contentlet.getIdentifier());
    }









}
