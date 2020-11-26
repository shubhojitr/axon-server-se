/*
 * Copyright (c) 2017-2020 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.interceptor;

import io.axoniq.axonserver.extensions.ExtensionUnitOfWork;
import io.axoniq.axonserver.grpc.SerializedCommand;
import io.axoniq.axonserver.grpc.SerializedCommandResponse;

/**
 * @author Marc Gathier
 */
public interface CommandInterceptors {

    SerializedCommand commandRequest(SerializedCommand serializedCommand,
                                     ExtensionUnitOfWork extensionUnitOfWork);

    SerializedCommandResponse commandResponse(SerializedCommandResponse serializedResponse,
                                              ExtensionUnitOfWork extensionUnitOfWork);
}