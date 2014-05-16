package nl.ciber.alfresco.repo.jscript.batchexecuter;

import akka.actor.ActorSystem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;

/**
 * A wrapper which initializes an ActorSystem on Alfresco startup.
 *
 * @author Bulat Yaminov
 */
public class ActorSystemWrapper extends AbstractLifecycleBean {

    private static final Log logger = LogFactory.getLog(ActorSystemWrapper.class);

    private ActorSystem actorSystem;

    @Override
    protected void onBootstrap(ApplicationEvent event) {
        logger.info("Starting Akka actor system 'BatchExecuter'");
        actorSystem = ActorSystem.create("BatchExecuter");
    }

    @Override
    protected void onShutdown(ApplicationEvent event) {
        if (actorSystem != null) {
            logger.info("Shutting down Akka actor system 'BatchExecuter'");
            actorSystem.shutdown();
        }
    }

    public ActorSystem getActorSystem() {
        return actorSystem;
    }
}
