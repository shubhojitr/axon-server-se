package io.axoniq.axonserver.localstorage.file;

import java.io.Serializable;
import java.util.Objects;

/**
 * Author: marc
 */
public class PositionInfo implements Serializable, Comparable<PositionInfo> {

    private final int position;
    private final long aggregateSequenceNumber;

    public PositionInfo(int position, long aggregateSequenceNumber) {

        this.position = position;
        this.aggregateSequenceNumber = aggregateSequenceNumber;
    }

    public int getPosition() {
        return position;
    }

    public long getAggregateSequenceNumber() {
        return aggregateSequenceNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PositionInfo that = (PositionInfo) o;
        return aggregateSequenceNumber == that.aggregateSequenceNumber;
    }

    @Override
    public int hashCode() {

        return Objects.hash(aggregateSequenceNumber);
    }

    @Override
    public int compareTo(PositionInfo o) {
        return Long.compare(this.aggregateSequenceNumber, o.aggregateSequenceNumber);
    }
}