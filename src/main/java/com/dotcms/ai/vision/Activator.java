package com.dotcms.ai.vision;

import com.dotcms.ai.translation.workflow.OpenAITranslationActionlet;
import com.dotcms.ai.vision.listener.OpenAIImageTaggingContentListener;
import com.dotcms.ai.vision.workflow.OpenAIVisionAutoTagActionlet;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.portlets.workflows.actionlet.WorkFlowActionlet;
import io.vavr.Lazy;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import org.osgi.framework.BundleContext;

public class Activator extends GenericBundleActivator {

    private static final OpenAIImageTaggingContentListener LISTENER = new OpenAIImageTaggingContentListener();


    public static Lazy<ThreadPoolExecutor> AIThreadPool = Lazy.of(() ->
            new ThreadPoolExecutor(10, 10, 60L, java.util.concurrent.TimeUnit.SECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(10000),
                    new ThreadPoolExecutor.CallerRunsPolicy()));

    // Register the actionlets




    private final List<WorkFlowActionlet> actionlets = List.of(
            new OpenAIVisionAutoTagActionlet(),
            new OpenAITranslationActionlet()
    );


    public void start(BundleContext context) throws Exception {
        this.initializeServices(context);

        // Register Embedding Actionlet
        actionlets.forEach(a -> this.registerActionlet(context, a));

        // Add the Embedding Listener (this does nothing right now)
        subscribeEmbeddingsListener();



    }

    public void stop(BundleContext context) throws Exception {

        unsubscribeEmbeddingsListener();

        // unregistering the actionlets actually removes them and their config from the system
        //this.unregisterActionlets();
        AIThreadPool.get().shutdown();
    }

    private void unsubscribeEmbeddingsListener() {
        APILocator.getLocalSystemEventsAPI().unsubscribe(LISTENER);
    }


    private void subscribeEmbeddingsListener() {

        APILocator.getLocalSystemEventsAPI().subscribe(LISTENER);

    }


}
