import { m as motion } from 'motion/react';
import { Chip } from '@/ui/Chip';
import { chips } from '@/lib/format';
import { play } from '@/audio/sound';

export const TABLE_CHIPS = [10, 50, 100, 500, 1000, 5000];

/** Fichas para elegir con qué valor se apuesta: la elegida se levanta y brilla. */
export function ChipRack({ selected, onSelect, size = 40, values = TABLE_CHIPS, disabled }: { selected: number; onSelect: (v: number) => void; size?: number; values?: number[]; disabled?: boolean }) {
  return (
    <div className="flex items-center gap-1.5" role="radiogroup" aria-label="Valor de la ficha">
      {values.map((value) => {
        const active = value === selected;
        return (
          <motion.button
            key={value}
            type="button"
            role="radio"
            aria-checked={active}
            aria-label={`Ficha de ${chips(value)}`}
            disabled={disabled}
            onClick={() => {
              play('chip');
              onSelect(value);
            }}
            animate={{ y: active ? -6 : 0 }}
            whileTap={{ scale: 0.92 }}
            transition={{ type: 'spring', stiffness: 500, damping: 28 }}
            className={`relative rounded-full disabled:opacity-40 ${active ? 'drop-shadow-[0_0_10px_rgb(243_223_162/0.7)]' : ''}`}
          >
            <Chip value={value} size={size} />
            {active && <span className="absolute inset-0 rounded-full ring-2 ring-gold-light" aria-hidden />}
          </motion.button>
        );
      })}
    </div>
  );
}
