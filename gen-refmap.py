#!/usr/bin/env python3
"""Resolve SRG (m_/f_) names for the @Mixin targets of this Forge branch.

Forge 1.20.1 production runs vanilla members in SRG names while mods are
compiled against official Mojang names. ForgeGradle 6 does not wire the Mixin
annotation processor, so instead of a refmap every mixin target in src/ is
written directly with its SRG name plus ``remap = false`` (regular code is
still reobfuscated by ForgeGradle automatically).

This script derives the SRG names from:
  - Mojang official mappings (client_mappings.txt + server_mappings.txt)
  - MCPConfig config/joined.tsrg (notch <-> searge)

Usage:
  gen-refmap.py <joined.tsrg> <client_mappings.txt> <server_mappings.txt> <out.json>

The output is a reference table (same shape as a Mixin refmap) used to fill
in / audit the SRG names in the @Mixin annotations. The TARGETS table below
must be kept in sync with those annotations. Run the game afterwards: Mixin
fails fast (defaultRequire=1) on any bad entry.
"""
import json
import re
import sys

# (mixin class, vanilla owner official, member official, desc official or None,
#  kind: "method" | "field" | "invoke-target", lookup owner override or None)
# "invoke-target" entries are @At(INVOKE) targets; "method"/"field" cover
# @Inject/@Redirect/@ModifyExpressionValue/@Overwrite/@Shadow.
# The lookup owner differs when the member is declared in a parent class
# (e.g. getOpacity lives in LightEngine, not BlockLightEngine).
TARGETS = [
    # --- server: me.tewek.lightengine.mixin ---
    ("me.tewek.lightengine.mixin.BlockStateBaseMixin",
     "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase",
     "getLightEmission", "()I", "method"),
    ("me.tewek.lightengine.mixin.BlockLightEngineMixin",
     "net.minecraft.world.level.lighting.BlockLightEngine",
     "propagateIncrease", "(JJI)V", "method"),
    ("me.tewek.lightengine.mixin.BlockLightEngineMixin",
     "net.minecraft.world.level.lighting.BlockLightEngine",
     "getOpacity",
     "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
     "invoke-target",
     "net.minecraft.world.level.lighting.LightEngine"),
    ("me.tewek.lightengine.mixin.BlockLightEngineMixin",
     "net.minecraft.world.level.lighting.BlockLightEngine",
     "getEmission",
     "(JLnet/minecraft/world/level/block/state/BlockState;)I", "method"),
    # Synthetic lambda body of propagateLightSources (mapped by Mojang too).
    ("me.tewek.lightengine.mixin.BlockLightEngineMixin",
     "net.minecraft.world.level.lighting.BlockLightEngine",
     "lambda$propagateLightSources$0",
     "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
     "method"),
    # NOTE: BlockState.getLightEmission(BlockGetter,BlockPos) is a Forge
    # interface default (never obfuscated) - resolves as-written, no entry.
    ("me.tewek.lightengine.mixin.LayerLightSectionStorageMixin",
     "net.minecraft.world.level.lighting.LayerLightSectionStorage",
     "getStoredLevel", "(J)I", "method"),
    ("me.tewek.lightengine.mixin.LayerLightSectionStorageMixin",
     "net.minecraft.world.level.lighting.LayerLightSectionStorage",
     "setStoredLevel", "(JI)V", "method"),
    ("me.tewek.lightengine.mixin.LayerLightSectionStorageMixin",
     "net.minecraft.world.level.lighting.LayerLightSectionStorage",
     "layer", None, "field"),
    ("me.tewek.lightengine.mixin.LayerLightSectionStorageMixin",
     "net.minecraft.world.level.lighting.LayerLightSectionStorage",
     "chunkSource", None, "field"),
    ("me.tewek.lightengine.mixin.LayerLightSectionStorageMixin",
     "net.minecraft.world.level.chunk.DataLayer",
     "set", "(IIII)V", "invoke-target"),
    ("me.tewek.lightengine.mixin.ChunkSerializerMixin",
     "net.minecraft.world.level.chunk.storage.ChunkSerializer",
     "write",
     "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;",
     "method"),
    ("me.tewek.lightengine.mixin.ChunkSerializerMixin",
     "net.minecraft.world.level.chunk.storage.ChunkSerializer",
     "read",
     "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/ai/village/poi/PoiManager;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/chunk/ProtoChunk;",
     "method"),
    ("me.tewek.lightengine.mixin.ChunkSendMixin",
     "net.minecraft.server.network.ServerGamePacketListenerImpl",
     "send", "(Lnet/minecraft/network/protocol/Packet;)V", "method"),
    ("me.tewek.lightengine.mixin.MinecraftServerMixin",
     "net.minecraft.server.MinecraftServer",
     "saveAllChunks", "(ZZZ)Z", "method"),
    ("me.tewek.lightengine.mixin.ThreadedLightCheckMixin",
     "net.minecraft.server.level.ThreadedLevelLightEngine",
     "checkBlock", "(Lnet/minecraft/core/BlockPos;)V", "method"),
    # --- server: QueueEntry overwrites ---
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "getFromLevel", "(J)I", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "withLevel", "(JI)J", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "shouldPropagateInDirection", "(JLnet/minecraft/core/Direction;)Z", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "withDirection", "(JLnet/minecraft/core/Direction;)J", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "withoutDirection", "(JLnet/minecraft/core/Direction;)J", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "isFromEmptyShape", "(J)Z", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "isIncreaseFromEmission", "(J)Z", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "decreaseAllDirections", "(I)J", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "decreaseSkipOneDirection", "(ILnet/minecraft/core/Direction;)J", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "increaseLightFromEmission", "(IZ)J", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "increaseSkipOneDirection", "(IZLnet/minecraft/core/Direction;)J", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "increaseOnlyOneDirection", "(IZLnet/minecraft/core/Direction;)J", "method"),
    ("me.tewek.lightengine.mixin.LightEngineQueueEntryMixin",
     "net.minecraft.world.level.lighting.LightEngine$QueueEntry",
     "increaseSkySourceInDirections", "(ZZZZZ)J", "method"),
    # --- client: me.tewek.lightengine.client.mixin ---
    ("me.tewek.lightengine.client.mixin.LevelRendererMixin",
     "net.minecraft.client.renderer.LevelRenderer",
     "getLightColor",
     "(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
     "method"),
    ("me.tewek.lightengine.client.mixin.PauseScreenMixin",
     "net.minecraft.client.gui.screens.PauseScreen",
     "init", "()V", "method"),
]

DESC_TYPE = re.compile(r"L([^;]+);|\[|([ZBCSIFDJV])")
PRIM_NAMES = {"Z": "boolean", "B": "byte", "C": "char", "S": "short",
              "I": "int", "J": "long", "F": "float", "D": "double"}


def desc_to_mojang_args(desc):
    """'(Lnet/minecraft/core/BlockPos;IZ)V' -> 'net.minecraft.core.BlockPos,int,boolean'."""
    args = desc[1:].split(")", 1)[0]
    out, i = [], 0
    toks = []
    for part in DESC_TYPE.finditer(args):
        toks.append(part.group(0))
    i = 0
    while i < len(toks):
        dims = 0
        while i < len(toks) and toks[i] == "[":
            dims += 1
            i += 1
        tok = toks[i]
        i += 1
        if tok in PRIM_NAMES:
            name = PRIM_NAMES[tok]
        else:
            name = tok[1:-1].replace("/", ".")
        out.append(name + "[]" * dims)
    return ",".join(out)


def notch_desc(official_desc, class_map):
    """Convert an official-typed method desc to notch-typed via class map."""
    def repl(m):
        cls, prim = m.group(1), m.group(2)
        if prim:
            return prim
        notch = class_map.get("L" + cls.replace("/", ".") + ";")
        if notch is None:
            raise KeyError("no notch class for L%s;" % cls)
        return notch

    def conv(sig):
        res = []
        for part in DESC_TYPE.finditer(sig):
            tok = part.group(0)
            if tok == "[":
                res.append("[")
            else:
                res.append(repl(part))
        return "".join(res)

    args, ret = official_desc[1:].split(")", 1)
    return "(" + conv(args) + ")" + conv(ret)


def parse_mojang(path, class_map, member_map):
    owner = None
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.startswith(" "):
                m = re.match(r"^(\S+) -> (\S+):$", line)
                if m:
                    owner = m.group(1)
                    class_map["L" + owner + ";"] = "L" + m.group(2) + ";"
            else:
                m = re.match(r"^\s+\S+ (\S+)\((.*)\) -> (\S+)$", line)
                if m and owner:
                    name, args, notch = m.group(1), m.group(2), m.group(3)
                    member_map[(owner, name, args)] = notch
                else:
                    m2 = re.match(r"^\s+\S+ (\S+) -> (\S+)$", line)
                    if m2 and owner:
                        member_map[(owner, m2.group(1), None)] = m2.group(2)


def parse_tsrg(path):
    """joined.tsrg (tsrg2, namespaces obf/srg/id) -> {(notchOwner,notchName,notchDesc|None): srgName}."""
    table = {}
    owner_notch = None
    with open(path, encoding="utf-8") as f:
        for raw in f:
            if raw.startswith("tsrg2"):
                continue
            if raw.startswith("\t") or raw.startswith(" "):
                parts = raw.split()
                if len(parts) == 4:
                    n_name, n_desc, srg, _id = parts
                    table[(owner_notch, n_name, n_desc)] = srg
                elif len(parts) == 3:
                    n_name, srg, _id = parts
                    table[(owner_notch, n_name, None)] = srg
            else:
                parts = raw.split()
                if len(parts) >= 2:
                    owner_notch = parts[0]
    return table


def main():
    joined, client_map, server_map, out = sys.argv[1:5]
    class_map, member_map = {}, {}
    parse_mojang(client_map, class_map, member_map)
    parse_mojang(server_map, class_map, member_map)
    srg = parse_tsrg(joined)

    # notch owner per official owner
    def notch_owner(official):
        key = "L" + official + ";"
        # inner classes use $ in Mojang files too
        if key not in class_map:
            raise KeyError("no notch owner for " + official)
        # tsrg owners are slash-separated; short notch names are unaffected
        return class_map[key][1:-1].replace(".", "/")

    mappings = {}
    missing = []
    for target in TARGETS:
        mixin, owner, name, desc, kind = target[:5]
        lookup = target[5] if len(target) > 5 else owner
        try:
            n_owner = notch_owner(lookup)
            if kind == "field":
                n_name = member_map.get((lookup, name, None))
                if n_name is None:
                    raise KeyError("no notch field %s.%s" % (lookup, name))
                srg_name = srg.get((n_owner, n_name, None))
                if srg_name is None:
                    raise KeyError("no srg field %s.%s" % (n_owner, n_name))
                value = "L%s;%s" % (owner.replace(".", "/"), srg_name)
                key = name
            else:
                n_desc = notch_desc(desc, class_map)
                # official member lookup needs Mojang-style args ('a.b.C,int')
                args_mojang = desc_to_mojang_args(desc)
                n_name = member_map.get((lookup, name, args_mojang))
                if n_name is None:
                    raise KeyError("no notch member %s.%s(%s)" % (lookup, name, args_mojang))
                srg_name = srg.get((n_owner, n_name, n_desc))
                if srg_name is None:
                    raise KeyError("no srg member %s.%s%s" % (n_owner, n_name, n_desc))
                owner_slash = owner.replace(".", "/")
                value = "L%s;%s%s" % (owner_slash, srg_name, desc)
                if kind == "invoke-target":
                    key = "L%s;%s%s" % (owner_slash, name, desc)
                else:
                    key = None  # two keys below
            entry = mappings.setdefault(mixin, {})
            if kind == "invoke-target":
                entry[key] = value
            else:
                if kind == "field":
                    entry[key] = value
                else:
                    entry[name] = value
                    entry[name + desc] = value
        except KeyError as ex:
            missing.append("%s %s.%s%s: %s" % (mixin, owner, name, desc or "", ex))

    if missing:
        print("MISSING %d mappings:" % len(missing))
        for m in missing:
            print("  " + m)
        sys.exit(1)

    total = sum(len(v) for v in mappings.values())
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"mappings": mappings}, f, indent=2)
        f.write("\n")
    print("wrote %s (%d mixins, %d entries)" % (out, len(mappings), total))


main()
