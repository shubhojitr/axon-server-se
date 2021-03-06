/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver;


import io.axoniq.axonserver.topology.Topology;

import java.util.Collections;
import java.util.Set;

/**
 * Interface containing operations for access control.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public interface AxonServerAccessController {
    String TOKEN_PARAM = "AxonIQ-Access-Token";
    String AXONDB_TOKEN_PARAM = "Access-Token";
    String CONTEXT_PARAM = "AxonIQ-Context";

    /**
     * Checks if a specific method is allowed in given {@code context} for application {@code token}.
     *
     * @param fullMethodName the method name
     * @param context        the context
     * @param token          the app's token
     * @return true if method is allowed
     */
    boolean allowed(String fullMethodName, String context, String token);

    /**
     * Does this Axon Server version support role based authentication.
     * @return true if this Axon Server version support role based authentication
     */
    boolean isRoleBasedAuthentication();

    /**
     * Returns the roles granted to the application identified by this token.
     * If token is unknown it throws an {@link io.axoniq.axonserver.exception.InvalidTokenException}.
     *
     * @param token the app's token
     * @return the roles granted to the application identified by this token
     */
    Set<String> getRoles(String token);

    /**
     * Retrieves all roles that include the specified operation.
     *
     * @param operation the operation to check
     * @return all roles that include the specified operation
     */
    default Set<String> rolesForOperation(String operation) {
        return Collections.emptySet();
    }

    /**
     * Returns the default context to authenticate against when the REST request does not specify a context.
     *
     * @return default context
     */
    default String defaultContextForRest() {
        return Topology.DEFAULT_CONTEXT;
    }

    /**
     * Returns (native SQL) query to retrieve username and password from the control database.
     * @return the query
     */
    default String usersByUsernameQuery() {
        return "select username,password, enabled from users where username=?";
    }

    /**
     * Returns (native SQL) query to retrieve the roles per user.
     * @return the query
     */
    default String authoritiesByUsernameQuery() {
        return "select username, role from user_roles where username=?";
    }
}
