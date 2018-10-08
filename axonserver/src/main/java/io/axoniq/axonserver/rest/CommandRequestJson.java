package io.axoniq.axonserver.rest;

import io.axoniq.axonserver.ProcessingInstructionHelper;
import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.platform.KeepNames;
import io.axoniq.platform.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.validation.constraints.NotNull;

/**
 * Author: marc
 */
@KeepNames
public class CommandRequestJson {
    private String messageIdentifier;
    @NotNull(message = "'name' field cannot be missing")
    private String name;
    @NotNull(message = "'routingKey' field cannot be missing")
    private String routingKey;
    private long timestamp;
    private SerializedObjectJson payload;
    private MetaDataJson metaData = new MetaDataJson();

    public Command asCommand() {
        Command.Builder builder = Command.newBuilder()
                                         .setName(name)
                                         .setMessageIdentifier(StringUtils.getOrDefault(messageIdentifier,
                                                                                UUID.randomUUID().toString()))
                                         .setTimestamp(timestamp);
        if( payload != null) {
            builder.setPayload(payload.asSerializedObject());
        }

        return builder.putAllMetaData(metaData.asMetaDataValueMap())
                      .addAllProcessingInstructions(processingInstructions())
                      .build();
    }

    private Iterable<? extends ProcessingInstruction> processingInstructions() {
        List<ProcessingInstruction> processingInstructions = new ArrayList<>();
        processingInstructions.add(ProcessingInstructionHelper.routingKey(routingKey));
        return processingInstructions;
    }

    public String getMessageIdentifier() {
        return messageIdentifier;
    }

    public void setMessageIdentifier(String messageIdentifier) {
        this.messageIdentifier = messageIdentifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public SerializedObjectJson getPayload() {
        return payload;
    }

    public void setPayload(SerializedObjectJson payload) {
        this.payload = payload;
    }

    public MetaDataJson getMetaData() {
        return metaData;
    }

    public void setMetaData(MetaDataJson metaData) {
        this.metaData = metaData;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }
}