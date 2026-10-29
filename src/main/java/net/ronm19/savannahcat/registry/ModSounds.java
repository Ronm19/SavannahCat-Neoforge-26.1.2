package net.ronm19.savannahcat.registry;

import java.util.EnumMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.savannahcat.SavannahCatMod;

/** Namespaced replaceable events. Current recordings are explicitly vanilla references, not custom OGGs. */
public final class ModSounds {
    public enum Cue {
        AMBIENT("ambient","entity.cat.ambient","Savannah Cat meows"),
        HURT("hurt","entity.cat.hurt","Savannah Cat cries"),
        DEATH("death","entity.cat.death","Savannah Cat dies"),
        STEP("step","block.grass.step","Savannah Cat pads"),
        PURR("purr","entity.cat.purr","Savannah Cat purrs"),
        HISS("hiss","entity.cat.hiss","Savannah Cat hisses"),
        GROWL("growl","entity.cat.hiss","Savannah Cat growls"),
        EATING("eating","entity.cat.eat","Savannah Cat eats"),
        DRINKING("drinking","entity.generic.drink","Savannah Cat drinks"),
        SLEEPING("sleeping","entity.cat.purr","Savannah Cat sleeps softly"),
        SCRATCHING("scratching","block.wood.hit","Savannah Cat scratches"),
        TAMING("taming","entity.cat.purreow","Savannah Cat bonds"),
        REVIVAL("revival","entity.cat.purr","Savannah Cat recovers"),
        PLAYING("playing","entity.cat.beg_for_food","Savannah Cat plays");
        public final String suffix,vanilla,subtitle;
        Cue(String suffix,String vanilla,String subtitle){this.suffix=suffix;this.vanilla=vanilla;this.subtitle=subtitle;}
        public String path(){return "entity.savannah_cat."+suffix;}
        public String subtitleKey(){return "subtitles.savannahcat."+suffix;}
        public Identifier id(){return Identifier.fromNamespaceAndPath(SavannahCatMod.MOD_ID,path());}
    }
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,SavannahCatMod.MOD_ID);
    private static final EnumMap<Cue,DeferredHolder<SoundEvent,SoundEvent>> EVENTS=new EnumMap<>(Cue.class);
    static {for(var cue:Cue.values())EVENTS.put(cue,SOUNDS.register(cue.path(),()->SoundEvent.createVariableRangeEvent(cue.id())));}
    public static SoundEvent sound(Cue cue){return EVENTS.get(cue).get();}
    private ModSounds() {}
}
