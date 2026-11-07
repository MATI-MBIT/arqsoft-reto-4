# Imagen de cualquiera de los servicios. El jar se compila en el host con
# `make imagenes`, así la imagen se arma en segundos y sin red.
# Uso: docker build -f deploy/Dockerfile --build-arg SERVICE=recepcion .
FROM eclipse-temurin:21-jre
ARG SERVICE
COPY services/${SERVICE}/build/libs/app.jar /app/app.jar
ENV JAVA_OPTS="-Xms256m -Xmx768m"
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
