package com.zxcmc.chorusfix.provider;

import com.zxcmc.chorusfix.ChorusFaceMask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.block.Block;

public final class CompositeCustomChorusBlockDetector implements CustomChorusBlockDetector {
  private final List<CustomChorusBlockDetector> detectors;
  private final List<ProviderHookStatus> statuses;

  public CompositeCustomChorusBlockDetector(List<CustomChorusBlockDetector> detectors) {
    this.detectors = List.copyOf(detectors);
    List<ProviderHookStatus> collected = new ArrayList<>();
    for (CustomChorusBlockDetector detector : detectors) {
      collected.addAll(detector.statuses());
    }
    this.statuses = Collections.unmodifiableList(collected);
  }

  @Override
  public boolean isCustom(Block block, ChorusFaceMask mask) {
    for (CustomChorusBlockDetector detector : detectors) {
      if (detector.isCustom(block, mask)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<ProviderHookStatus> statuses() {
    return statuses;
  }
}
