import { collection, getDocs, limit, orderBy, query, where } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { entryFromData } from '@/domain/documents';
import type { LedgerEntry } from '@/domain/economy';
import { accumulate, emptySnapshot, type StatsSnapshot } from '@/domain/history';

export const HISTORY_PAGE_SIZE = 30;
const STATS_BATCH = 300;

/** Asientos anteriores a [beforeSeq] (todos si es `null`), del más reciente al más antiguo. */
export async function ledgerBefore(uid: string, beforeSeq: number | null, size = HISTORY_PAGE_SIZE): Promise<LedgerEntry[]> {
  const ledger = collection(db, `wallets/${uid}/ledger`);
  const q = beforeSeq === null ? query(ledger, orderBy('seq', 'desc'), limit(size)) : query(ledger, where('seq', '<', beforeSeq), orderBy('seq', 'desc'), limit(size));
  const snapshot = await getDocs(q);
  return snapshot.docs.map((d) => entryFromData(d.data()));
}

const statsKey = (uid: string) => `royal-chance-estadisticas-${uid}`;

/**
 * Estadísticas por juego de toda la cuenta. El resumen se guarda en el dispositivo y solo se leen
 * los asientos nuevos desde la última vez (los asientos son inmutables).
 */
export async function loadStatistics(uid: string): Promise<StatsSnapshot> {
  let snapshot = emptySnapshot();
  try {
    const saved = localStorage.getItem(statsKey(uid));
    if (saved) snapshot = JSON.parse(saved) as StatsSnapshot;
  } catch {
    // Sin almacenamiento local: se recalcula desde el principio.
  }
  const ledger = collection(db, `wallets/${uid}/ledger`);
  for (;;) {
    const page = await getDocs(query(ledger, where('seq', '>', snapshot.lastSeq), orderBy('seq', 'asc'), limit(STATS_BATCH)));
    if (page.empty) break;
    snapshot = accumulate(snapshot, page.docs.map((d) => entryFromData(d.data())));
    if (page.size < STATS_BATCH) break;
  }
  try {
    localStorage.setItem(statsKey(uid), JSON.stringify(snapshot));
  } catch {
    // Sin espacio o sin permiso: la próxima vez se recalcula.
  }
  return snapshot;
}
