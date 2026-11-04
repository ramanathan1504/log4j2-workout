package org.apache.logging.probe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import org.apache.logging.log4j.message.Message;

/**
 * A harmless class that is not on Log4j's deserialization allow-list. It records that it was instantiated and does
 * nothing else, so a probe can tell whether a filter let it through without running any gadget.
 */
public final class Canary implements Message, Serializable {

    private static final long serialVersionUID = 1L;

    static volatile boolean instantiated;

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        instantiated = true;
    }

    @Override
    public String getFormattedMessage() {
        return "canary";
    }

    @Override
    public Object[] getParameters() {
        return null;
    }

    @Override
    public Throwable getThrowable() {
        return null;
    }
}
