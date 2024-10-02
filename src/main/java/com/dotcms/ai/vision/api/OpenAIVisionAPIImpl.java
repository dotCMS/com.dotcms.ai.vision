package com.dotcms.ai.vision.api;

import com.dotcms.ai.app.AppKeys;
import com.dotcms.ai.util.VelocityContextFactory;
import com.dotcms.ai.vision.listener.OpenAIImageTaggingContentListener;
import com.dotcms.contenttype.model.field.BinaryField;
import com.dotcms.contenttype.model.field.Field;
import com.dotcms.contenttype.model.field.TagField;
import com.dotcms.rendering.velocity.util.VelocityUtil;
import com.dotcms.security.apps.AppSecrets;
import com.dotcms.security.apps.Secret;
import com.dotcms.storage.model.Metadata;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.portlets.contentlet.business.exporter.ImageFilterExporter;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.json.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.liferay.portal.model.User;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.velocity.context.Context;

public class OpenAIVisionAPIImpl implements AIVisionAPI {


    static final String[] AI_VISION_ALT_TEXT_OPTIONS = {AI_VISION_ALT_TEXT_VARIBLE, "alt", "description"};
    static final String AI_VISION_PROMPT_DEFAULT =
            "Generate appropriate alt text and keywords that describe this image.  Return your response as a valid json object with the properties `"
                    + AI_VISION_ALT_TEXT_VARIBLE + "` and `" + AI_VISION_TAG_FIELD
                    + "` where `" + AI_VISION_TAG_FIELD + "` is an array of the keywords.";
    static final String TAGGED_BY_DOTAI = "dot:taggedByDotAI";
    static final String TAG_AND_ALT_PROMPT_TEMPLATE = "{  \"model\": \"${visionModel}\",\n"
            + "  \"messages\": [\n"
            + "    {\n"
            + "      \"role\": \"user\",\n"
            + "      \"content\": [\n"
            + "        {\n"
            + "          \"type\": \"text\",\n"
            + "          \"text\": \"${visionPrompt}\"\n"
            + "        },\n"
            + "        {\n"
            + "          \"type\": \"image_url\",\n"
            + "          \"image_url\": {\n"
            + "            \"url\": \"data:image/webp;base64,${base64Image}\"\n"
            + "          }\n"
            + "        }\n"
            + "      ]\n"
            + "    }\n"
            + "  ],\n"
            + "  \"max_tokens\": ${maxTokens} }";
    static final ImageFilterExporter IMAGE_FILTER_EXPORTER = new ImageFilterExporter();
    static final Cache<String, Tuple2<String, List<String>>> promptCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();
    final Map<String, String[]> imageResizeParameters = Map.of(
            "resize_maxw", new String[]{"500"},
            "resize_maxh", new String[]{"500"},
            "webp_q", new String[]{"85"}
    );
    Map<String, Secret> getSecrets(Contentlet contentlet) {
        return getSecrets(contentlet.getHost());
    }

    Map<String, Secret> getSecrets(String hostId) {
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

    boolean shouldProcessTags(Contentlet contentlet, Field binaryField) {

        Optional<Field> tagFieldOpt = contentlet.getContentType().fields(TagField.class).stream().findFirst();

        Optional<File> fileToProcess = getFileToProcess(contentlet, binaryField.variable());

        if (tagFieldOpt.isEmpty()) {
            return false;
        }

        boolean alreadyTagged = Try.of(() -> contentlet.getStringProperty(AI_VISION_TAG_FIELD))
                .map(tags -> tags.contains(TAGGED_BY_DOTAI))
                .getOrElse(false);

        //If the contentlet is already tagged by this AI, then we should not process it again
        if (tagFieldOpt.isEmpty() || alreadyTagged) {
            return false;
        }

        //If there is no image to process, then we should not process it
        if (fileToProcess.isEmpty() || fileToProcess.get().length() < 100 || !UtilMethods.isImage(
                fileToProcess.get().getName())) {
            return false;
        }



        return !getSecrets(contentlet).isEmpty();
    }

    boolean shouldProcessAltText(Contentlet contentlet, Field binaryField, Field altTextField) {

        if (UtilMethods.isSet(contentlet.getStringProperty(altTextField.variable()))) {
            return false;
        }

        Optional<File> fileToProcess = getFileToProcess(contentlet, binaryField.variable());
        //If there is no image to process, then we should not process it
        if (fileToProcess.isEmpty() || fileToProcess.get().length() < 100 || !UtilMethods.isImage(
                fileToProcess.get().getName())) {
            return false;
        }

        return !getSecrets(contentlet).isEmpty();
    }

    Optional<Field> getBinaryFieldToProcess(Contentlet contentlet) {

        return contentlet.getContentType()
                .fields(BinaryField.class)
                .stream()
                .findFirst();
    }

    @Override
    public boolean tagImageIfNeeded(Contentlet contentlet) {
        Optional<Field> binaryField = getBinaryFieldToProcess(contentlet);
        return binaryField.filter(field -> tagImageIfNeeded(contentlet, field)).isPresent();
    }

    @Override
    public boolean tagImageIfNeeded(Contentlet contentlet, Field binaryField) {
        if (!shouldProcessTags(contentlet,binaryField)) {
            return false;
        }

        Optional<Tuple2<String, List<String>>> altAndTags = readImageTagsAndDescription(contentlet, binaryField);

        if (altAndTags.isEmpty()) {
            return false;
        }

        saveTags(contentlet, altAndTags.get()._2);
        return true;

    }

    @Override
    public boolean addAltTextIfNeeded(Contentlet contentlet) {
        Optional<Field> binaryField = getBinaryFieldToProcess(contentlet);
        Optional<Field> altTextField = getAltTextField(contentlet);
        if (binaryField.isEmpty() || altTextField.isEmpty()) {
            return false;
        }
        if(UtilMethods.isSet(()->contentlet.getStringProperty(altTextField.get().variable()))){
            return false;
        }

        return addAltTextIfNeeded(contentlet, binaryField.get(), altTextField.get());
    }

    @Override
    public boolean addAltTextIfNeeded(Contentlet contentlet, Field binaryField, Field altTextField) {

        Optional<Tuple2<String, List<String>>> altAndTags = readImageTagsAndDescription(contentlet, binaryField);

        if (altAndTags.isEmpty()) {
            return false;
        }

        Optional<Contentlet> contentToSave = setAltText(contentlet, altTextField, altAndTags.get()._1);
        return contentToSave.isPresent();
    }



    @Override
    public Optional<Tuple2<String, List<String>>> readImageTagsAndDescription(Contentlet contentlet,
            Field binaryField) {

        Optional<File> fileToProcess = getFileToProcess(contentlet, binaryField.variable());
        if(fileToProcess.isEmpty()){
            return Optional.empty();
        }

        Metadata meta = Try.of(() -> contentlet.getBinaryMetadata(binaryField.variable())).getOrNull();
        if (UtilMethods.isEmpty(() -> meta.getSha256())) {
            return Optional.empty();
        }

        return Optional.ofNullable(promptCache.get(meta.getSha256(), k -> {
            try {
                final Context ctx = VelocityContextFactory.getMockContext(contentlet, APILocator.systemUser());
                ctx.put("visionModel", getAiVisionModel(contentlet.getHost()));
                ctx.put("maxTokens", getAiVisionMaxTokens(contentlet.getHost()));
                ctx.put("visionPrompt", getAiVisionPrompt(contentlet.getHost()));
                ctx.put("base64Image", base64EncodeImage(fileToProcess.get()));

                final String parsedPrompt = VelocityUtil.eval(TAG_AND_ALT_PROMPT_TEMPLATE, ctx);
                Logger.debug(OpenAIImageTaggingContentListener.class.getCanonicalName(),
                        "rawJSON: " + parsedPrompt.toString());

                JSONObject parsedPromptJson = new JSONObject(parsedPrompt);
                Logger.debug(this.getClass(), "parsedPromptJson: " + parsedPromptJson.toString());


                final JSONObject openAIResponse = APILocator.getDotAIAPI()
                        .getCompletionsAPI()
                        .raw(parsedPromptJson, APILocator.systemUser().getUserId());

                Logger.debug(OpenAIImageTaggingContentListener.class.getName(),
                        "OpenAI Response: " + openAIResponse.toString());

                final JSONObject parsedResponse = parseAIResponse(openAIResponse);
                Logger.debug(OpenAIImageTaggingContentListener.class.getName(),
                        "parsedResponse: " + parsedResponse.toString());

                return Tuple.of(parsedResponse.getString(AI_VISION_ALT_TEXT_VARIBLE),
                        parsedResponse.getJSONArray(AI_VISION_TAG_FIELD));
            } catch (Exception e) {
                Logger.warnAndDebug(OpenAIImageTaggingContentListener.class.getCanonicalName(), e.getMessage(), e);
                return null;
            }


        }));
    }






    Optional<File> getFileToProcess(Contentlet contentlet, String fieldVariable) {

        return Try.of(() -> contentlet.getBinary(fieldVariable)).toJavaOptional();
    }




    JSONObject parseAIResponse(JSONObject response) {

        String aiJson = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

        // gets at the first json object
        while (aiJson.length() > 0 && !aiJson.startsWith("{")) {
            aiJson = aiJson.substring(1);
        }
        while (aiJson.length() > 0 && !aiJson.endsWith("}")) {
            aiJson = aiJson.substring(0, aiJson.length() - 1);
        }

        return new JSONObject(aiJson);

    }


    String base64EncodeImage(File imageFile) throws Exception {
        File transformedFile = IMAGE_FILTER_EXPORTER.exportContent(imageFile, new HashMap<>(this.imageResizeParameters))
                .getDataFile();

        Logger.debug(OpenAIImageTaggingContentListener.class.getCanonicalName(),
                "Transformed file: " + transformedFile.getAbsolutePath());

        return java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(transformedFile.toPath()));
    }


    String getAiVisionModel(String hostId) {

        if (UtilMethods.isSet(() -> getSecrets(hostId).get(AI_VISION_MODEL).getString())) {
            return getSecrets(hostId).get(AI_VISION_MODEL).getString();
        }
        return "gpt-4o";
    }

    String getAiVisionMaxTokens(String hostId) {
        if (UtilMethods.isSet(() -> getSecrets(hostId).get(AI_VISION_MAX_TOKENS).getString())) {
            return getSecrets(hostId).get(AI_VISION_MAX_TOKENS).getString();
        }
        return "500";
    }

    String getAiVisionPrompt(String hostId) {
        if (UtilMethods.isSet(() -> getSecrets(hostId).get(AI_VISION_PROMPT).getString())) {
            return getSecrets(hostId).get(AI_VISION_PROMPT).getString();
        }
        return AI_VISION_PROMPT_DEFAULT;
    }


    String[] getAltTextFields(String hostId) {
        if (UtilMethods.isSet(() -> getSecrets(hostId).get(AI_VISION_ALT_TEXT_OPTIONS).getString())) {
            return getSecrets(hostId).get(AI_VISION_ALT_TEXT_OPTIONS).getString().split(",");
        }
        return AI_VISION_ALT_TEXT_OPTIONS;


    }


    private void saveTags(Contentlet contentlet, List<String> tags) {
        Optional<Field> tagFieldOpt = contentlet.getContentType().fields(TagField.class).stream().findFirst();
        if (tagFieldOpt.isEmpty()) {
            return;
        }
        Try.run(() -> APILocator.getTagAPI()
                .addContentletTagInode(TAGGED_BY_DOTAI, contentlet.getInode(), contentlet.getHost(),
                        tagFieldOpt.get().variable())).getOrElseThrow(
                DotRuntimeException::new);

        for (final String tag : tags) {
            Try.run(() -> APILocator.getTagAPI().addContentletTagInode(tag, contentlet.getInode(), contentlet.getHost(),
                    tagFieldOpt.get().variable())).getOrElseThrow(
                    DotRuntimeException::new);
        }
    }

    private Optional<Contentlet> setAltText(Contentlet contentlet, Field altTextField, String altText) {
        if (UtilMethods.isEmpty(altText)) {
            return Optional.empty();
        }

        if (UtilMethods.isSet(() -> contentlet.getStringProperty(altTextField.variable()))) {
            return Optional.empty();
        }

        contentlet.setStringProperty(altTextField.variable(), altText);
        return Optional.of(contentlet);


    }

    Optional<Field> getAltTextField(Contentlet contentlet) {

        for (String fieldVariable : this.getAltTextFields(contentlet.getHost())) {
            if (contentlet.getContentType().fieldMap().keySet().contains(fieldVariable)) {
                return Optional.of(contentlet.getContentType().fieldMap().get(fieldVariable));
            }
        }

        return Optional.empty();

    }


}
