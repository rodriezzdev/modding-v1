package net.mcextremo.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class GlobalSoundInstance extends AbstractSoundInstance {
    public GlobalSoundInstance(SoundEvent sound, SoundSource category, float volume) {
        super(sound, category, SoundInstance.createUnseededRandom());
        this.volume = volume; // Aquí asignamos el multiplicador de volumen
        this.pitch = 1.0F;
        this.attenuation = SoundInstance.Attenuation.NONE; // Desactiva la pérdida por distancia
        this.relative = true; // Fuerza a que suene estático directamente en los auriculares
    }
}