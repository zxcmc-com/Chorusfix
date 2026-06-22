package com.zxcmc.chorusfix.provider;

import com.zxcmc.chorusfix.ChorusFaceMask;
import java.util.List;
import org.bukkit.block.Block;

public interface CustomChorusBlockDetector {
  boolean isCustom(Block block, ChorusFaceMask mask);

  default boolean isHardCustom(Block block, ChorusFaceMask mask) {
    return isCustom(block, mask);
  }

  List<ProviderHookStatus> statuses();
}
