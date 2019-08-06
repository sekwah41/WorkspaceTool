package net.covers1624.wt.api.impl.dependency;

import net.covers1624.wt.api.dependency.SourceSetDependency;
import net.covers1624.wt.api.module.Module;

/**
 * Created by covers1624 on 30/6/19.
 */
public class SourceSetDependencyImpl extends AbstractDependency implements SourceSetDependency {

    private Module module;
    private String sourceSet;

    public SourceSetDependencyImpl() {
    }

    SourceSetDependencyImpl(SourceSetDependency other) {
        this();
        setModule(other.getModule());
        setSourceSet(other.getSourceSet());
    }

    @Override
    public SourceSetDependency setExport(boolean export) {
        super.setExport(export);
        return this;
    }

    @Override
    public Module getModule() {
        return module;
    }

    @Override
    public String getSourceSet() {
        return sourceSet;
    }

    @Override
    public SourceSetDependency setModule(Module module) {
        this.module = module;
        return this;
    }

    @Override
    public SourceSetDependency setSourceSet(String sourceSet) {
        this.sourceSet = sourceSet;
        return this;
    }

    @Override
    public SourceSetDependency copy() {
        return new SourceSetDependencyImpl(this);
    }
}