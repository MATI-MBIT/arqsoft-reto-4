// MASIVOS archivos masivos (100 000 registros, ~49 MB) lanzados a la vez, cada uno
// en su propio VU. El orquestador lo arranca en el minuto que fija la fila del plan.
//
// Uso: k6 run -e CORRIDA=<id> -e MASIVOS=5 masivos.js
import http from 'k6/http';
import { check } from 'k6';

const REMITENTE = __ENV.REMITENTE_URL || 'http://localhost:8090';
const CORRIDA = __ENV.CORRIDA || `manual-${Date.now()}`;
const MASIVOS = Number(__ENV.MASIVOS || 5);

export const options = {
  scenarios: {
    masivos: { executor: 'per-vu-iterations', vus: MASIVOS, iterations: 1, maxDuration: '15m' },
  },
  summaryTrendStats: ['med', 'p(95)', 'max'],
};

export default function () {
  const r = http.post(`${REMITENTE}/enviar?tipo=masivo&corrida=${CORRIDA}`, null,
    { tags: { tipo: 'masivo', name: 'enviar' }, timeout: '10m' });
  check(r, { 'acusado': (x) => x.status === 200 && x.json('status') === 202 });
}
