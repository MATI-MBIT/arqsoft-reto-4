# El inyector de fallas: anota el t0 de cada falla en el registro de eventos con
# el reloj de Docker y le pide a Toxiproxy el corte o la latencia.
FROM alpine:3.20
RUN apk add --no-cache bash curl postgresql16-client docker-cli
COPY inyectar.sh /usr/local/bin/inyectar
RUN chmod +x /usr/local/bin/inyectar
ENTRYPOINT ["sleep", "infinity"]
