package net.covers1624.wt.forge.gradle.data;

import net.covers1624.wt.api.data.ExtraData;
import net.covers1624.wt.event.VersionedClass;
import net.covers1624.wt.forge.gradle.ForgeGradleVersion;

/**
 * Created by covers1624 on 15/6/19.
 */
@VersionedClass (1)
public class ForgeGradlePluginData implements ExtraData {

    public String versionString;
    public ForgeGradleVersion version;
}
