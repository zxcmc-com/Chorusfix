package com.zxcmc.chorusfix;

import com.zxcmc.chorusfix.provider.CustomChorusBlockDetector;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ChorusUpdateService {
  private static final BlockFace[] DIRECT_NEIGHBORS = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
  };
  private static final int[][] REQUIRED_OFFSETS = {
    {0, 0, -1},
    {1, 0, 0},
    {0, 0, 1},
    {-1, 0, 0},
    {0, 1, 0},
    {0, -1, 0},
    {0, -1, -1},
    {1, -1, 0},
    {0, -1, 1},
    {-1, -1, 0}
  };

  private final Plugin plugin;
  private final Logger logger;
  private final ChorusBlockBreaker breakExecutor;
  private final Queue<QueuedBlock> queue = new ArrayDeque<>();
  private final Set<LocationKey> pending = new HashSet<>();
  private ChorusfixConfig config;
  private CustomChorusBlockDetector detector;
  private PaperChorusPlantUpdates.RuntimeState paperState;
  private BukkitTask task;
  private long processedTotal;
  private long brokenTotal;
  private long correctedTotal;
  private long skippedTotal;

  public ChorusUpdateService(
      Plugin plugin,
      ChorusfixConfig config,
      CustomChorusBlockDetector detector,
      PaperChorusPlantUpdates.RuntimeState paperState) {
    this(plugin, config, detector, paperState, new ChorusBreakExecutor());
  }

  ChorusUpdateService(
      Plugin plugin,
      ChorusfixConfig config,
      CustomChorusBlockDetector detector,
      PaperChorusPlantUpdates.RuntimeState paperState,
      ChorusBlockBreaker breakExecutor) {
    this.plugin = plugin;
    this.logger = plugin.getLogger();
    this.breakExecutor = breakExecutor;
    this.config = config;
    this.detector = detector;
    this.paperState = paperState;
  }

  public void update(
      ChorusfixConfig config,
      CustomChorusBlockDetector detector,
      PaperChorusPlantUpdates.RuntimeState paperState) {
    this.config = config;
    this.detector = detector;
    this.paperState = paperState;
    if (!active()) {
      clearQueue();
    }
  }

  public boolean active() {
    return config.enabled() && (!config.onlyWhenPaperDisabled() || paperState.disabledTrue());
  }

  public void enqueueNeighborhoodNextTick(Block origin) {
    Bukkit.getScheduler().runTask(plugin, () -> enqueueNeighborhood(origin, 0));
  }

  public void enqueueNeighborhood(Block origin, int depth) {
    if (!active()) {
      return;
    }
    QueueBudget budget = new QueueBudget(config.maxPerEvent());
    tryEnqueue(origin, depth, budget);
    for (BlockFace face : DIRECT_NEIGHBORS) {
      Optional<Block> relative =
          loadedRelative(origin, face.getModX(), face.getModY(), face.getModZ());
      relative.ifPresent(block -> tryEnqueue(block, depth, budget));
    }
  }

  public void enqueueIfChorusRelatedNextTick(Block origin) {
    if (!active() || !isChorusRelated(origin)) {
      return;
    }
    enqueueNeighborhoodNextTick(origin);
  }

  public Status status() {
    return new Status(
        active(), queue.size(), processedTotal, brokenTotal, correctedTotal, skippedTotal);
  }

  public void shutdown() {
    if (task != null) {
      task.cancel();
      task = null;
    }
    clearQueue();
  }

  private boolean isChorusRelated(Block origin) {
    if (isChorusMaterial(origin.getType())) {
      return true;
    }
    for (BlockFace face : DIRECT_NEIGHBORS) {
      Optional<Block> relative =
          loadedRelative(origin, face.getModX(), face.getModY(), face.getModZ());
      if (relative.isPresent() && isChorusMaterial(relative.get().getType())) {
        return true;
      }
    }
    return false;
  }

  private void tryEnqueue(Block block, int depth, QueueBudget budget) {
    if (depth > config.maxChainDepth()
        || !budget.tryConsume()
        || !isChorusMaterial(block.getType())) {
      return;
    }
    LocationKey key = LocationKey.from(block);
    if (!pending.add(key)) {
      return;
    }
    queue.add(new QueuedBlock(key, depth));
    schedule();
  }

  private void schedule() {
    if (task != null || !active()) {
      return;
    }
    task = Bukkit.getScheduler().runTask(plugin, this::processTick);
  }

  private void processTick() {
    task = null;
    if (!active()) {
      clearQueue();
      return;
    }
    int budget = config.maxPerTick();
    while (budget-- > 0 && !queue.isEmpty()) {
      QueuedBlock queued = queue.poll();
      pending.remove(queued.key());
      Optional<Block> block = queued.key().resolve();
      if (block.isEmpty()) {
        continue;
      }
      processBlock(block.get(), queued.depth());
    }
    if (!queue.isEmpty()) {
      schedule();
    }
  }

  private void processBlock(Block block, int depth) {
    if (!loadedNeighborhood(block)) {
      skippedTotal++;
      return;
    }
    ChorusMaterial material = ChorusMaterial.fromMaterial(block.getType());
    if (!material.isChorusBlock()) {
      return;
    }

    ChorusFaceMask mask = null;
    if (material == ChorusMaterial.CHORUS_PLANT) {
      mask = ChorusFaceMask.fromBlockData(block.getBlockData()).orElse(null);
      if (mask == null) {
        skippedTotal++;
        return;
      }
    }

    boolean providerClaimed = detector.isCustom(block, mask);
    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(material, mask, config.ignoredMasks(), providerClaimed);
    if (!decision.process()) {
      skippedTotal++;
      return;
    }

    ChorusWorldView world = relativeWorld(block);
    if (material == ChorusMaterial.CHORUS_PLANT) {
      processPlant(block, mask, world, depth);
    } else if (!VanillaChorusRules.canFlowerSurvive(world)) {
      breakAndCascade(block, depth);
    }
    processedTotal++;
  }

  private void processPlant(Block block, ChorusFaceMask current, ChorusWorldView world, int depth) {
    if (!VanillaChorusRules.canPlantSurvive(world)) {
      breakAndCascade(block, depth);
      return;
    }
    ChorusFaceMask expected = VanillaChorusRules.recomputePlantMask(world);
    if (expected.equals(current)) {
      return;
    }
    BlockData nextData = block.getBlockData().clone();
    if (!(nextData instanceof MultipleFacing facing)) {
      skippedTotal++;
      return;
    }
    expected.applyTo(facing);
    block.setBlockData(nextData, false);
    correctedTotal++;
    enqueueNeighborhood(block, depth + 1);
  }

  private void breakAndCascade(Block block, int depth) {
    if (config.debug()) {
      logger.info("Breaking unsupported chorus block at " + block.getLocation());
    }
    if (breakExecutor.breakNaturallyWithFeedback(block)) {
      brokenTotal++;
      enqueueNeighborhood(block, depth + 1);
    }
  }

  private ChorusWorldView relativeWorld(Block origin) {
    return (dx, dy, dz) -> {
      Optional<Block> block = loadedRelative(origin, dx, dy, dz);
      return block
          .map(value -> ChorusMaterial.fromMaterial(value.getType()))
          .orElse(ChorusMaterial.OTHER);
    };
  }

  private boolean loadedNeighborhood(Block origin) {
    for (int[] offset : REQUIRED_OFFSETS) {
      if (loadedRelative(origin, offset[0], offset[1], offset[2]).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private Optional<Block> loadedRelative(Block origin, int dx, int dy, int dz) {
    int x = origin.getX() + dx;
    int y = origin.getY() + dy;
    int z = origin.getZ() + dz;
    World world = origin.getWorld();
    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
      return Optional.empty();
    }
    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
      return Optional.empty();
    }
    return Optional.of(origin.getRelative(dx, dy, dz));
  }

  private static boolean isChorusMaterial(Material material) {
    return material == Material.CHORUS_PLANT || material == Material.CHORUS_FLOWER;
  }

  private void clearQueue() {
    queue.clear();
    pending.clear();
  }

  public record Status(
      boolean active,
      int queued,
      long processedTotal,
      long brokenTotal,
      long correctedTotal,
      long skippedTotal) {}

  private record QueuedBlock(LocationKey key, int depth) {}

  private record LocationKey(UUID worldId, int x, int y, int z) {
    static LocationKey from(Block block) {
      return new LocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    Optional<Block> resolve() {
      World world = Bukkit.getWorld(worldId);
      if (world == null || y < world.getMinHeight() || y >= world.getMaxHeight()) {
        return Optional.empty();
      }
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return Optional.empty();
      }
      return Optional.of(world.getBlockAt(x, y, z));
    }
  }
}
