/*
 * This file is part of NpcTraitPack for NucleusFramework, licensed under the MIT License (MIT).
 *
 * Copyright (c) JCThePants (www.jcwhatever.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.jcwhatever.nucleus.npc.traits.waypoints;

import com.jcwhatever.nucleus.npc.traits.NpcTraitPack;
import com.jcwhatever.nucleus.npc.traits.waypoints.plan.WaypointTimer;
import com.jcwhatever.nucleus.providers.npc.INpc;
import com.jcwhatever.nucleus.providers.npc.ai.INpcState;
import com.jcwhatever.nucleus.providers.npc.ai.goals.INpcGoal;
import com.jcwhatever.nucleus.providers.npc.ai.goals.INpcGoalAgent;
import com.jcwhatever.nucleus.providers.npc.events.NpcDespawnEvent;
import com.jcwhatever.nucleus.providers.npc.events.NpcDespawnEvent.NpcDespawnReason;
import com.jcwhatever.nucleus.providers.npc.events.NpcSpawnEvent;
import com.jcwhatever.nucleus.providers.npc.events.NpcSpawnEvent.NpcSpawnReason;
import com.jcwhatever.nucleus.providers.npc.traits.INpcTraits;
import com.jcwhatever.nucleus.providers.npc.traits.NpcRunnableTrait;
import com.jcwhatever.nucleus.providers.npc.traits.NpcTrait;
import com.jcwhatever.nucleus.providers.npc.traits.NpcTraitType;
import com.jcwhatever.nucleus.utils.PreCon;
import com.jcwhatever.nucleus.utils.Scheduler;
import com.jcwhatever.nucleus.utils.coords.ChunkUtils;
import com.jcwhatever.nucleus.utils.coords.Coords2Di;
import com.jcwhatever.nucleus.utils.coords.MutableCoords2Di;
import com.jcwhatever.nucleus.utils.observer.script.IScriptUpdateSubscriber;
import com.jcwhatever.nucleus.utils.observer.script.ScriptUpdateSubscriber;
import com.jcwhatever.nucleus.utils.observer.update.NamedUpdateAgents;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Pre-planned Waypoints that run even when the NPC is not spawned (due to chunk unload).
 *
 * <p>Plans each step of the path of waypoints using AStar. It uses the plan to calculate
 * where the NPC should be over time while it's despawned due to chunk unload. When the chunk is reloaded
 * or the NPC reaches a waypoint where the chunk is loaded, the NPC is moved to the point it should be at.</p>
 *
 * <p>Only works with ground based paths.</p>
 *
 * <p>Waypoint location pairs are cached for reuse among multiple NPC's.</p>
 *
 * <p>Recommended only for waypoints that are not dynamically generated, the waypoints are reused (not transient),
 * the path moves across many chunks and where it's important that the NPC continues pathing even when no
 * players are around to keep the path chunks loaded.</p>
 *
 * <p>It is also recommended to use the {@link com.jcwhatever.nucleus.npc.traits.SpigotActivatedTrait} in
 * conjunction with this trait.</p>
 */
public class PlannedWaypointsTrait  extends NpcTraitType {

    public static final String NAME = "PlannedWaypoints";

    /**
     * Constructor.
     */
    public PlannedWaypointsTrait() {
        super(NpcTraitPack.getPlugin(), NAME);
    }

    @Override
    protected NpcTrait createTrait(INpc npc) {
        return new PlannedWaypoints(this);
    }

    public static class PlannedWaypoints extends NpcRunnableTrait {

        private static final String META_AWAITING_RESPAWN = "__NpcTraitPack:PlannedWaypoints:AwaitingRespawn_";
        private static final MutableCoords2Di CHUNK_COORDS = new MutableCoords2Di();
        private static final Location NPC_LOCATION = new Location(null, 0, 0, 0);
        private static BukkitListener _listener;

        private final LinkedList<Location> _waypoints = new LinkedList<>();
        private final NamedUpdateAgents _subscriberAgents = new NamedUpdateAgents();
        private final Timer _timer = new Timer();

        private Location _current;
        private INpcGoal _waypointGoal;

        /**
         * Constructor.
         *
         * @param type The parent type that instantiated the trait.
         */
        PlannedWaypoints(NpcTraitType type) {
            super(type);

            if (_listener == null) {
                _listener = new BukkitListener();
                Bukkit.getPluginManager().registerEvents(_listener, NpcTraitPack.getPlugin());
            }
        }

        /**
         * Set the waypoints.
         *
         * @param locations  The locations to use as waypoints.
         *
         * @return  Self for chaining.
         */
        public PlannedWaypoints setWaypoints(Collection<Location> locations) {
            PreCon.notNull(locations);

            _waypoints.clear();
            _waypoints.addAll(locations);

            _timer.init(_waypoints);

            return this;
        }

        /**
         * Add a one time callback that is run when the NPC has finished
         * pathing to all of the way points.
         *
         * @param subscriber  The subscriber.
         *
         * @return  Self for chaining.
         */
        public PlannedWaypoints onFinish(IScriptUpdateSubscriber<INpc> subscriber) {
            PreCon.notNull(subscriber);

            _subscriberAgents.getAgent("onFinish")
                    .register(new ScriptUpdateSubscriber<>(subscriber));

            return this;
        }

        /**
         * Start pathing to the added waypoints.
         *
         * @return  Self for chaining.
         */
        public PlannedWaypoints start() {

            if (_waypointGoal == null)
                _waypointGoal = new WaypointGoal();

            getNpc().getGoals().add(1, _waypointGoal);

            return this;
        }

        /**
         * Stop pathing.
         *
         * @return  Self for chaining.
         */
        public PlannedWaypoints stop() {

            if (_waypointGoal == null)
                return this;

            getNpc().getGoals().remove(_waypointGoal);

            return this;
        }

        /**
         * Clear all way points.
         */
        public void clear() {
            _waypoints.clear();
            _current = null;
            setAwaitingRespawn(AwaitRespawnReason.NONE);
        }

        @Override
        protected void onAdd(INpc npc) {
            setInterval(10);
        }

        @Override
        protected void onRemove() {
            stop();
            clear();
            _subscriberAgents.disposeAgents();
            _timer.dispose();
            setAwaitingRespawn(AwaitRespawnReason.NONE);
        }

        @Override
        protected void onRun() {

            if (_current == null || _timer.isRunning())
                return;

            // Unload current chunk if surrounding chunks are not loaded.
            // This prevents the NPC from being slowed by spigot activation.

            Location npcLocation = getNpc().getLocation(NPC_LOCATION);

            if (!ChunkUtils.isNearbyChunksLoaded(npcLocation, 3)) {
                setAwaitingRespawn(AwaitRespawnReason.INVOKED);

                double speed = getNpc().getNavigator().getCurrentSettings().getSpeed();

                getNpc().despawn();
                _timer.start(_current, speed);
            }
        }

        /*
         * Determine if the trait timer is awaiting NPC respawn.
         */
        private boolean isAwaitingRespawn() {
            return getNpc().getMeta(META_AWAITING_RESPAWN) != null;
        }

        /*
         * Determine if the trait timer is awaiting NPC respawn due to chunk unload.
         */
        private boolean isAwaitingChunkReload() {
            return AwaitRespawnReason.CHUNK_UNLOAD.equals(getNpc().getMeta(META_AWAITING_RESPAWN));
        }

        /*
         * Set the respawn flag.
         */
        private void setAwaitingRespawn(AwaitRespawnReason reason) {
            getNpc().setMeta(META_AWAITING_RESPAWN, reason == AwaitRespawnReason.NONE ? null : reason);
        }

        /*
         * Respawn flags.
         */
        private enum AwaitRespawnReason {
            NONE,
            CHUNK_UNLOAD,
            INVOKED
        }

        /*
         * Waypoint timer to run waypoint path while NPC is despawned.
         */
        private class Timer extends WaypointTimer {

            @Override
            protected void onMove(Location current) {
                Coords2Di chunkCoords = ChunkUtils.getChunkCoords(current, CHUNK_COORDS);
                Location npcLocation = getNpc().getLocation(NPC_LOCATION);

                // make sure enough chunks are loaded around the location so that
                // spigot will allow the entity to be activated.
                boolean isNearbyChunksLoaded = ChunkUtils.isNearbyChunksLoaded(
                        npcLocation.getWorld(), chunkCoords.getX(), chunkCoords.getZ(), 3);

                if (isNearbyChunksLoaded) {
                    stop();
                    setAwaitingRespawn(AwaitRespawnReason.INVOKED);
                    spawnNpc(current);
                }
            }

            @Override
            protected void onPathComplete() {
                _subscriberAgents.update("onFinish", getNpc());
            }

            private void spawnNpc(Location location) {

                if (isAwaitingChunkReload()) {
                    // load chunk NPC is in to cause respawn.
                    Location npcLocation = getNpc().getLocation(NPC_LOCATION);
                    Coords2Di chunkCoords = ChunkUtils.getChunkCoords(npcLocation, CHUNK_COORDS);
                    npcLocation.getWorld().loadChunk(chunkCoords.getX(), chunkCoords.getZ());
                } else {
                    setAwaitingRespawn(AwaitRespawnReason.INVOKED);
                    // directly spawn NPC.
                    getNpc().spawn(location);
                }
            }
        }

        private static class BukkitListener implements Listener {

            String traitName = NpcTraitPack.getLookup(NAME);

            @EventHandler
            private void onNpcSpawn(final NpcSpawnEvent event) {

                INpcTraits traits = event.getNpc().getTraits();

                if (!traits.isEnabled(traitName))
                    return;

                final PlannedWaypoints trait = (PlannedWaypoints)traits.get(traitName);
                assert trait != null;

                final Location currentPosition = trait._timer.stop();

                if (currentPosition != null && trait.isAwaitingRespawn()) {

                    if (event.getReason() == NpcSpawnReason.CHUNK_LOAD) {
                        event.setCancelled(true);

                        Scheduler.runTaskLater(NpcTraitPack.getPlugin(), new Runnable() {
                            @Override
                            public void run() {
                                trait.setAwaitingRespawn(AwaitRespawnReason.INVOKED);
                                event.getNpc().spawn(currentPosition);
                            }
                        });

                        return;
                    }
                    else {
                        Entity npcEntity = event.getNpc().getEntity();
                        assert npcEntity != null;

                        npcEntity.teleport(currentPosition);
                        trait._current = trait._timer.getCurrentDestination();
                    }
                }

                trait.setAwaitingRespawn(AwaitRespawnReason.NONE);
            }

            @EventHandler
            private void onNpcDespawn(NpcDespawnEvent event) {

                INpcTraits traits = event.getNpc().getTraits();

                if (!traits.isEnabled(traitName))
                    return;

                PlannedWaypoints trait = (PlannedWaypoints)traits.get(traitName);
                assert trait != null;

                if (event.getReason() == NpcDespawnReason.CHUNK_UNLOAD && trait._current != null) {

                    trait.setAwaitingRespawn(AwaitRespawnReason.CHUNK_UNLOAD);

                    double speed = event.getNpc().getNavigator().getCurrentSettings().getSpeed();

                    trait._timer.start(trait._current, speed);
                }
            }
        }

        /*
         * NPC Way point goal
         */
        private class WaypointGoal implements INpcGoal {

            @Override
            public String getName() {
                return "PlannedWaypoint";
            }

            @Override
            public void reset(INpcState state) {
                // do nothing
            }

            @Override
            public boolean canRun(INpcState state) {
                return !_waypoints.isEmpty();
            }

            @Override
            public float getCost(INpcState state) {
                return 1.0f;
            }

            @Override
            public void pause(INpcState state) {
                // do nothing
            }

            @Override
            public void firstRun(INpcGoalAgent agent) {
                getNpc().getNavigator().cancel();
            }

            @Override
            public void run(INpcGoalAgent goalAgent) {

                if (!getNpc().getNavigator().isRunning()) {

                    if (_waypoints.isEmpty()) {
                        _subscriberAgents.update("onFinish", getNpc());

                        // check waypoints again in case more were added
                        // by onFinish subscriber
                        if (_waypoints.isEmpty())
                            goalAgent.finish();
                        else
                            next();
                    }
                    else {
                        next();
                    }
                }
            }

            private void next() {

                _current = _waypoints.removeFirst();

                getNpc().getNavigator().setTarget(_current);
                getNpc().lookLocation(_current);
            }
        }
    }
}
