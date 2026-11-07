-- Esquema del prototipo del experimento de Orquídea (reto 4).
-- operacion: el estado que usan los servicios. registro: la fuente del veredicto.

CREATE SCHEMA operacion;
CREATE SCHEMA registro;

-- Un archivo acusado. La llave (remitente|fecha|lote|consecutivo) es única: se
-- registra en la misma transacción que el archivo, antes del acuse (ADR-004).
CREATE TABLE operacion.archivo (
    llave          text PRIMARY KEY,
    remitente      text        NOT NULL,
    carril         text        NOT NULL,         -- carril-a | carril-b
    registros      int         NOT NULL,
    bytes          bigint      NOT NULL,
    hash           text        NOT NULL,         -- SHA-256 de los bytes custodiados
    estado         text        NOT NULL,         -- ACUSADO | VERIFICACION_PENDIENTE | RECHAZADO
    serial         text,
    estampa        text,
    causa          text,
    recibido_en    timestamptz NOT NULL,         -- t0: último byte
    resultado      text,                         -- TOTAL | PARCIAL | RECHAZO
    resultado_en   timestamptz,
    hash_procesado text,                         -- el hash que recalculó quien procesó
    resuelto_por   text
);

-- Lo que la recepción rechazó por validación o verificación: sin llave registrada.
CREATE TABLE operacion.archivo_rechazado (
    id         bigserial   PRIMARY KEY,
    llave      text        NOT NULL,
    causa      text        NOT NULL,
    bytes      bigint      NOT NULL,
    rechazado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- El resultado de cada declaración, una sola vez por (archivo, declaración) (ADR-004).
CREATE TABLE operacion.resultado_declaracion (
    llave     text NOT NULL,
    n         int  NOT NULL,
    resultado text NOT NULL,                     -- ACEPTADA | RECHAZADA
    causa     text,
    PRIMARY KEY (llave, n)
);

-- El efecto del resultado definitivo (la notificación al remitente y a las áreas).
-- Sin clave única a propósito: si la idempotencia falla, el duplicado queda a la vista.
CREATE TABLE operacion.efecto_resultado (
    id         bigserial   PRIMARY KEY,
    llave      text        NOT NULL,
    componente text        NOT NULL,
    escrito_en timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX efecto_resultado_llave ON operacion.efecto_resultado (llave);

-- Lo que espera la ventana nocturna (ADR-003, ADR-007). El mensaje durable está en
-- el tópico «pendientes»; esta fila dice en qué intento va y dónde quedó.
CREATE TABLE operacion.pendiente (
    llave       text PRIMARY KEY,
    motivo      text        NOT NULL,            -- TERCERO_CAIDO | VERIFICACION_PENDIENTE
    desde_n     int         NOT NULL,
    intentos    int         NOT NULL,
    notificado  boolean     NOT NULL DEFAULT false,
    estado      text        NOT NULL,            -- PENDIENTE | RESUELTO
    creado_en   timestamptz NOT NULL,
    resuelto_en timestamptz
);

-- El registro de evidencia de solo anexar, firmado por BCR (ADR-008).
CREATE TABLE operacion.evidencia (
    id            bigserial   PRIMARY KEY,
    llave         text        NOT NULL,
    hash          text        NOT NULL,
    serial        text,
    firmante      text,
    estampa       text,
    contenido     text        NOT NULL,          -- el texto exacto que firmó BCR
    firma_bcr     text        NOT NULL,
    registrado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE FUNCTION operacion.solo_anexar() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'el registro de evidencia es de solo anexar';
END $$;

CREATE TRIGGER evidencia_solo_anexar BEFORE UPDATE OR DELETE ON operacion.evidencia
    FOR EACH ROW EXECUTE FUNCTION operacion.solo_anexar();

-- Reinicio de prueba antes de cada ejecución. TRUNCATE no dispara el disparador de
-- fila: el registro de evidencia sigue siendo de solo anexar para la aplicación.
CREATE FUNCTION operacion.reiniciar() RETURNS void LANGUAGE sql AS $$
    TRUNCATE operacion.archivo, operacion.archivo_rechazado, operacion.resultado_declaracion,
             operacion.efecto_resultado, operacion.pendiente, operacion.evidencia;
$$;

-- Registro ----------------------------------------------------------------
CREATE TABLE registro.evento (
    id         bigserial   PRIMARY KEY,
    ts         timestamptz NOT NULL,
    componente text        NOT NULL,
    tipo       text        NOT NULL,
    archivo    text,
    datos      jsonb       NOT NULL DEFAULT '{}'
);
CREATE INDEX evento_ts      ON registro.evento (ts);
CREATE INDEX evento_tipo    ON registro.evento (tipo, ts);
CREATE INDEX evento_archivo ON registro.evento (archivo, tipo) WHERE archivo IS NOT NULL;

-- Una fila por ejecución. inicio es el fin del calentamiento; fin, el de la medición.
CREATE TABLE registro.corrida (
    id          text PRIMARY KEY,
    asr         text        NOT NULL,            -- D01 | L04 | S06
    escenario   text        NOT NULL,            -- D-1 ... S-3
    variables   jsonb       NOT NULL DEFAULT '{}',
    arranque    timestamptz NOT NULL,
    inicio      timestamptz,
    fin         timestamptz,
    cierre      timestamptz,                     -- fin de la gracia: lo último que entra al cruce
    valida      boolean     NOT NULL DEFAULT true,
    motivo      text
);
