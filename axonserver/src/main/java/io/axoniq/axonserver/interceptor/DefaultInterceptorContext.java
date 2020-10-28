/*
 * Copyright (c) 2017-2020 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.interceptor;

import io.axoniq.axonserver.extensions.interceptor.InterceptorContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides the context for intercepted requests. It contains information on the caller and the Axon Server context
 * of the request. The same context object is used for the whole interceptor chain for a request.
 *
 * @author Marc Gathier
 * @since 4.5
 */
public class DefaultInterceptorContext implements InterceptorContext {

    private final String context;
    private final Authentication principal;
    private final List<Runnable> compensatingActions = new LinkedList<>();
    private final Map<String, Object> details = new HashMap<>();

    /**
     * @param context   the Axon Server context for the request
     * @param principal the caller's information
     */
    public DefaultInterceptorContext(String context, Authentication principal) {
        this.context = context;
        this.principal = principal;
    }

    /**
     * Returns the Axon Server context for the request.
     *
     * @return the Axon Server context
     */
    @Override
    public String context() {
        return context;
    }

    /**
     * Returns the caller's information.
     *
     * @return the caller's information
     */
    @Override
    public String principal() {
        return principal.getName();
    }

    @Override
    public Set<String> principalRoles() {
        return principal.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
    }

    @Override
    public Map<String, String> principalMetaData() {
        return Collections.emptyMap();
    }

    /**
     * Registers an action to call when request execution fails, or any of the subsequent interceptors fail.
     *
     * @param compensatingAction runnable compensation action
     */
    @Override
    public void registerCompensatingAction(Runnable compensatingAction) {
        compensatingActions.add(0, compensatingAction);
    }

    /**
     * Execute all compensating actions. The last registered compensating action is executed first.
     */
    public void compensate() {
        compensatingActions.forEach(Runnable::run);
    }

    /**
     * Add data to the interceptor context to be used at a later point in the interceptor chain.
     *
     * @param key   a key for the data
     * @param value the value
     */
    @Override
    public void addDetails(String key, Object value) {
        details.put(key, value);
    }

    /**
     * Retrieves custom data from the interceptor context.
     *
     * @param key the key of the data
     * @return the value
     */
    @Override
    public Object getDetails(String key) {
        return details.get(key);
    }
}