package com.dotcms.ai.vision.api;

import com.dotcms.contenttype.model.field.Field;
import com.dotcms.contenttype.model.type.DotAssetContentType;
import com.dotmarketing.portlets.contentlet.business.exporter.ImageFilterExporter;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.util.json.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vavr.Lazy;
import io.vavr.Tuple2;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public interface AIVisionAPI {

    static final String AI_VISION_AUTOTAG_CONTENTTYPES_KEY ="AI_VISION_AUTOTAG_CONTENTTYPES";

    static final String AI_VISION_MODEL = "AI_VISION_MODEL";

    static final String AI_VISION_MAX_TOKENS = "AI_VISION_MAX_TOKENS";

    static final String AI_VISION_PROMPT = "AI_VISION_PROMPT";

    static final String AI_VISION_ALT_FIELD_VAR = "dotAIDescriptionSrc";

    static final String AI_VISION_TAG_FIELD_VAR = "dotAITagSrc";


    static final Lazy<AIVisionAPI> instance = Lazy.of(OpenAIVisionAPIImpl::new);


    /**
     * returns true if the contentlet has a binary field that is an image and a tag field and has not been tagged yet
     * @param contentlet
     * @return
     */
    boolean tagImageIfNeeded(Contentlet contentlet);


    boolean addAltTextIfNeeded(Contentlet contentlet);

    boolean shouldAutoTag(Contentlet contentlet);

    /**
     * This method takes a file and returns a Tuple2 with the first element being the description and the second element
     * being the tags
     *
     * @param imageFile
     * @return
     */
    Optional<Tuple2<String, List<String>>> readImageTagsAndDescription(File imageFile);

    /**
     * This method takes a contentlet and a binary field and returns a Tuple2 with the first element being the description and the second element
     * being the tags.  The contentlet can
     * @param contentlet
     * @param binaryField
     * @return
     */
    Optional<Tuple2<String, List<String>>> readImageTagsAndDescription(Contentlet contentlet,
            Field binaryField);
}
