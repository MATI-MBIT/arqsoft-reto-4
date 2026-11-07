// La carga de fondo del Ambiente A: archivos pequeños (1 a 50 registros) con arribo
// estocástico, a TASA archivos por minuto (400 por omisión), durante DURACION.
//
// constant-arrival-rate reparte las llegadas a intervalos fijos. Para que el arribo
// sea estocástico sin perder la tasa media, k6 dispara a 4 veces la tasa y cada
// llegada se envía con probabilidad 1/4: el número de envíos por intervalo es
// binomial, cercano a Poisson.
//
// Cada iteración le pide al remitente simulado que arme, firme y envíe un archivo.
// La latencia que decide ASR-L04 no se mide aquí, sino en el registro de eventos
// (del último byte al resultado definitivo); k6 da la referencia del acuse.
//
// Uso: k6 run -e CORRIDA=<id> -e DURACION=10m -e TASA=400 pequenos.js
import http from 'k6/http';
import { check } from 'k6';

const REMITENTE = __ENV.REMITENTE_URL || 'http://localhost:8090';
const CORRIDA = __ENV.CORRIDA || `manual-${Date.now()}`;
const TASA = Number(__ENV.TASA || 400);
const FACTOR = 4;

export const options = {
  scenarios: {
    pequenos: {
      executor: 'constant-arrival-rate',
      rate: TASA * FACTOR,
      timeUnit: '1m',
      duration: __ENV.DURACION || '10m',
      preAllocatedVUs: 20,
      maxVUs: 200,
    },
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  if (Math.random() >= 1 / FACTOR) {
    return;
  }
  const r = http.post(`${REMITENTE}/enviar?tipo=pequeno&corrida=${CORRIDA}`, null,
    { tags: { tipo: 'pequeno', name: 'enviar' }, timeout: '60s' });
  check(r, { 'acusado': (x) => x.status === 200 && x.json('status') === 202 });
}
