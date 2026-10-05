import { useEffect, useRef, useState } from 'react';
import { grouped } from '@/lib/format';
import { Chip } from './Chip';

/** Cifra que cuenta hasta su nuevo valor (sube o baja) en lugar de saltar. */
export function useCountUp(target: number, durationMs = 650, initial = target): number {
  const [shown, setShown] = useState(initial);
  const from = useRef(initial);
  const frame = useRef(0);
  useEffect(() => {
    const start = performance.now();
    const origin = from.current;
    if (origin === target || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      from.current = target;
      setShown(target);
      return;
    }
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs);
      const eased = 1 - Math.pow(1 - t, 3);
      const value = Math.round(origin + (target - origin) * eased);
      from.current = value;
      setShown(value);
      if (t < 1) frame.current = requestAnimationFrame(tick);
    };
    frame.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame.current);
  }, [target, durationMs]);
  return shown;
}

/** Saldo con su ficha dorada. */
export function ChipBalance({ balance, size = 'md' }: { balance: number; size?: 'sm' | 'md' }) {
  const shown = useCountUp(balance);
  const chipSize = size === 'sm' ? 20 : 24;
  return (
    <span className="inline-flex items-center gap-2" aria-label={`Saldo: ${grouped(balance)} fichas`} role="status">
      <Chip value={1000} size={chipSize} label="" />
      <span className={`tabular font-bold text-gold-light ${size === 'sm' ? 'text-[15px]' : 'text-lg'}`} aria-hidden>
        {grouped(shown)}
      </span>
    </span>
  );
}
