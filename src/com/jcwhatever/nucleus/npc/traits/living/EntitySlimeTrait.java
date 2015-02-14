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

package com.jcwhatever.nucleus.npc.traits.living;

import com.jcwhatever.nucleus.providers.npc.INpc;
import com.jcwhatever.nucleus.providers.npc.traits.NpcTraitType;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Slime;

import javax.annotation.Nullable;

/**
 * An implementation of {@link LivingEntityTrait} specific to
 * {@link Slime} entities.
 *
 * <p>Trait is registered with the lookup name "NpcTraitPack:LivingEntity"</p>
 */
public class EntitySlimeTrait extends LivingEntityTrait {

    private int _size;

    /**
     * Constructor.
     *
     * @param npc  The NPC the trait is for.
     * @param type The parent type that instantiated the trait.
     */
    EntitySlimeTrait(INpc npc, NpcTraitType type) {
        super(npc, type, EntityType.SLIME);
    }

    /**
     * Get the size the slime is spawned with.
     */
    public int getSize() {
        return _size;
    }

    /**
     * Set the size the slime is spawned with. If the slime is
     * already spawned, the current entity is also updated.
     *
     * @param size  The size.
     *
     * @return  Self for chaining.
     */
    public EntitySlimeTrait setSize(int size) {
        _size = size;

        Slime slime = getSlime();
        if (slime != null)
            slime.setSize(size);

        return this;
    }

    @Override
    public void onSpawn() {

        super.onSpawn();

        if (isDisposed())
            return;

        Slime slime = getSlime();
        if (slime != null)
            slime.setSize(_size);
    }

    @Nullable
    private Slime getSlime() {
        return (Slime)getLivingEntity();
    }
}