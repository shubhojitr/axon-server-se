/*
 * Copyright (c) 2017-2020 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.config;

import org.osgi.framework.Bundle;

/**
 * @author Marc Gathier
 */
public class BundleInfo {

    private final boolean latestVersion;
    private final String version;
    private final long id;
    private final String name;
    private final String location;

    public BundleInfo(Bundle bundle, boolean latestVersion) {
        this.version = String.valueOf(bundle.getVersion());
        this.id = bundle.getBundleId();
        this.name = bundle.getSymbolicName();
        this.location = bundle.getLocation();
        this.latestVersion = latestVersion;
    }

    public boolean isLatestVersion() {
        return latestVersion;
    }

    public String getVersion() {
        return version;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }
}
