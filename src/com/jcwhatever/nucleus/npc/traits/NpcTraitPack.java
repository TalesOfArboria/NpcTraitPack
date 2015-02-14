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

package com.jcwhatever.nucleus.npc.traits;

import com.jcwhatever.nucleus.Nucleus;
import com.jcwhatever.nucleus.NucleusPlugin;
import com.jcwhatever.nucleus.npc.traits.living.LivingEntityTraitType;
import com.jcwhatever.nucleus.providers.npc.INpcProvider;
import com.jcwhatever.nucleus.providers.npc.events.NpcCreateEvent;
import com.jcwhatever.nucleus.providers.npc.events.NpcEntityTypeChangeEvent;
import com.jcwhatever.nucleus.providers.npc.traits.INpcTraits;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * NPC Trait Pack Bukkit plugin for NucleusFramework.
 *
 * <p>Adds collection of NPC traits.</p>
 */
public class NpcTraitPack extends NucleusPlugin implements Listener {

    private static NpcTraitPack _instance;

    public static NpcTraitPack getPlugin() {
        return _instance;
    }

    public static String getLookup(String name) {
        return getPlugin().getName() + ':' + name;
    }

    @Override
    public String getChatPrefix() {
        return "[NPCTraitPack] ";
    }

    @Override
    public String getConsolePrefix() {
        return "[NPCTraitPack] ";
    }

    @Override
    protected void onInit() {
        _instance = this;
    }

    @Override
    protected void onEnablePlugin() {
        _instance = this;

        INpcProvider provider = Nucleus.getProviderManager().getNpcProvider();

        if (provider == null) {
            getMessenger().warning("Nucleus NPC provider not detected. Disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
        }
        else {

            provider
                    // manual add traits
                    .registerTrait(new AggressiveTrait())
                    .registerTrait(new ArcherTrait())
                    .registerTrait(new FreezeHeightTrait())
                    .registerTrait(new LookingTrait())
                    .registerTrait(new NoDropsTrait())
                    .registerTrait(new ProtectPassengerTrait())
                    .registerTrait(new RiderTrait())
                    .registerTrait(new SimpleWaypointsTrait())
                    .registerTrait(new UnbreakingArmorTrait())
                    .registerTrait(new UnbreakingWeaponTrait())

                    // auto added traits
                    .registerTrait(new LivingEntityTraitType());
        }

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    protected void onDisablePlugin() {
        _instance = null;
    }

    @EventHandler
    private void onNpcCreate(NpcCreateEvent event) {

        INpcTraits traits = event.getNpc().getTraits();

        EntityType type = traits.getType();

        if (type.isAlive()) {
            traits.add(getLookup("LivingEntity"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onNpcEntityTypeChange(NpcEntityTypeChangeEvent event) {

        if (event.getOldType().isAlive() == event.getNewType().isAlive())
            return;

        INpcTraits traits = event.getNpc().getTraits();

        if (event.getNewType().isAlive()) {
            traits.add(getLookup("LivingEntity"));
        }
        else {
            traits.remove(getLookup("LivingEntity"));
        }
    }
}