/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.metric;

import java.util.Iterator;

import static java.util.stream.StreamSupport.stream;

/**
 * Created by Sara Pellegrini on 17/04/2018.
 * sara.pellegrini@gmail.com
 */
public class Metrics implements Iterable<ClusterMetric> {

    private String metricName;

    private MetricCollector target;

    public Metrics(String metricName, MetricCollector target) {
        this.metricName = metricName;
        this.target = target;
    }

    @Override
    public Iterator<ClusterMetric> iterator() {
        return stream(target.getAll().spliterator(), false)
                .filter(metric -> metric.getName().equals(metricName))
                .map(metric -> (ClusterMetric) new NodeMetric(metric))
                .iterator();
    }
}
