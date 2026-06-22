package com.zxcmc.chorusfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.chorusfix.provider.CustomChorusBlockDetector;
import java.util.List;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

final class ProviderHooksTest {
  @Test
  void compositeSkipsWhenAnyProviderClaimsBlock() {
    CustomChorusBlockDetector first = detector(false);
    CustomChorusBlockDetector second = detector(true);
    CustomChorusBlockDetector composite =
        new com.zxcmc.chorusfix.provider.CompositeCustomChorusBlockDetector(List.of(first, second));

    assertTrue(composite.isCustom(null, ChorusFaceMask.parse("down")));
  }

  @Test
  void compositeAllowsWhenNoProviderClaimsBlock() {
    CustomChorusBlockDetector composite =
        new com.zxcmc.chorusfix.provider.CompositeCustomChorusBlockDetector(
            List.of(detector(false), detector(false)));

    assertFalse(composite.isCustom(null, ChorusFaceMask.parse("down")));
  }

  @Test
  void compositeKeepsHardClaimsSeparateFromHeuristicClaims() {
    CustomChorusBlockDetector heuristicOnly =
        new CustomChorusBlockDetector() {
          @Override
          public boolean isCustom(Block block, ChorusFaceMask mask) {
            return true;
          }

          @Override
          public boolean isHardCustom(Block block, ChorusFaceMask mask) {
            return false;
          }

          @Override
          public List<com.zxcmc.chorusfix.provider.ProviderHookStatus> statuses() {
            return List.of();
          }
        };
    CustomChorusBlockDetector composite =
        new com.zxcmc.chorusfix.provider.CompositeCustomChorusBlockDetector(List.of(heuristicOnly));

    assertTrue(composite.isCustom(null, ChorusFaceMask.ALL));
    assertFalse(composite.isHardCustom(null, ChorusFaceMask.ALL));
  }

  private static CustomChorusBlockDetector detector(boolean result) {
    return new CustomChorusBlockDetector() {
      @Override
      public boolean isCustom(Block block, ChorusFaceMask mask) {
        return result;
      }

      @Override
      public List<com.zxcmc.chorusfix.provider.ProviderHookStatus> statuses() {
        return List.of();
      }
    };
  }
}
