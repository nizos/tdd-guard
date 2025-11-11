package com.lightspeed.tddguard.junit5;

import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Stub implementation of TestDescriptor for testing purposes.
 * Provides minimal implementation needed for test creation without mocking.
 */
class TestDescriptorStub implements TestDescriptor {

    private final String uniqueId;
    private final String displayName;
    private final TestSource source;

    TestDescriptorStub(String uniqueId, String displayName, TestSource source) {
        this.uniqueId = uniqueId;
        this.displayName = displayName;
        this.source = source;
    }

    @Override
    public UniqueId getUniqueId() {
        return UniqueId.forEngine("junit-jupiter").append("test", uniqueId);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Set<TestTag> getTags() {
        return Collections.emptySet();
    }

    @Override
    public Optional<TestSource> getSource() {
        return Optional.ofNullable(source);
    }

    @Override
    public Optional<TestDescriptor> getParent() {
        return Optional.empty();
    }

    @Override
    public void setParent(TestDescriptor parent) {
        // Not needed for stub
    }

    @Override
    public Set<? extends TestDescriptor> getChildren() {
        return Collections.emptySet();
    }

    @Override
    public void addChild(TestDescriptor descriptor) {
        // Not needed for stub
    }

    @Override
    public void removeChild(TestDescriptor descriptor) {
        // Not needed for stub
    }

    @Override
    public void removeFromHierarchy() {
        // Not needed for stub
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    @Override
    public Optional<TestDescriptor> findByUniqueId(UniqueId uniqueId) {
        return Optional.empty();
    }

    @Override
    public void accept(Visitor visitor) {
        // Not needed for stub
    }
}
