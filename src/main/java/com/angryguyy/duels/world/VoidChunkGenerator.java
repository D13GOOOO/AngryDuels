package com.angryguyy.duels.world;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Chunk generator that produces a completely empty world.
 *
 * <p>Every generation step that Bukkit exposes is disabled: no noise,
 * no surface, no caves, no decorations, no mobs, and no structures.
 * This results in a void world with nothing but sky, which is exactly
 * what a duel arena needs. Arenas are expected to be built manually or
 * spawned in through other means.</p>
 *
 * <p>Because every overriding method simply returns {@code false} or
 * does nothing, this generator is essentially free at runtime and can
 * be used for as many chunks as needed without performance concerns.</p>
 */
public class VoidChunkGenerator extends ChunkGenerator {

    /**
     * Generates nothing for the given chunk.
     *
     * <p>The method is intentionally empty: the {@code shouldGenerate*}
     * overrides already prevent Bukkit from running any of the other
     * generation steps, so this hook is effectively a no-op kept only
     * to satisfy the interface contract.</p>
     *
     * @param worldInfo metadata about the world being generated
     * @param random    random source provided by the server
     * @param chunkX    chunk X coordinate
     * @param chunkZ    chunk Z coordinate
     * @param chunkData mutable chunk data, left untouched
     */
    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
    }

    /**
     * Disables noise generation.
     *
     * @return always {@code false}
     */
    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    /**
     * Disables surface generation.
     *
     * @return always {@code false}
     */
    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    /**
     * Disables cave generation.
     *
     * @return always {@code false}
     */
    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    /**
     * Disables decoration generation.
     *
     * @return always {@code false}
     */
    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    /**
     * Disables mob generation.
     *
     * @return always {@code false}
     */
    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    /**
     * Disables structure generation.
     *
     * @return always {@code false}
     */
    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}