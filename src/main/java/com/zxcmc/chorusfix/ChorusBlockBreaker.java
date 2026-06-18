package com.zxcmc.chorusfix;

import org.bukkit.block.Block;

@FunctionalInterface
interface ChorusBlockBreaker {
  boolean breakNaturallyWithFeedback(Block block);
}
