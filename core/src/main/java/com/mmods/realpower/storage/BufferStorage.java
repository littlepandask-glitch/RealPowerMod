package com.mmods.realpower.storage;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Armazenamento de energia que a rede de cabos trata como reserva (banco de capacitores): carrega com o excedente da
 * rede e descarrega para cobrir a demanda que os geradores nao atendem. Os demais {@link IEnergyStorage} sao consumidores.
 */
public interface BufferStorage extends IEnergyStorage {
}
