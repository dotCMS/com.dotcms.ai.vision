package com.dotcms.ai.vision.workflow;

import com.dotcms.ai.app.AppKeys;
import com.dotcms.ai.vision.api.AIVisionAPI;
import com.dotcms.ai.vision.api.OpenAIVisionAPIImpl;
import com.dotcms.contenttype.model.field.Field;
import com.dotcms.security.apps.AppSecrets;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.db.LocalTransaction;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.workflows.actionlet.PublishContentActionlet;
import com.dotmarketing.portlets.workflows.actionlet.SaveContentActionlet;
import com.dotmarketing.portlets.workflows.actionlet.WorkFlowActionlet;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClass;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClassParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowActionFailureException;
import com.dotmarketing.portlets.workflows.model.WorkflowActionletParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowProcessor;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.model.User;
import io.vavr.control.Try;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OpenAIVisionAutoTagActionlet extends WorkFlowActionlet {

    private static final long serialVersionUID = 1L;
    AIVisionAPI aiVisionAPI = AIVisionAPI.instance.get();

    @Override
    public List<WorkflowActionletParameter> getParameters() {
        return List.of();
    }

    @Override
    public String getName() {
        return "Open AI - Tag Images";
    }

    @Override
    public String getHowTo() {
        return "This will attempt to Auto-tag and add alt tag descriptions to your images based on field variables.  You will need to make sure this runs before the save/publish Content actionlet.";
    }


    @Override
    public void executeAction(WorkflowProcessor processor, Map<String, WorkflowActionClassParameter> params)
            throws WorkflowActionFailureException {


        if(!shouldAutoTag(processor.getContentlet())){
            return;
        }
        AIVisionAPI aiVisionAPI = AIVisionAPI.instance.get();
        aiVisionAPI.addAltTextIfNeeded(processor.getContentlet());


        if(aiVisionAPI.addAltTextIfNeeded(processor.getContentlet()) && !processor.getAction().hasSaveActionlet()){
            processor.setContentlet(saveContentlet(processor.getContentlet(),  processor.getUser()));
        }

        aiVisionAPI.tagImageIfNeeded(processor.getContentlet());
    }


    boolean shouldAutoTag(Contentlet contentlet) {
        Host host = Try.of(() -> APILocator.getHostAPI().find(contentlet.getHost(), APILocator.systemUser(), true)).getOrNull();
        if (UtilMethods.isEmpty(() -> host.getIdentifier())) {
            return false;
        }
        Optional<AppSecrets> secrets = Try.of(
                        () -> APILocator.getAppsAPI().getSecrets(AppKeys.APP_KEY, true, host, APILocator.systemUser()))
                .getOrElse(Optional.empty());

        if (secrets.isEmpty()) {
            return false;
        }

        List<String> contentTypes=Arrays.asList(Try.of(()->secrets.get().getSecrets().get(AIVisionAPI.AI_VISION_AUTOTAG_CONTENTTYPES_KEY).getString().toLowerCase().split("[\\s,]+")).getOrElse(new String[0]));

        String contentType = contentlet.getContentType().variable().toLowerCase();
        if(contentTypes.contains(contentType)){
            return true;
        }

        Optional<Field> altField = contentlet.getContentType().fields().stream().filter(f -> f.fieldVariablesMap().containsKey(AIVisionAPI.AI_VISION_ALT_FIELD_VAR)).findFirst();
        Optional<Field> tagField = contentlet.getContentType().fields().stream().filter(f -> f.fieldVariablesMap().containsKey(AIVisionAPI.AI_VISION_TAG_FIELD_VAR)).findFirst();
        return altField.isPresent() || tagField.isPresent();

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

}
