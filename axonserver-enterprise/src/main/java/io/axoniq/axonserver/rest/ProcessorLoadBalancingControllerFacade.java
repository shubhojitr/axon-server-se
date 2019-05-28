package io.axoniq.axonserver.rest;


import io.axoniq.axonserver.enterprise.component.processor.balancing.jpa.ProcessorLoadBalancing;

import java.util.List;

/**
 * @author Marc Gathier
 */
public interface ProcessorLoadBalancingControllerFacade {

    void save(ProcessorLoadBalancing processorLoadBalancing);

    List<ProcessorLoadBalancing> findByContext(String context);
}