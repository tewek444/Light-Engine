package me.tewek.lightengine;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.tewek.lightengine.lightfield.LightFieldStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Repairs orphaned light: cells brighter than any live source can explain.
 *
 * <p>Background: if a chunk is saved mid-flood (quit right after breaking a
 * strong source), the persisted halo has no source on load, and a vanilla
 * 15-plateau can no longer be cleared by a 15-wave, so it stays forever.
 * This command finds such cells and re-checks each through the vanilla
 * engine (its own {@code setStoredLevel(0)} clears both stores; the spawned
 * waves die on already-zeroed cells, so live halos are untouched).</p>
 *
 * <p>Soundness: light decays by at least 1 per block in every direction, so
 * a cell with value {@code v} is explainable only by a source with emission
 * {@code E} within Manhattan distance {@code E - v}. Everything else is an
 * orphan by definition. Usage: {@code /lerepair [<pos>] [<radius 8..64>]}.</p>
 */
public final class LeRepairCommand {
    private LeRepairCommand() {
    }

    private record Source(BlockPos pos, int reach) {
    }

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("lerepair")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> repair(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition()), 40))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> repair(ctx.getSource(),
                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"), 40))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 64))
                                .executes(ctx -> repair(ctx.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                        IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static int repair(CommandSourceStack src, BlockPos center, int r) {
        ServerLevel level;
        try {
            level = src.getLevel();
        } catch (Exception ex) {
            return 0;
        }
        int orphans = repair(level, center, r);
        String msg = "LEREPAIR center=" + center.toShortString() + " r=" + r
                + " orphansRechecked=" + orphans + " (details in server log)";
        LightEngine.LOGGER.warn(msg);
        try {
            if (src.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    /**
     * A cell is workable when its chunk is loaded and its Y is inside the
     * padded light range. Deliberately NOT {@code Level.isLoaded} (which
     * rejects anything outside the build height): orphaned light survives
     * exactly there, and vanilla reads below the floor are safe (air/zero).
     */
    private static boolean isWorkable(ServerLevel level, BlockPos pos) {
        try {
            int y = pos.getY();
            if (y < me.tewek.lightengine.lightfield.LightSectionRange.minY(level)
                    || y > me.tewek.lightengine.lightfield.LightSectionRange.maxY(level)) {
                return false;
            }
            return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Package-visible core used by headless verification. Returns orphans rechecked. */
    static int repair(ServerLevel level, BlockPos center, int r) {
        // Padded light range on purpose (see LightSectionRange): orphaned
        // light survives exactly outside the buildable world, so the repair
        // must reach one section below/above it. Vanilla reads below
        // minBuildHeight are safe (air / zero light when nothing is stored).
        int minY = me.tewek.lightengine.lightfield.LightSectionRange.minY(level);
        int maxY = me.tewek.lightengine.lightfield.LightSectionRange.maxY(level);

        // Pass 1: live sources (profiled blocks whose conditions currently hold).
        List<Source> sources = new ArrayList<>();
        int sr = r + 16;
        for (int bx = center.getX() - sr; bx <= center.getX() + sr; bx++) {
            for (int by = Math.max(minY, center.getY() - sr); by <= Math.min(maxY, center.getY() + sr); by++) {
                for (int bz = center.getZ() - sr; bz <= center.getZ() + sr; bz++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    if (!isWorkable(level, pos)) {
                        continue;
                    }
                    BlockState state;
                    try {
                        state = level.getBlockState(pos);
                    } catch (Exception ignored) {
                        continue;
                    }
                    int emission = 0;
                    try {
                        emission = LightProfileRegistry.emissionForState(state, 0);
                    } catch (Exception ignored) {
                    }
                    if (emission > 0) {
                        sources.add(new Source(pos.immutable(), emission));
                    }
                }
            }
        }

        // Pass 2: lit cells no source can explain.
        LightFieldStore store = LightFieldStore.forLevel(level);
        int scanned = 0;
        int orphans = 0;
        int skippedUnloaded = 0;
        for (int bx = center.getX() - r; bx <= center.getX() + r; bx++) {
            for (int by = Math.max(minY, center.getY() - r); by <= Math.min(maxY, center.getY() + r); by++) {
                for (int bz = center.getZ() - r; bz <= center.getZ() + r; bz++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    if (!isWorkable(level, pos)) {
                        skippedUnloaded++;
                        continue;
                    }
                    long s = SectionPos.asLong(bx >> 4, by >> 4, bz >> 4);
                    int ext = 0;
                    try {
                        ext = store.getCell(s, SectionPos.sectionRelative(bx),
                                SectionPos.sectionRelative(by), SectionPos.sectionRelative(bz));
                    } catch (Exception ignored) {
                    }
                    int vanilla = 0;
                    try {
                        vanilla = level.getBrightness(LightLayer.BLOCK, pos);
                    } catch (Exception ignored) {
                    }
                    int v = Math.max(ext, vanilla);
                    if (v <= 0) {
                        continue;
                    }
                    scanned++;
                    boolean explained = false;
                    for (Source source : sources) {
                        int d = Math.abs(bx - source.pos().getX())
                                + Math.abs(by - source.pos().getY())
                                + Math.abs(bz - source.pos().getZ());
                        if (d <= source.reach() - v) {
                            explained = true;
                            break;
                        }
                    }
                    if (!explained) {
                        try {
                            level.getLightEngine().checkBlock(pos);
                            orphans++;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        LightEngine.LOGGER.warn(
                "LEREPAIR center={} r={} sources={} litCells={} orphansRechecked={} skippedUnloaded={} (effect lands in a few seconds)",
                center, r, sources.size(), scanned, orphans, skippedUnloaded);
        return orphans;
    }
}
