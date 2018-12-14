package io.axoniq.axonserver.localstorage.file;

import io.axoniq.axonserver.grpc.internal.TransactionWithToken;
import io.axoniq.axonserver.localstorage.TransactionInformation;

import java.util.Iterator;

/**
 * Author: marc
 */
public interface TransactionIterator extends Iterator<TransactionWithToken>, AutoCloseable {

    @Override
    default void close() {
        // Default no action, defined here to avoid IOException
    }

    TransactionInformation currentTransaction();
}
