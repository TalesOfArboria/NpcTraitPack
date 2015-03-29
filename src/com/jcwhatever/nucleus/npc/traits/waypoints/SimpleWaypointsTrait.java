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
import com.jcwhatever.nucleus.npc.traits.waypoints.provider.SimpleWaypointProvider;
import com.jcwhatever.nucleus.providers.npc.INpc;
import com.jcwhatever.nucleus.providers.npc.ai.INpcState;
import com.jcwhatever.nucleus.providers.npc.ai.goals.INpcGoal;
import com.jcwhatever.nucleus.providers.npc.ai.goals.INpcGoalAgent;
import com.jcwhatever.nucleus.providers.npc.traits.NpcTrait;
import com.jcwhatever.nucleus.providers.npc.traits.NpcTraitType;
import com.jcwhatever.nucleus.utils.PreCon;
import com.jcwhatever.nucleus.utils.observer.script.IScriptUpdateSubscriber;
import com.jcwhatever.nucleus.utils.observer.script.ScriptUpdateSubscriber;
import com.jcwhatever.nucleus.utils.observer.update.NamedUpdateAgents;

import org.bukkit.Location;

import java.util.Collection;

/**
 * Simple waypoints queue. Add waypoints to the trait and the NPC
 * will go to each one in the order it was added. Non-looping.
 *
 * <p>Trait is registered with the lookup name "NpcTraitPack:SimpleWaypoints"</p>
 */
public class SimpleWaypointsTrait extends NpcTraitType {

    public static final String NAME = "SimpleWaypoints";

    /**
     * Constructor.
     */
    public SimpleWaypointsTrait() {
        super(NpcTraitPack.getPlugin(), NAME);
    }

    @Override
    protected NpcTrait createTrait(INpc npc) {
        return new SimpleWaypoints(this);
    }

    public static class SimpleWaypoints extends NpcTrait {

        private static final Location CURRENT = new Location(null, 0, 0, 0);

        private final SimpleWaypointProvider _provider = new SimpleWaypointProvider();
        private final NamedUpdateAgents _subscriberAgents = new NamedUpdateAgents();

        private INpcGoal _waypointGoal;

        /**
         * Constructor.
         *
         * @param type The parent type that instantiated the trait.
         */
        SimpleWaypoints(NpcTraitType type) {
            super(type);
        }

        /**
         * Add a way point location.
         *
         * @param location  The location to add.
         *
         * @return  Self for chaining.
         */
        public SimpleWaypoints addWaypoint(Location location) {
            PreCon.notNull(location);

            _provider.getWaypoints().add(location);

            return this;
        }

        /**
         * Add a collection of way point locations.
         *
         * @param locations  The locations to add.
         *
         * @return  Self for chaining.
         */
        public SimpleWaypoints addWaypoints(Collection<Location> locations) {
            PreCon.notNull(locations);

            _provider.getWaypoints().addAll(locations);

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
        public SimpleWaypoints onFinish(IScriptUpdateSubscriber<INpc> subscriber) {
            PreCon.notNull(subscriber);

            _subscriberAgents.getAgent("onFinish").register(new ScriptUpdateSubscriber<>(subscriber));

            return this;
        }

        /**
         * Start pathing to the added waypoints.
         *
         * @return  Self for chaining.
         */
        public SimpleWaypoints start() {
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
        public SimpleWaypoints stop() {
            if (_waypointGoal == null)
                return this;

            getNpc().getGoals().remove(_waypointGoal);

            return this;
        }

        /**
         * Clear all way points.
         */
        public void clear() {
            _provider.reset();
        }

        @Override
        protected void onRemove() {
            stop();
            clear();
            _subscriberAgents.disposeAgents();
        }

        /**
         * NPC Way point goal
         */
        private class WaypointGoal implements INpcGoal {

            @Override
            public String getName() {
                return "SimpleWaypoint";
            }

            @Override
            public void reset(INpcState state) {
                _provider.reset();
            }

            @Override
            public boolean canRun(INpcState state) {
                return _provider.hasNext();
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

                    if (_provider.hasNext()) {
                        next();
                    } else {
                        _subscriberAgents.update("onFinish", getNpc());

                        // check waypoints again in case more were added
                        // by onFinish subscriber
                        if (_provider.hasNext())
                            next();
                        else
                            goalAgent.finish();
                    }
                }
            }

            private void next() {

                Location current = _provider.next(CURRENT);

                getNpc().getNavigator().setTarget(current);
                getNpc().lookLocation(current);
            }
        }
    }
}
