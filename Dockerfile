FROM openjdk:latest

COPY src/ /usr/src/app
WORKDIR /usr/src/app

RUN javac -cp ".:netty-all-4.1.0.CR3.jar" Server.java

ENTRYPOINT java -cp ".:netty-all-4.1.0.CR3.jar" Server

CMD ["java", "Server"]

EXPOSE 8080

